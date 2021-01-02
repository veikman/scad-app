;;; A demo of scad-app’s concurrency mechanisms with channeled feedback.

;;; This module is intended to be run as a script using lein-exec,
;;; https://github.com/kumarshantanu/lein-exec, e.g.
;;;
;;;     lein exec -p src/demo/core.clj

(ns demo.core
  (:require [clojure.core.async :as async]
            [scad-app.core :as core]))

(def get-time #(java.time.LocalTime/now))
(def t0 (get-time))
(def get-delta #(. java.time.Duration between t0 (get-time)))

(defn stamp-report [report] (assoc report ::time-of-req (get-delta)))

(defn print-timestamped
  [{::keys [time-of-req] ::core/keys [update-type] :as asset}]
  (let [time-of-feedback (get-delta)]
    (println (.toString time-of-req)
             (.toString time-of-feedback)
             (name update-type)
             (:name asset))))

(defn- asset-sleep
  "Take a pair of durations snuck into an asset and sleep for that long."
  [enqueue-report options {::keys [scad-sleep render-sleep] :as asset}]
  (let [loose-ends (async/chan)
        n-ends (atom 0)
        log #(async/go (async/>! loose-ends (enqueue-report %))
                       (swap! n-ends inc))]
    (log (stamp-report (merge asset {::core/update-type ::core/started-scad})))
    (Thread/sleep (or scad-sleep 0))
    (log (stamp-report (merge asset {::core/update-type ::core/started-render})))
    (Thread/sleep (or render-sleep 0))
    (log (stamp-report (merge asset {::core/update-type ::core/finished})))
    (async/go
      (dotimes [_ @n-ends] (async/<! loose-ends)))))

(defn -main
  [& _]
  (print-timestamped (stamp-report {:name "demo", ::core/update-type ::core/started-scad}))
  (core/build-all
    [{:name "a", ::scad-sleep 5000, ::render-sleep 5000}
     {:name "of"}
     {:name "gob", ::render-sleep 1000}
     {:name "stem", ::scad-sleep 1000}
     {:name "freak", ::scad-sleep 1000}
     {:name "detail", ::scad-sleep 3000}
     {:name "juniper", ::scad-sleep 1000, ::render-sleep 1000}
     {:name "absentee", ::scad-sleep 1000, ::render-sleep 1000}
     {:name "influence", ::scad-sleep 1000, ::render-sleep 3000}
     {:name "connection", ::scad-sleep 1000, ::render-sleep 1000}
     {:name "psychedelic", ::scad-sleep 1000, ::render-sleep 1000}
     {:name "belligerence", ::scad-sleep 5000, ::render-sleep 1000}
     {:name "admonishments", ::scad-sleep 1000, ::render-sleep 1000}
     {:name "stupendousness", ::scad-sleep 1000, ::render-sleep 5000}
     {:name "archeozoologist"}]
    {:report-fn print-timestamped, :build-fn asset-sleep})
  (Thread/sleep 10)  ; Try not to clash with final print from the above.
  (print-timestamped (stamp-report {:name "demo", ::core/update-type ::core/finished})))

(apply -main (rest *command-line-args*))
