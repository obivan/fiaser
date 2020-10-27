(ns fiaser.sqlite
  (:require [clojure.string :as string]
            [camel-snake-kebab.core :as csk]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]))

;todo: primary & foreign keys, indexes

(def type-map {:integer "integer"
               :byte "integer"
               :string "text"
               :date "text"
               :boolean "boolean"
               :empty "text"})

(defn field-ddl
  [field]
  (let [field-name (:name field)
        field-type ((:base field) type-map)
        not-null? (= (:use field) :required)]
    (string/join \space [field-name field-type (when not-null? "not null")])))

(defn create-table-ddl
  [table-name table-fields]
  (let [fields-ddl (map field-ddl table-fields)]
    (format "create table %s (%s)" table-name (string/join ", " fields-ddl))))

(defn- create-table!
  [datasource schema]
  (let [table-name (csk/->snake_case (:collection schema))
        table-fields (:fields schema)]
    (jdbc/execute-one! datasource [(create-table-ddl table-name table-fields)])))

(defn prepare-database!
  [datasource schemas]
  (run! (partial create-table! datasource) schemas))

(defn make-datasource
  "Creates SQLite datasource according to the specified sqlite file path"
  [sqlite-file]
  (let [spec {:dbtype "sqlite" :dbname sqlite-file}
        opts {:builder-fn rs/as-unqualified-lower-maps}]
    (jdbc/with-options spec opts)))

(defn make-row-stub
  [schema]
  (let [fields (:fields schema)]
    (into {} (for [{:keys [name]} fields] [(keyword name) nil]))))

(defn prepare-multi-insert
  [schema rows]
  (let [stub (make-row-stub schema)
        cols (keys stub)
        rows (map (partial merge stub) rows)]
    [cols (map vals rows)]))

(defn skip-insert!
  [conn schema rows-batch]
  (let [coll-name (:collection schema)
        table-name (keyword (csk/->snake_case coll-name))
        [cols rows] (prepare-multi-insert schema rows-batch)]
    (try
      (sql/insert-multi! conn table-name cols rows)
      (catch Exception e
        (let [msg (.getMessage e)]
          (binding [*out* *err*]
            (locking *out*
              (println "skip row" rows-batch "because of error:" msg))))))))
