(defproject cyrus/config "0.0.0"
  :description "Config loading library"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :plugins [[lein-cloverage "1.0.9"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.6.15"]]
  :profiles {:dev {:dependencies [[squeeze "0.3.1"]]}})
