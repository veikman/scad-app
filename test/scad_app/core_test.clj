(ns scad-app.core-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer :all]
            [scad-clj.model :refer [square cube]]
            [scad-app.core :refer [filter-basenames build-all]]))

(def sqp #(square 1 2))
(def cbp #(cube 1 2 4))

(deftest filter-test
  (testing "filter-basenames, single input"
    (is (= (filter-basenames [{:basename "a1", :producer sqp}] #"a")
           [{:basename "a1", :producer sqp}]))
    (is (= (filter-basenames [{:basename "a1", :producer sqp}] #"b")
           [])))
  (testing "filter-basenames, mixed input"
    (is (= (filter-basenames [{:basename "a1", :producer sqp}
                              {:basename "b1", :producer sqp}]
                             #"a")
           [{:basename "a1", :producer sqp}]))
    (is (= (filter-basenames [{:basename "a1", :producer sqp}
                              {:basename "b1", :producer sqp}]
                             #"b")
           [{:basename "b1", :producer sqp}]))))

(deftest build-all-test
  (testing "build-all, the high-level API"
    (let [reports (atom #{})
          report-fn (fn [asset] (swap! reports conj asset))
          build-fn (fn [log options asset]
                     (log (merge asset {:update-type :finished})))
          a1 {:basename "a1", :producer sqp}
          a2 {:basename "a2", :producer cbp}]
      (is (nil? (build-all [a1 a2] {:build-fn build-fn, :report-fn report-fn})))
      (is (= @reports
             #{(merge a1 {:update-type :finished})
               (merge a2 {:update-type :finished})})))))
