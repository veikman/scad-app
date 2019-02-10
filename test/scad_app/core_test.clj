(ns scad-app.core-test
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as spec]
            [clojure.test :refer :all]
            [scad-clj.model :refer [square cube]]
            [scad-app.core :as mut]))

(def sqm (square 1 2))
(def cbm (cube 1 2 4))

(deftest schemata
  (testing "the asset schema"
    (is (spec/valid? ::mut/asset {:name "a"}))
    (is (spec/valid? ::mut/asset {:name "b", :model-fn #(sqm)}))
    (is (spec/valid? ::mut/asset {:name "c", :model-main sqm}))
    (is (spec/valid? ::mut/asset {:name "d", :model-vector [sqm]}))))

(deftest filter-test
  (testing "filter-by-name, single input"
    (is (= (mut/filter-by-name  #"a" [{:name "a1", :model-main sqm}])
           [{:name "a1", :model-main sqm}]))
    (is (= (mut/filter-by-name  #"b" [{:name "a1", :model-main sqm}])
           [])))
  (testing "filter-by-name, mixed input"
    (is (= (mut/filter-by-name #"a"
                               [{:name "a1", :model-main sqm}
                                {:name "b1", :model-main sqm}])

           [{:name "a1", :model-main sqm}]))
    (is (= (mut/filter-by-name #"b"
                               [{:name "a1", :model-main sqm}
                                {:name "b1", :model-main sqm}])
           [{:name "b1", :model-main sqm}]))))

(deftest build-all-test
  (testing "build-all, the high-level API"
    (let [reports (atom #{})
          report-fn (fn [asset] (swap! reports conj asset))
          build-fn (fn [log options asset]
                     (log (merge asset {:update-type :finished})))
          a1 {:name "a1", :model-main sqm}
          a2 {:name "a2", :model-main cbm}]
      (is (nil? (mut/build-all [a1 a2] {:build-fn build-fn, :report-fn report-fn})))
      (is (= @reports
             #{(merge a1 {:update-type :finished})
               (merge a2 {:update-type :finished})})))))
