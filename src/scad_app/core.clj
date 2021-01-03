;;; Refer to README.md.

(ns scad-app.core
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.spec.alpha :as spec]
            [clojure.string :refer [join]]
            [scad-clj.model :refer [define-module mirror fa! fn! fs!]]
            [scad-clj.scad :refer [write-scad]]
            [scad-app.schema :as schema]
            [scad-app.misc :as misc]))


;;;;;;;;;;;;;;
;; INTERNAL ;;
;;;;;;;;;;;;;;

(defn- format-report
  "Take an asset update. Return a string describing it."
  [{:keys [name filepath-scad]
    ::schema/keys [update-type filepath-render command-render]}]
  {:pre [(spec/valid? ::schema/update-type update-type)
         (spec/valid? (spec/nilable ::schema/command-render) command-render)]}
  (case update-type
    ::schema/started-scad (format "%s: Creating %s" name filepath-scad)
    ::schema/started-render (format "%s: Creating %s" name filepath-render)
    ::schema/failed-render
      (do
        (assert command-render)
        (format "%s: Failed to render %s with external command “%s”"
                name filepath-render (join " " command-render)))
    ::schema/finished (format "%s: Complete" name)))

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
  {:pre [(spec/valid? ::schema/asset asset)]}
  (cond
    model-vector asset  ; Asset is already complete.
    model-main (merge asset {:model-vector [model-main]})  ; Wrap seq.
    model-fn (ensure-model-vector (trigger-model-fn asset))
    :else (throw (ex-info (format "Asset “%s” has no model." name) asset))))

(defn- vectored-model [asset] (:model-vector (ensure-model-vector asset)))

(defn- ensure-model-main
  "Take an asset. Return an asset with an unwrapped main scad-clj spec."
  [{:keys [name model-fn model-main model-vector] :as asset}]
  {:pre [(spec/valid? ::schema/asset asset)]}
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
  {:pre [(spec/valid? ::schema/asset asset)]}
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
  {:pre [(spec/valid? ::schema/asset asset)]}
  (let [f (cond mirrored mirrored-fn
                chiral original-fn
                :else achiral-fn)]
    (assoc asset :name (f name))))

(defn- to-scad
  "Write one SCAD file from a scad-clj specification in an asset.
  This operation does not log failure because it does not call external
  utilities that are considered risky. Failure will instead crash the
  current application."
  [log {:keys [filepath-scad minimum-face-angle face-count
               minimum-face-size]
        :as asset}]
  {:pre [(some? filepath-scad)
         (spec/valid? ::schema/asset asset)]}
  (log (merge asset {::schema/update-type ::schema/started-scad}))
  (io/make-parents filepath-scad)
  (let [{:keys [model-vector]} (ensure-model-vector asset)
        preface [(when minimum-face-angle (fa! minimum-face-angle))
                 (when face-count (fn! face-count))
                 (when minimum-face-size (fs! minimum-face-size))]]
    (spit filepath-scad (apply write-scad (concat preface model-vector)))))

(defn- from-scad
  "Render SCAD to something else.
  Call a named rendering program with a prepared CLI command."
  [log asset filepath-out cmd]
  (let [status-update (fn [update-type]
                        (log (merge asset {::schema/update-type update-type,
                                           ::schema/command-render cmd,
                                           ::schema/filepath-render filepath-out})))]
    (status-update ::schema/started-render)
    (io/make-parents filepath-out)
    (let [success (zero? (:exit (apply sh cmd)))]
      (when-not success (status-update ::schema/failed-render))
      success)))

(defn- default-stl-writer
  "Render SCAD to STL."
  [log {:keys [filepath-scad filepath-stl] :as asset}]
  (from-scad log asset filepath-stl
             (misc/compose-openscad-command
               (merge asset {:outputfile filepath-stl})
               filepath-scad)))

(defn- default-image-writers
  "Define a vector of nullary functions based on a shared asset.
  Each of these renders a separate 2D image of the asset."
  [log {:keys [filepath-fn filepath-scad images]
        :or {filepath-fn misc/compose-filepath, images []}
        :as asset}]
  (mapv (fn [{:keys [name suffix] :or {suffix "png"} :as image}]
          (let [filepath (filepath-fn (:name asset) suffix {:tail [name]})
                options (merge asset image {:outputfile filepath})]
            #(from-scad log asset filepath
                        (misc/compose-openscad-command options filepath-scad))))
        images))

(defn- produce-module
  "Return scad-clj specs for an OpenSCAD module."
  [{:keys [flip-chiral] :or {flip-chiral true}}
   {:keys [name] :as asset}]
  {:pre [(spec/valid? ::schema/asset asset)]}
  (define-module name
    (single-model (if flip-chiral (mirror-chiral-asset asset) asset))))

(defn- asset-io
  "Write one asset to file(s). Return a go-block channel.
  Progress reports are handled asynchronously, but each reporter is also
  put in a local channel and this channel is ultimately drained here for
  synchronization. The returned go block ensures that reports are resolved."
  [enqueue-report
   {:keys [filepath-fn scad-writer render image-writers stl-writer]
    :or {filepath-fn misc/compose-filepath, scad-writer to-scad}}
   {:keys [name] :as asset}]
  {:pre [(spec/valid? ::schema/asset asset)]}
  (let [loose-ends (async/chan)
        n-ends (atom 0)
        log (fn [msg]
              (swap! n-ends inc)
              (async/go (async/>! loose-ends (enqueue-report msg))))
        inputs (merge {:filepath-scad (filepath-fn name "scad")
                       :filepath-stl (filepath-fn name "stl")}
                      asset)]
    (scad-writer log inputs)
    (when (or (not render)
              (and render
                   (reduce
                     ;; Terminate if any render fails.
                     (fn [_ writer] (if (writer) true (reduced false)))
                     nil
                     ;; Iterate over one rendering function per output file.
                     (remove nil?
                       (conj
                         (or image-writers
                             (default-image-writers log inputs))
                         (or stl-writer
                             #(default-stl-writer log inputs)))))))
      (log (merge inputs {::schema/update-type ::schema/finished})))
    (async/go
      (dotimes [_ @n-ends] (async/<! loose-ends)))))


;;;;;;;;;;;;;;;;;;;;;;;;;
;; INTERFACE FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filter-by-name
  "Filter passed assets by a regular expression applied to their names."
  [regex assets]
  {:pre [(spec/valid? (spec/coll-of ::schema/asset) assets)]}
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
   {:pre [(spec/valid? ::schema/asset asset)
          (spec/valid? (spec/coll-of ::schema/asset) module-assets)]}
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
   (mapcat (partial refine-fn options) assets)))

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
   {:pre [(spec/valid? (spec/coll-of ::schema/asset) assets)]}
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
