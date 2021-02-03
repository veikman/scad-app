;;; Parameter schema.

(ns scad-app.schema
  (:require [clojure.spec.alpha :as spec]))

(spec/def ::update-type
  #{::started-scad ::failed-scad ::started-render ::failed-render ::finished})

(spec/def ::render boolean?)
(spec/def ::rendering-program string?)
(spec/def ::command-render (spec/coll-of string?))

(spec/def ::name string?)
(spec/def ::suffix string?)
(spec/def ::model-fn fn?)
;; :model-main should key any return value of a scad-clj model function.
;; ‘seq?’ is a fragile assumption about the library’s internals.
;; Cf. https://github.com/farrellm/scad-clj/issues/42
(spec/def ::model-main seq?)
(spec/def ::model-vector (spec/and vector? (spec/coll-of ::model-main)))
;; The OpenSCAD manual on $fa and $fs: “The minimum allowed value is 0.01.”
(spec/def ::minimum-face-angle (spec/and number? #(>= % 0.01)))
(spec/def ::minimum-face-size ::minimum-face-angle)
(spec/def ::face-count (spec/and integer? #(>= % 0)))
(spec/def ::chiral boolean?)
(spec/def ::mirrored boolean?)
(spec/def ::translation (spec/coll-of number? :count 3))
(spec/def ::rotation (spec/coll-of number? :count 3))
(spec/def ::distance (spec/and number? (complement neg?)))
(spec/def ::eye (spec/coll-of number? :count 3))
(spec/def ::center (spec/coll-of number? :count 3))
(spec/def ::camera
  (spec/or :gimbal (spec/keys :req-un [::translation ::rotation ::distance])
           :vector (spec/keys :req-un [::eye ::center])))
(spec/def :image/size (spec/coll-of number? :count 2))
(spec/def ::colorscheme string?)

(spec/def ::image (spec/keys :req-un [::name]
                             :opt-un [::suffix ::camera :image/size ::colorscheme]))
(spec/def ::images (spec/coll-of ::image))
(spec/def ::asset (spec/keys :req-un [::name]
                             :opt-un [::model-fn ::model-main ::model-vector
                                      ::chiral ::mirrored ::images ::face-count
                                      ::minimum-face-angle ::minimum-face-size]))

;; TODO: Expand ::asset to require one of model-fn, model-main, model-vector.
