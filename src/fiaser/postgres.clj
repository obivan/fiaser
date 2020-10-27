(ns fiaser.postgres
  (:require [next.jdbc.result-set :as rs]
            [next.jdbc :as jdbc]
            [fiaser.sqlite :as sqlite]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as string]))

(defn- field-ddl
  [field]
  (sqlite/field-ddl field))

(defn create-table-ddl
  [table-name table-fields tbs-name]
  (let [fields-ddl (map field-ddl table-fields)]
    (format "create table %s (%s) tablespace %s"
            table-name (string/join ", " fields-ddl) tbs-name)))

(defn table-comment-ddl
  [table-name comment]
  (format "comment on table %s is '%s'" table-name comment))

(defn column-comment-ddl
  [table-name column-name comment]
  (format "comment on column %s.%s is '%s'" table-name column-name comment))

(defn- create-table!
  [datasource tbs-name schema]
  (let [table-name (csk/->snake_case (:collection schema))
        table-comment (:annotation schema)
        table-fields (:fields schema)]
    (jdbc/execute-one!
      datasource [(create-table-ddl table-name table-fields tbs-name)])
    (jdbc/execute-one!
      datasource [(table-comment-ddl table-name table-comment)])
    (run!
      (fn [{:keys [name annotation]}]
        (jdbc/execute-one!
          datasource [(column-comment-ddl table-name name annotation)]))
      table-fields)))

(defn create-schema-ddl
  [schema-name]
  (format "create schema if not exists %s" schema-name))

(defn prepare-database!
  [datasource db-schema-name tbs-name schemas]
  (jdbc/execute! datasource [(create-schema-ddl db-schema-name)])
  (run! (partial create-table! datasource tbs-name) schemas))

(defn make-datasource
  "Creates PostgreSQL datasource"
  [host port user dbname schema password]
  (let [spec {:dbtype "postgres"
              :stringtype "unspecified"
              :host host
              :port port
              :user user
              :dbname dbname
              :currentSchema schema
              :password password}
        opts {:builder-fn rs/as-unqualified-lower-maps}]
    (jdbc/with-options spec opts)))

(defn skip-insert!
  [conn schema rows-batch]
  (sqlite/skip-insert! conn schema rows-batch))
