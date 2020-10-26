(defproject fiaser "0.2.0-SNAPSHOT"
  :description "Utilities for working with FIAS"
  :url "http://github.com/obivan/fiaser"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "1.0.0"]
                 [org.xerial/sqlite-jdbc "3.32.3.2"]
                 [seancorfield/next.jdbc "1.1.610"]
                 [org.postgresql/postgresql "42.2.18"]
                 [aero "1.1.6"]
                 [camel-snake-kebab "0.4.2"]]
  :plugins [[lein-cljfmt "0.7.0"]]
  :main ^:skip-aot fiaser.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[clj-kondo "RELEASE"]]
                   :aliases {"lint" ["run" "-m" "clj-kondo.main"
                                     "--lint" "src"]
                             "fmtc" ["cljfmt" "check"]
                             "fmt" ["cljfmt" "fix"]}}})
