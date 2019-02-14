;;; A demo of scad-app’s concurrency mechanisms with channeled feedback.

;;; This module is intended to be run as a script using lein-exec,
;;; https://github.com/kumarshantanu/lein-exec, e.g.
;;;
;;;     lein exec -p src/demo/core.clj

(ns demo.core
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async :as async]
            [scad-app.core :as core]))

(def get-time #(java.time.LocalTime/now))

(def t0 (get-time))

(defn print-timestamped
  [{:keys [name update-type]}]
  (let [t (get-time)
        Δ (. java.time.Duration between t0 t)]
    (println (.toString Δ) update-type name)))

(defn- asset-sleep
  "Take a pair of durations snuck into an asset and sleep for that long."
  [enqueue-report options {:keys [name scad-sleep stl-sleep] :as asset}]
  (let [loose-ends (async/chan)
        n-ends (atom 0)
        log #(async/go (async/>! loose-ends (enqueue-report %))
                       (swap! n-ends inc))]
    (log (merge asset {:update-type :started-scad}))
    (Thread/sleep (or scad-sleep 0))
    (log (merge asset {:update-type :started-stl}))
    (Thread/sleep (or stl-sleep 0))
    (log (merge asset {:update-type :finished}))
    (async/go
      (dotimes [_ @n-ends] (async/<! loose-ends)))))

(defn -main
  [& _]
  (print-timestamped {:name "demo", :update-type "started"})
  (core/build-all
    [{:name "a", :scad-sleep 5000, :stl-sleep 5000}
     {:name "of"}
     {:name "gob", :stl-sleep 1000}
     {:name "stem", :scad-sleep 1000}
     {:name "freak", :scad-sleep 1000}
     {:name "detail", :scad-sleep 3000}
     {:name "juniper", :scad-sleep 1000, :stl-sleep 1000}
     {:name "absentee", :scad-sleep 1000, :stl-sleep 1000}
     {:name "influence", :scad-sleep 1000, :stl-sleep 3000}
     {:name "connection", :scad-sleep 1000, :stl-sleep 1000}
     {:name "psychedelic", :scad-sleep 1000, :stl-sleep 1000}
     {:name "belligerence", :scad-sleep 5000, :stl-sleep 1000}
     {:name "admonishments", :scad-sleep 1000, :stl-sleep 1000}
     {:name "stupendousness", :scad-sleep 1000, :stl-sleep 5000}
     {:name "archeozoologist"}]
    {:report-fn print-timestamped, :build-fn asset-sleep})
  (print-timestamped {:name "demo", :update-type "completed"}))

(apply -main (rest *command-line-args*))
