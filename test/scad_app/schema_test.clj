(ns scad-app.schema-test
  (:require [clojure.spec.alpha :as spec]
            [clojure.test :refer [deftest testing is]]
            [scad-clj.model :refer [square]]
            [scad-app.schema :as schema]))

(def sqm (square 1 2))

(deftest schemata
  (testing "the asset schema"
    (is (spec/valid? ::schema/asset {:name "a"}))
    (is (spec/valid? ::schema/asset {:name "b", :model-fn #(sqm)}))
    (is (spec/valid? ::schema/asset {:name "c", :model-main sqm}))
    (is (spec/valid? ::schema/asset {:name "d", :model-vector [sqm]}))))

