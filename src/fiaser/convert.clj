(ns fiaser.convert
  (:import (java.io File))
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :as set]
            [fiaser.xsd :as xsd]
            [fiaser.xml :as xml]
            [fiaser.sqlite :as sqlite]))

(defn- dir-ls-by-ext
  "Returns the list of files with a certain extension relative to the path"
  [path ext]
  {:pre [(.isDirectory (io/file path))]}
  (let [cleaned-ext (string/replace-first ext #"^\." "")
        lower-ext-pattern (str "." (string/lower-case cleaned-ext))
        by-ext #(string/ends-with? (string/lower-case %) lower-ext-pattern)]
    (->> path io/file .list (filter by-ext))))

(defn- dir-ls-sub-dir
  "Returns the list of subdirectories in the directory including path"
  [path]
  {:pre [(.isDirectory (io/file path))]}
  (->> path io/file file-seq (filter #(.isDirectory ^File %)) (map str)))

(defn- absolute-file-path
  "Concatenates dir and file using a file system separator"
  [dir file]
  {:pre  [(.isDirectory (io/file dir))]
   :post [(.exists (io/file %))]}
  (str dir File/separator file))

(defn- fias-file?
  "Checks the file-name for matching the FIAS file name mask"
  [file-name]
  (let [upcase-name (string/upper-case file-name)]
    (and (-> upcase-name
             (string/starts-with? "AS_"))
         (not (-> upcase-name
                  (string/starts-with? "AS_DEL_"))))))

(defn ls-fias-abs
  "Absolute paths of fias-file? files in the directory"
  [dir ext]
  {:pre [(.isDirectory (io/file dir))]}
  (->> (dir-ls-by-ext dir ext)
       (filter fias-file?)
       (map #(absolute-file-path dir %))))

(defn- xml-dir-map
  "Absolute path and top tag map of XML files"
  [dir]
  (let [files (mapcat #(ls-fias-abs % "xml") (dir-ls-sub-dir dir))
        tags (map xml/top-tag files)]
    (zipmap files tags)))

(defn- xsd-dir-map
  "Absolute path and parsed schema map of XSD files"
  [dir]
  (let [files (ls-fias-abs dir "xsd")
        schemas (map xsd/parse files)
        coll-names (map :collection schemas)]
    (zipmap coll-names schemas)))

(defn- xml-processing-scope
  "Maps the absolute path of an XML data file to its corresponding schema"
  [xml-dir xsd-dir]
  (let [xml-map (xml-dir-map xml-dir)
        xsd-map (xsd-dir-map xsd-dir)
        intersect (set/intersection (set (vals xml-map))
                                    (->> xsd-map vals (map :collection) set))
        scope (filter (comp intersect val) xml-map)]
    (reduce (fn
              [result [file-path, collection-name]]
              (assoc result file-path (get xsd-map collection-name)))
            {}
            scope)))

;todo: progress reporting
(defn xml->sqlite
  "Convert FIAS XML directory content to SQLite database"
  [options]
  (println "perform conversion with options:" options)
  (let [xml-dir (:xml-dir options)
        xsd-dir (:xsd-dir options)
        scope (xml-processing-scope xml-dir xsd-dir)
        ds (sqlite/make-datasource (:sqlite-file options))]
    (println "creating tables ...")
    (run! (fn [schema]
            (print "creating table" (:collection schema) "...")
            (flush)
            (sqlite/create-table! schema ds)
            (println " done"))
          (set (vals scope)))
    (println "done")
    (println "loading data ...")
    (run! (fn [item]
            (let [file (key item)
                  schema (val item)]
              (print "processing file" file "...")
              (flush)
              (sqlite/stream-to-table! file schema ds)
              (println " done")))
          scope)
    (println "done")))
