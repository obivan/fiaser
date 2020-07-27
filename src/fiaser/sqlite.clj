(ns fiaser.sqlite
  (:require [clojure.string :as string]
            [clojure.core.async :as a]
            [camel-snake-kebab.core :as csk]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [fiaser.xml :as xml]))

;todo: primary & foreign keys, indexes
;todo: PRAGMA foreign_keys = ON;
;todo: custom indexes

(def type-map {:integer "integer"
               :byte    "integer"
               :string  "text"
               :date    "text"
               :boolean "boolean"
               :empty   "text"})

(defn- field-to-sql
  [field]
  (str (:name field)
       " "
       ((:base field) type-map)
       (when (= (:use field) :required) " not null")))

(defn- gen-create-table
  [xsd-schema]
  (str "create table " (csk/->snake_case (:collection xsd-schema))
       " ("
       (string/join ", " (map field-to-sql (:fields xsd-schema)))
       ")"))

(defn create-table!
  [schema ds]
  (with-open [connection (jdbc/get-connection ds)]
    (jdbc/execute! connection [(gen-create-table schema)])))

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

(defn stream-to-table!
  [xml-file schema ds]
  (let [ch (a/chan 1000)
        coll-name (:collection schema)
        table-name (keyword (csk/->snake_case coll-name))
        next-row #(a/<!! ch)]
    (xml/stream-attrs! xml-file ch)
    (jdbc/with-transaction [tx ds]
      (loop [row (next-row)]
        (when-not (nil? row)
          (skip-insert! tx table-name row)
          (recur (next-row)))))))
