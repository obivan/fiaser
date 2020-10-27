(ns fiaser.xml
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [fiaser.xsd :as xsd]))

;gar bugfix for:
;AS_CHANGE_HISTORY_20200629_80f0a427-0178-4b0c-80f1-3095362c70e8.XML
;AS_ADM_HIERARCHY_20200629_efc01487-c56a-4b05-9bcf-ae74d101efe8.XML
;AS_ADDRESS_OBJECT_DIVISION_20200629_c3c1158b-cd7f-4a94-abdd-fe978fa735f8.XML
;AS_MUN_HIERARCHY_20200629_93beca4b-df7b-4514-89c2-a4edd1114c3a.XML
(def gar-fix-table {"AS_CHANGE_HISTORY"
                    {:collection "AS_CHANGE_HISTORY"}
                    "AS_ADM_HIERARCHY"
                    {:collection "AS_ADM_HIERARCHY"}
                    "AS_ADDRESS_OBJECT_DIVISION"
                    {:collection "AS_ADDR_OBJ_DIVISIONS"}
                    "AS_MUN_HIERARCHY"
                    {:collection "AS_MUN_HIERARCHY"}})

(defn stream
  [input-stream]
  (->> input-stream
       xml/parse
       :content
       (map :attrs)
       (seque 500)))

(defn top-tag
  [file-name]
  (xsd/gar-fix gar-fix-table file-name :collection
               (with-open [input-stream (io/input-stream file-name)]
                 (let [tree (xml/parse input-stream)]
                   (-> tree :tag name)))))
