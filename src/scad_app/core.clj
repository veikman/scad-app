(ns scad-app.core
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as spec]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [scad-clj.scad :refer [write-scad]]))


;;;;;;;;;;;;;;
;; INTERNAL ;;
;;;;;;;;;;;;;;

(defn- re-predicate-fn
  "Define a basename predicate function based on a regular expression."
  [regex]
  (fn [basename] (some? (re-find regex basename))))

(defn- default-namer
  [basename suffix]
  "Produce a relative file path for e.g. SCAD or STL."
  (io/file "output" suffix (str basename "." suffix)))

(spec/def ::update-type
  #{:skipped :started-scad :started-stl :failed-stl :finished})

(defn format-report
  "Take an asset update. Return a string describing it."
  [{:keys [basename filepath-scad filepath-stl update-type]}]
  {:pre [(spec/valid? ::update-type update-type)]}
  (case update-type
    :skipped (format "Skipped %s" basename)
    :started-scad (format "Creating %s" filepath-scad)
    :started-stl (format "Creating %s" filepath-stl)
    :failed-stl (format "Could not render %s" filepath-scad)
    :finished (format "Finished %s" basename)))

(defn print-report
  "Print a progress report to *out* (by default: STDOUT)."
  [asset]
  (println (format-report asset)))

(defn to-scad
  "Write one SCAD file from a scad-clj specification."
  [log {:keys [producer filepath-scad] :as asset}]
  {:pre [(some? producer)
         (some? filepath-scad)]}
  (log (merge asset {:update-type :started-scad}))
  (io/make-parents filepath-scad)
  (spit filepath-scad (write-scad (producer))))

(defn define-stl-writer
  "Define a function that render SCAD to STL.
  The function is to call a named rendering program CLI-compatible with
  OpenSCAD (e.g. ‘openscad-nightly’)."
  [program]
  (fn [log {:keys [filepath-scad filepath-stl] :as asset}]
    (log (merge asset {:update-type :started-stl}))
    (io/make-parents filepath-stl)
    (zero? (:exit (sh program "-o" filepath-stl filepath-scad)))))

(spec/def ::render boolean?)
(spec/def ::renderer string?)
(spec/def ::asset (spec/keys :req-un [::basename ::producer]
                             :opt-un [::render ::renderer]))

(defn build-asset
  "Treat one asset. Return a go-block channel.
  Progress reports are handled asynchronously, but each reporter is also
  put in a local channel and this channel is ultimately drained here for
  synchronization, to ensure that all reports have been resolved before the
  go block exits."
  [enqueue-report
   {:keys [basename-pred whitelist-re namer scad-writer
           render rendering-program stl-writer]
    :or {namer default-namer,
         scad-writer to-scad,
         stl-writer (when render
                      (define-stl-writer (or rendering-program "openscad")))}}
   {:keys [basename]
    :as asset}]
  {:pre [(spec/valid? ::asset asset)]}
  (let [basename-pred (re-predicate-fn (or whitelist-re #""))
        loose-ends (async/chan)
        n-ends (atom 0)
        log #(async/go (async/>! loose-ends (enqueue-report %))
                       (swap! n-ends inc))]
    (if (basename-pred basename)
      (let [inputs (merge {:filepath-scad (namer basename "scad")
                           :filepath-stl (namer basename "stl")}
                          asset)]
        (scad-writer log inputs)
        (if stl-writer
          ;; Call STL writer.
          (if (stl-writer log inputs)
            (log (merge asset {:update-type :finished}))
            (log (merge asset {:update-type :failed-stl})))
          ;; No STL write requested. SCAD is enough.
          (log (merge asset {:update-type :finished}))))
      (log (update asset :update-type :skipped)))
    (async/go
      (dotimes [_ @n-ends] (async/<! loose-ends)))))


;;;;;;;;;;;;;;;;;;;;;;;;;
;; INTERFACE FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-all
  "Build specified assets in parallel. Block until all are complete.
  This function sets up a single thread to handle UI output serially, with a
  closure for enqueing that output. The closure is passed down along with each
  model asset."
  ([assets]
   (build-all assets {}))
  ([assets
    {:keys [report-fn build-fn]
     :or {report-fn print-report, build-fn build-asset}
     :as options}]
   (let [report-chan (async/chan)
         enqueue-report
           (fn [asset]
             (let [response-chan (async/chan)]
               (async/go
                 (async/>! report-chan [asset response-chan])
                 ;; Wait for reporting thread to close the channel it passed.
                 (async/<! response-chan))))]
     (async/thread
       (loop []  ; Loop until report channel is closed.
         (when-let [report (async/<!! report-chan)]
           (let [[asset response-chan] report]
             (report-fn asset)            ; Display message in UI.
             (async/close! response-chan))  ; Coordinate with sender.
           (recur))))
     ;; Start all go blocks, then wait for them to finish.
     (let [cvec (mapv (partial build-fn enqueue-report options) assets)]
       (doseq [c cvec] (async/<!! c)))
     ;; Close channel explicitly to prevent lingering circular references.
     (async/close! report-chan))))
