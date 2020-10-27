(ns fiaser.convert
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :as set]
            [fiaser.xsd :as xsd]
            [fiaser.xml :as xml]
            [fiaser.sqlite :as sqlite]
            [fiaser.postgres :as pg]
            [next.jdbc :as jdbc])
  (:import (java.nio.file FileSystems)))

(defn fias-files
  [directory-path extension-filter]
  {:pre [#{"xml" "xsd"} (string/lower-case extension-filter)]}
  (let [matcher (.getPathMatcher
                  (FileSystems/getDefault)
                  (str "regex:(?i)as_(?!del).*." extension-filter))]
    (->> directory-path
         clojure.java.io/file
         file-seq
         (filter #(.isFile %))
         (filter #(.matches matcher (.getFileName (.toPath %))))
         (mapv #(.getAbsolutePath %)))))

(defn xml-dir-map
  "Absolute path and top tag map of XML files"
  [dir]
  (let [files (fias-files dir "xml")
        tags (map xml/top-tag files)]
    (zipmap files tags)))

(defn xsd-dir-map
  "Absolute path and parsed schema map of XSD files"
  [dir]
  (let [files (fias-files dir "xsd")
        schemas (map xsd/parse files)
        coll-names (map :collection schemas)]
    (zipmap coll-names schemas)))

(defn processing-scope
  "Maps the absolute path of an XML data file to its corresponding schema"
  [xml-dir xsd-dir]
  (let [xml-map (xml-dir-map xml-dir)
        xsd-map (xsd-dir-map xsd-dir)
        intersect (set/intersection (set (vals xml-map))
                                    (->> xsd-map vals (map :collection) set))
        scope (filter (comp intersect val) xml-map)]
    (reduce (fn [result [file-path, collection-name]]
              (assoc result file-path (get xsd-map collection-name)))
            {}
            scope)))

(defmulti make-datasource :dbtype)
(defmethod make-datasource :sqlite [_ config]
  (sqlite/make-datasource (:file config)))
(defmethod make-datasource :postgres [_ config]
  (apply pg/make-datasource
         ((juxt :host :port :user :dbname
                :schema :password) config)))

(defn make-datasources
  [targets]
  (into {} (for [[k v] targets :when (:enabled? v)]
             [k (make-datasource {:dbtype k} v)])))

(defn open-connections
  [datasources]
  (into {} (for [[k v] datasources] [k (jdbc/get-connection v)])))

(defn close-connections
  [connections]
  (run! (fn [[_ v]] (.close v)) connections))

(defmulti make-insert-handler :dbtype)
(defmethod make-insert-handler :sqlite [_ [conn schema]]
  (partial sqlite/skip-insert! conn schema))
(defmethod make-insert-handler :postgres [_ [conn schema]]
  (jdbc/execute-one! conn ["set synchronous_commit = off"])
  (partial pg/skip-insert! conn schema))

(defn make-insert-handlers
  [cons schema]
  (into [] (for [[k v] cons] (make-insert-handler {:dbtype k} [v schema]))))

(defn proceed-file
  [datasources file-name schema]
  (locking *out* (println "processing file" file-name))
  (with-open [input-stream (io/input-stream file-name)]
    (let [connections (open-connections datasources)
          handlers (make-insert-handlers connections schema)
          batch-size 250]
      (doseq [rows-batch (partition-all batch-size (xml/stream input-stream))
              handler handlers]
        (apply handler [rows-batch]))
      (close-connections connections))))

(defn proceed-data
  [{:keys [targets]
    {:keys [xml-dir xsd-dir]} :sources}]
  (let [scope (processing-scope xml-dir xsd-dir)
        schemas (set (vals scope))
        datasources (make-datasources targets)]
    (when (not-empty datasources)
      (println "creating tables ...")
      (when-let [datasource (:sqlite datasources)]
        (sqlite/prepare-database! datasource schemas))
      (when-let [datasource (:postgres datasources)]
        (let [pg-schema-name (-> targets :postgres :schema)
              pg-tbs-name (or (-> targets :postgres :tablespace)
                              "pg_default")]
          (pg/prepare-database! datasource pg-schema-name pg-tbs-name schemas)))
      (println "done")
      (println "loading data ...")
      (doall (pmap (fn [[file schema]]
                     (proceed-file datasources file schema)) scope))
      (println "done"))))
