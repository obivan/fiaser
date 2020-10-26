(ns fiaser.core
  (:require [aero.core :as aero]
            [fiaser.convert :as convert])
  (:gen-class))

(defn read-config
  [filename]
  (aero/read-config filename))

(def usage
  "Usage: java -jar fiaser-x.y.z-SNAPSHOT-standalone.jar [path/to/config.edn]")

(defn exit
  [status msg]
  (println msg)
  (System/exit status))

(def default-config-filename "config.edn")

(defn -main
  "Application entry point"
  [& args]
  (when (= "-h" (first args))
    (exit 0 usage))
  (let [config (read-config (or (first args)
                                default-config-filename))]
    (convert/proceed-data config)
    (shutdown-agents)))
