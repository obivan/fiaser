(ns fiaser.core
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [fiaser.convert :as convert])
  (:gen-class))

(def cli-spec
  [["-d" "--xml-dir DIR" "FIAS xml directory (default current)"
    :id :xml-dir
    :default "."
    :validate [#(.isDirectory (io/file %)) "must be a directory"]]
   ["-s" "--xml-schema-dir DIR" "FIAS xsd files directory (default current)"
    :id :xsd-dir
    :default "."
    :validate [#(.isDirectory (io/file %)) "must be a directory"]]
   ["-o" "--sqlite-file FILE" "SQLite output database file (default fias.sqlite)"
    :id :sqlite-file
    :default "fias.sqlite"
    :validate [#(not (.exists (io/file %))) "SQLite file already exists"]]
   ["-h" "--help" :id :help]])

(defn usage
  [options-summary]
  (->> ["Usage: java -jar fiaser-x.y.z-SNAPSHOT-standalone.jar <command> [options]"
        ""
        "Options:" options-summary
        ""
        "Actions:"
        "  convert    Convert XML directory to SQLite database"
        ""]
       (string/join \newline)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments summary errors]} (cli/parse-opts args cli-spec)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}
      errors
      {:exit-message errors}
      ;custom validation on arguments
      (and (= 1 (count arguments))
           (#{"convert"} (first arguments)))
      {:action (first arguments) :options options}
      :else
      {:exit-message (usage summary)})))

(defn exit
  [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "Application entry point"
  [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "convert" (convert/xml->sqlite options)))))

(comment
  (-main "convert"
         "-d" "/Users/vko/Desktop/fias/fias_xml"
         "-s" "/Users/vko/Desktop/fias/fias_schemas"
         "-o" "/Users/vko/Desktop/fiaser/fias.sqlite"))
