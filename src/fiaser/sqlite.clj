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

(defn skip-insert!
  [tx table-name row]
  (try
    (sql/insert! tx table-name row)
    (catch Exception e
      (let [msg (.getMessage e)]
        (binding [*out* *err*]
          (println "skip row" row "because of error:" msg))))))
