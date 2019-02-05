(ns scad-app.core-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer :all]
            [scad-clj.model :refer [square cube]]
            [scad-app.core :refer [build-all]]))

(deftest build-all-test
  (testing "build-all, the high-level API"
    (let [reports (atom #{})
          report-fn (fn [asset] (swap! reports conj asset))
          build-fn (fn [log options asset]
                     (log (merge asset {:update-type :finished})))
          a1 {:basename "a1", :producer #(square 1 2)}
          a2 {:basename "a2", :producer #(cube 1 2 4)}]
      (is (nil? (build-all [a1 a2] {:build-fn build-fn, :report-fn report-fn})))
      (is (= @reports
             #{(merge a1 {:update-type :finished})
               (merge a2 {:update-type :finished})})))))
