(ns fiaser.xsd
  {:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [xs]}}}}
  (:require [clojure.data.zip.xml :as zx]
            [clojure.data.xml.node :as node]
            [clojure.string :as string]
            [clojure.zip :as zip]
            [clojure.data.xml :as xml]
            [clojure.data.xml.name :as xn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [fiaser.specs :as specs]
            [clojure.set :as set]))

(xn/alias-uri :xs "http://www.w3.org/2001/XMLSchema")

(defn- parse-attribute
  "Parse the field data type and its limitations"
  [xml-node]
  {:name         (zx/xml1-> xml-node (zx/attr :name))
   :annotation   (zx/xml1-> xml-node
                            ::xs/annotation
                            ::xs/documentation
                            zx/text
                            string/trim)
   :base         (-> (or (zx/xml1-> xml-node
                                    ::xs/simpleType
                                    ::xs/restriction
                                    (zx/attr :base))
                         (zx/xml1-> xml-node (zx/attr :type))
                         ;bug - empty type (OBJECTID) in AS_PARAM_2_251_02_04_01_01.xsd
                         "empty"))
   :use          (zx/xml1-> xml-node (zx/attr :use) keyword)
   :restrictions (zx/xml-> xml-node
                           ::xs/simpleType
                           ::xs/restriction
                           zip/children
                           #(when (node/element? %) %)
                           #(let [tag (xn/qname-local (:tag %))
                                  attrs (-> % :attrs :value)]
                              (zipmap [tag] [attrs])))})

(defn- object-attr
  "Special for AS_NORMATIVE.DOCS.KINDS case"
  [xml-node attr-name]
  (zx/xml1-> xml-node
             ::xs/schema
             ::xs/element
             ::xs/complexType
             ::xs/sequence
             ::xs/element (zx/attr attr-name)))

;gar bug, duplicate collection/object name in the following files.
;ITEM/ITEMS:
;AS_CHANGE_HISTORY_251_21_04_01_01.xsd
;AS_ADM.HIERARCHY_2_251_04_04_01_01.xsd
;AS_ADDR.OBJ.DIVISION_2_251_19_04_01_01.xsd
;AS_MUN.HIERARCHY_2_251_10_04_01_01.xsd
(def gar-fix-table {"AS_CHANGE_HISTORY"    {:collection "AS_CHANGE_HISTORY"
                                            :object     "AS_CHANGE_HISTORY"}
                    "AS_ADM.HIERARCHY"     {:collection "AS_ADM_HIERARCHY"
                                            :object     "AS_ADM_HIERARCHY"}
                    "AS_ADDR.OBJ.DIVISION" {:collection "AS_ADDR_OBJ_DIVISIONS"
                                            :object     "AS_ADDR_OBJ_DIVISION"}
                    "AS_MUN.HIERARCHY"     {:collection "AS_MUN_HIERARCHY"
                                            :object     "AS_MUN_HIERARCHY"}})
(defn gar-fix
  [fix-table file-name key ok-func]
  (let [broken-scope (set (keys fix-table))
        broken? #(string/includes? file-name %)
        ;swap incorrect collection names with hardcoded values
        fix (->> (set/select broken? broken-scope)
                 (select-keys fix-table)
                 vals
                 first)]
    (if (nil? fix)
      ok-func
      (get fix key))))

(defn- read-schema
  [file-name]
  (with-open [input-file (io/input-stream file-name)]
    (doall (let [root (-> input-file xml/parse zip/xml-zip)]
             {:collection (gar-fix gar-fix-table file-name :collection
                                   (zx/xml1-> root
                                              ::xs/schema
                                              ::xs/element
                                              (zx/attr :name)))
              :object     (gar-fix gar-fix-table file-name :object
                                   ;need or for AS_NORMATIVE.DOCS.KINDS case
                                   (or (object-attr root :name)
                                       (object-attr root :ref)))
              :annotation (zx/xml1-> root
                                     ::xs/schema
                                     ::xs/element
                                     ::xs/complexType
                                     ::xs/sequence
                                     ::xs/element
                                     ::xs/annotation
                                     ::xs/documentation
                                     zx/text
                                     string/trim)
              :fields     (if (empty? (zx/xml-> root
                                                ::xs/schema
                                                ::xs/element
                                                ::xs/complexType
                                                ::xs/attribute))
                            (zx/xml-> root
                                      ::xs/schema
                                      ::xs/element
                                      ::xs/complexType
                                      ::xs/sequence
                                      ::xs/element
                                      ::xs/complexType
                                      ::xs/attribute
                                      #(parse-attribute %))
                            ;AS_NORMATIVE.DOCS.KINDS case
                            (zx/xml-> root
                                      ::xs/schema
                                      ::xs/element
                                      ::xs/complexType
                                      ::xs/attribute
                                      #(parse-attribute %)))}))))

(defn parse
  "Builds a schema from an xsd file"
  [file-name]
  (let [s (read-schema file-name)]
    (when (not (s/valid? ::specs/schema s))
      (binding [*out* *err*]
        (println (format "Specification mismatch during %s processing" file-name))
        (s/explain ::specs/schema s)))
    (s/conform ::specs/schema s)))
