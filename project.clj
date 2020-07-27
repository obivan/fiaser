(defproject fiaser "0.1.0-SNAPSHOT"
  :description "Utilities for working with FIAS"
  :url "http://github.com/obivan/fiaser"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "1.0.0"]
                 [org.clojure/core.async "1.2.603"]
                 [org.xerial/sqlite-jdbc "3.32.3"]
                 [seancorfield/next.jdbc "1.1.547"]
                 [camel-snake-kebab "0.4.1"]]
  :plugins [[lein-cljfmt "0.6.8"]]
  :main ^:skip-aot fiaser.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev     {:dependencies [[clj-kondo "RELEASE"]]
                       :aliases      {"lint" ["run" "-m" "clj-kondo.main"
                                              "--lint" "src"]
                                      "fmtc" ["cljfmt" "check"]
                                      "fmt"  ["cljfmt" "fix"]}}})
