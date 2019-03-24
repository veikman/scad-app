(ns scad-app.core-test
  (:require [clojure.spec.alpha :as spec]
            [clojure.test :refer :all]
            [tempfile.core :refer [tempfile with-tempfile]]
            [scad-clj.model :refer [square cube mirror]]
            [scad-app.core :as mut]))

(def sqm (square 1 2))
(def cbm (cube 1 2 4))
(def msq (mirror [-1 0 0] sqm))

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
               (merge a2 {:update-type :finished})}))))
  (testing "trivial OpenSCAD artefact"
    (let [contents
           (with-tempfile [t (tempfile)]
             (mut/build-all
               [{:name "b1", :filepath-scad (.getPath t), :model-main sqm}]
               {:report-fn (fn [_])})
             (slurp (.getPath t)))]
      (is (= contents "square ([1, 2], center=true);\n"))))
  (testing "OpenSCAD artefact at non-standard arc resolution"
    (let [contents
           (with-tempfile [t (tempfile)]
             (mut/build-all
               [{:name "b2", :filepath-scad (.getPath t), :model-main sqm,
                 :minimum-face-size 1}]
               {:report-fn (fn [_])})
             (slurp (.getPath t)))]
      (is (= contents "$fs = 1;\nsquare ([1, 2], center=true);\n")))))

(deftest refine-asset-test
  (testing "the refine-asset function on an achiral asset"
    (is (= (mut/refine-asset {} {:name "n", :model-main sqm})
           [{:name "n", :model-vector [sqm]}])))
  (testing "the refine-asset function on a chiral asset, declining mirroring"
    (is (= (mut/refine-asset {:flip-chiral false}
             {:name "m", :model-main sqm, :chiral true})
           [{:name "m", :chiral true,
             :model-vector [sqm]}])))
  (testing "the refine-asset function on chiral asset, mirroring it"
    (is (= (mut/refine-asset {} {:name "m", :model-main sqm, :chiral true})
           [{:name "m", :chiral true,
             :model-vector [sqm]}
            {:name "m_mirrored", :chiral true, :mirrored true,
             :model-vector [msq]}]))))
