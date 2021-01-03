;;; Miscellaneous utilities.

(ns scad-app.misc
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [clojure.string :refer [join]]
            [scad-app.schema :as schema]))

(defn- intish [n] (if (integer? n) n (float n)))

(defn compose-filepath
  "Produce a relative file path for e.g. SCAD, PNG or STL."
  ([head suffix]
   (compose-filepath head suffix {}))
  ([head suffix {:keys [tail separator] :or {tail [], separator "_"}}]
   {:pre [(string? head)
          (string? suffix)
          (spec/valid? (spec/coll-of string?) tail)
          (string? separator)]}
   (io/file "output" suffix (str (join separator (concat [head] tail))
                                 "." suffix))))

(defn compose-camera-argument
  "Compose a CLI argument to OpenSCAD for camera position."
  [{:keys [translation rotation distance eye center] :as options}]
  (join ","
    (map intish
      (case (first (spec/conform ::schema/camera options))
        :gimbal (concat translation rotation [distance])
        :vector (concat eye center)))))

(defn compose-openscad-command
  "Compose a complete, shell-ready command for running OpenSCAD."
  [{:keys [rendering-program outputfile camera size colorscheme]
    :or {rendering-program "openscad"}}
   inputfile]
  (concat
    [rendering-program]
    (when outputfile ["-o" (.getPath outputfile)])
    (when camera ["--camera" (compose-camera-argument camera)])
    (when size ["--imgsize" (join "," (map intish size))])
    (when colorscheme ["--colorscheme" colorscheme])
    [(.getPath inputfile)]))
