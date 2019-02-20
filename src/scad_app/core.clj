;;; Refer to README.md.

(ns scad-app.core
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as spec]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [scad-clj.model :refer [define-module mirror]]
            [scad-clj.scad :refer [write-scad]]))


;;;;;;;;;;;;
;; SCHEMA ;;
;;;;;;;;;;;;

(spec/def ::update-type
  #{:started-scad :started-stl :failed-stl :finished})

(spec/def ::render boolean?)
(spec/def ::rendering-program string?)

(spec/def ::name string?)
(spec/def ::model-fn fn?)
; :model-main should key any return value of a scad-clj model function.
; ‘seq?’ is a fragile assumption about the library’s internals.
(spec/def ::model-main seq?)
(spec/def ::model-vector (spec/and vector? (spec/coll-of ::model-main)))
(spec/def ::chiral boolean?)
(spec/def ::mirrored boolean?)
(spec/def ::asset (spec/keys :req-un [::name]
                             :opt-un [::model-fn ::model-main ::model-vector
                                      ::chiral ::mirrored]))
; TODO: Expand ::asset to require one of model-fn, model-main, model-vector,
; and drop the corresponding ex-info below.


;;;;;;;;;;;;;;
;; INTERNAL ;;
;;;;;;;;;;;;;;

(defn- default-filepath-fn
  [base suffix]
  "Produce a relative file path for e.g. SCAD or STL."
  (io/file "output" suffix (str base "." suffix)))

(defn- format-report
  "Take an asset update. Return a string describing it."
  [{:keys [name filepath-scad filepath-stl update-type]}]
  {:pre [(spec/valid? ::update-type update-type)]}
  (case update-type
    :started-scad (format "%s: Creating %s" name filepath-scad)
    :started-stl (format "%s: Creating %s" name filepath-stl)
    :failed-stl (format "%s: Failed to render %s" name filepath-scad)
    :finished (format "%s: Complete" name)))

(defn- print-report
  "Print a progress report to *out* (by default: STDOUT)."
  [asset]
  (println (format-report asset)))

(defn- dissoc-model
  "Remove all canonically keyed model information from passed asset."
  [asset]
  (dissoc asset :model-fn :model-main :model-vector))

(defn- get-model-type-key [model]
  (if (vector? model) :model-vector :model-main))

(defn- trigger-model-fn
  "Trigger the model function of an asset and store the output appropriately."
  [{:keys [model-fn] :as asset}]
  (let [model (model-fn)]
    (update asset (get-model-type-key model) model)))

(defn- ensure-model-vector
  "Take an asset. Return an asset with a vector of scad-clj specs."
  [{:keys [name model-fn model-main model-vector] :as asset}]
  {:pre [(spec/valid? ::asset asset)]}
  (cond
    model-vector asset  ; Asset is already complete.
    model-main (merge asset {:model-vector [model-main]})  ; Wrap seq.
    model-fn (ensure-model-vector (trigger-model-fn asset))
    :else (throw (ex-info (format "Asset “%s” has no model." name) asset))))

(defn- vectored-model [asset] (:model-vector (ensure-model-vector asset)))

(defn- ensure-model-main
  "Take an asset. Return an asset with an unwrapped main scad-clj spec."
  [{:keys [name model-fn model-main model-vector] :as asset}]
  {:pre [(spec/valid? ::asset asset)]}
  (cond
    model-main asset  ; Asset is already complete.
    model-vector (do (assert (= (count model-vector) 1))
                     (update asset :model-main (first model-vector)))
    model-fn (ensure-model-main (trigger-model-fn asset))
    :else (throw (ex-info (format "Asset “%s” has no model." name) asset))))

(defn- single-model [asset] (:model-main (ensure-model-main asset)))

(defn- mirror-chiral-asset
  "Update passed asset. If it’s chiral, strip all model info except
  :model-main, mirror that model on the x axis, and tag the updated asset
  as mirrored so the operation will not be repeated on a second pass."
  [{:keys [chiral mirrored] :as asset}]
  {:pre [(spec/valid? ::asset asset)]}
  (if (and chiral (not mirrored))
    (merge
      (dissoc-model asset)
      {:mirrored true, :model-main (mirror [-1 0 0] (single-model asset))})
    asset))

(defn- mirror-rename
  "Update asset name based on chirality and mirroring.
  Take string-altering functions and an asset. Return an updated asset.
  If the input asset is chiral, it must have information on mirroring, such as
  is set by the mirror-chiral-asset function.
  By default, if no functions are provided, an achiral asset will not have its
  name changed, nor will a chiral asset that has not been mirrored, but a
  mirrored asset will have “_mirrored” appended to its name."
  [{:keys [achiral-fn original-fn mirrored-fn]
    :or {achiral-fn identity, original-fn identity,
         mirrored-fn #(str % "_mirrored")}}
   {:keys [name chiral mirrored] :as asset}]
  {:pre [(spec/valid? ::asset asset)]}
  (let [f (cond mirrored mirrored-fn
                chiral original-fn
                :else achiral-fn)]
    (assoc asset :name (f name))))

(defn- to-scad
  "Write one SCAD file from a scad-clj specification in an asset."
  [log {:keys [filepath-scad] :as asset}]
  {:pre [(some? filepath-scad)
         (spec/valid? ::asset asset)]}
  (log (merge asset {:update-type :started-scad}))
  (io/make-parents filepath-scad)
  (let [{:keys [model-vector]} (ensure-model-vector asset)]
    (spit filepath-scad (apply write-scad model-vector))))

(defn- define-stl-writer
  "Define a function that renders SCAD to STL.
  The function is to call a named rendering program CLI-compatible with
  OpenSCAD (e.g. ‘openscad-nightly’)."
  [program]
  (fn [log {:keys [filepath-scad filepath-stl] :as asset}]
    (let [cmd [program "-o" (.getPath filepath-stl) (.getPath filepath-scad)]]
      (log (merge asset {:update-type :started-stl, :command-stl cmd}))
      (io/make-parents filepath-stl)
      (zero? (:exit (apply sh cmd))))))

(defn- produce-module
  "Return scad-clj specs for an OpenSCAD module."
  [{:keys [flip-chiral] :or {flip-chiral true}}
   {:keys [name chiral] :or {chiral false} :as asset}]
  {:pre [(spec/valid? ::asset asset)]}
  (define-module name
    (single-model (if flip-chiral (mirror-chiral-asset asset) asset))))

(defn- asset-io
  "Write one asset to file(s). Return a go-block channel.
  Progress reports are handled asynchronously, but each reporter is also
  put in a local channel and this channel is ultimately drained here for
  synchronization. The returned go block ensures that reports are resolved."
  [enqueue-report
   {:keys [filepath-fn scad-writer render rendering-program stl-writer]
    :or {filepath-fn default-filepath-fn,
         scad-writer to-scad,
         stl-writer (when render
                      (define-stl-writer (or rendering-program "openscad")))}}
   {:keys [name] :as asset}]
  {:pre [(spec/valid? ::asset asset)]}
  (let [loose-ends (async/chan)
        n-ends (atom 0)
        log (fn [msg]
              (swap! n-ends inc)
              (async/go (async/>! loose-ends (enqueue-report msg))))
        inputs (merge {:filepath-scad (filepath-fn name "scad")
                       :filepath-stl (filepath-fn name "stl")}
                      asset)]
    (scad-writer log inputs)
    (if stl-writer
      ;; Call STL writer.
      (if (stl-writer log inputs)
        (log (merge inputs {:update-type :finished}))
        (log (merge inputs {:update-type :failed-stl})))
      ;; No STL write requested. SCAD is enough.
      (log (merge inputs {:update-type :finished})))
    (async/go
      (dotimes [_ @n-ends] (async/<! loose-ends)))))


;;;;;;;;;;;;;;;;;;;;;;;;;
;; INTERFACE FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filter-by-name
  "Filter passed assets by a regular expression applied to their names."
  [regex assets]
  {:pre [(spec/valid? (spec/coll-of ::asset) assets)]}
  (filter #(some? (re-find regex (:name %))) assets))

(defn refine-asset
  "Take an asset for a model, with keyword options and an optional iterable of
  subordinate assets for OpenSCAD modules needed by the main asset.
  Return an iterable of one or two assets modified for chirality and bundled
  with the OpenSCAD modules. Both of these types of refinement are united here
  because they are interdependent. This in turn is because modules (e.g.
  screw holes) can themselves be chiral, in such a way that they need to be
  doubly mirrored to come out useful in a singly mirrored model."
  ([options asset]
   (refine-asset options asset []))
  ([{:keys [flip-chiral] :or {flip-chiral true} :as options}
    {:keys [chiral] :or {chiral false} :as asset}
    module-assets]
   {:pre [(spec/valid? ::asset asset)
          (spec/valid? (spec/coll-of ::asset) module-assets)]}
   (reduce
     (fn [coll mirrored-twin]
       (let [oriented (if mirrored-twin (mirror-chiral-asset asset) asset)
             model-vector (vectored-model oriented)
             modules (mapv (partial produce-module {:flip-chiral mirrored-twin})
                           module-assets)]
         (conj coll
           (mirror-rename options
             (merge (dissoc-model oriented)
                    {:model-vector (vec (concat modules model-vector))})))))
     []
     (if (and chiral flip-chiral) [false true] [false]))))

(defn refine-all
  "Map refine-asset onto an iterable, possibly lengthening it."
  ([assets]
   (refine-all assets {}))
  ([assets {:keys [refine-fn] :or {refine-fn refine-asset} :as options}]
   (apply concat (map (partial refine-fn options) assets))))

(defn build-all
  "Build specified assets in parallel. Block until all are complete.
  This function sets up a single thread to handle UI output serially, with a
  closure for enqueing that output. The closure is passed down along with each
  model asset."
  ([assets]
   (build-all assets {}))
  ([assets
    {:keys [report-fn build-fn]
     :or {report-fn print-report, build-fn asset-io}
     :as options}]
   {:pre [(spec/valid? (spec/coll-of ::asset) assets)]}
   (let [report-chan (async/chan)
         build-chan (async/chan)
         enqueue-report
           (fn [asset]
             (let [response-chan (async/chan)]
               (async/go
                 (async/>! report-chan [asset response-chan])
                 ;; Wait for reporting thread to close the channel it passed.
                 (async/<! response-chan))))
         build
           (fn [asset]
             (async/go (async/>! build-chan
                         (build-fn enqueue-report options asset))))]
     (async/thread
       (loop []  ; Loop until report channel is closed.
         (when-let [report (async/<!! report-chan)]
           (let [[asset response-chan] report]
             (report-fn asset)              ; Display message in UI.
             (async/close! response-chan))  ; Coordinate with sender.
           (recur))))
     ;; Start all async threads building assets, then wait for them to finish.
     (doall (map build assets))
     ;; Waiting takes the form of synchronously getting a build-fn go block out
     ;; of build-chan and then waiting for that block as a channel.
     (dotimes [_ (count assets)] (async/<!! (async/<!! build-chan)))
     ;; By this point, both build-chan and report-chan should be drained,
     ;; but the async/thread in this function should still be synchronously
     ;; waiting for new reports. Close the channel to let the thread exit.
     (async/close! report-chan))))
