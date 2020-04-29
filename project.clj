(defproject scad-app "0.3.0-SNAPSHOT"
  :description "Programmatic CAD rendering interface"
  :url "https://github.com/veikman/scad-app"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v20.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.490"]
                 [scad-clj "0.5.3"]]
  :profiles {:dev {:dependencies [[tempfile "0.2.0"]]}})
