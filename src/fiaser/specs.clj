(ns fiaser.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [camel-snake-kebab.core :as csk]))

(defmacro with-conformer
  [bind & body]
  `(s/conformer
     (fn [~bind]
       (try
         ~@body
         (catch Exception _#
           ::s/invalid)))))

(s/def ::non-empty-string (every-pred string? not-empty))

(defn- trim-xs-prefix [s] (string/replace-first s #"^xs:" ""))
(defn- normalize-integer [s] (string/replace s #"^int$" "integer"))

(s/def ::known-bases #{:integer :byte :string :date :boolean :long :empty})
(s/def ::str->base
  (s/and ::non-empty-string
         (with-conformer
           val
           (-> val
               trim-xs-prefix
               normalize-integer
               keyword))
         ::known-bases))

(s/def ::->maybe-int
  (s/conformer
    (fn [val]
      (try
        (Integer/parseInt val)
        (catch Exception _e
          val)))))

(s/def ::maybe-int
  (s/and ::non-empty-string
         ::->maybe-int))

(s/def ::known-restrictions #{:total-digits
                              :min-length
                              :max-length
                              :length
                              :enumeration
                              :pattern})
(s/def ::->kebab-kw
  (s/and ::non-empty-string
         (with-conformer val (csk/->kebab-case-keyword val))
         ::known-restrictions))
(s/def ::restriction (s/map-of ::->kebab-kw ::maybe-int :conform-keys true))

(s/def :field/name ::non-empty-string)
(s/def :field/annotation ::non-empty-string)
(s/def :field/base ::str->base)
(s/def :field/use #{:required :optional})
(s/def :field/restrictions (s/coll-of ::restriction))

(s/def ::field (s/keys :req-un [:field/name
                                :field/annotation
                                :field/base
                                :field/use
                                :field/restrictions]))

(s/def :schema/collection ::non-empty-string)
(s/def :schema/object ::non-empty-string)
(s/def :schema/annotation ::non-empty-string)
(s/def :schema/fields (s/coll-of ::field))

(s/def ::schema (s/keys :req-un [:schema/collection
                                 :schema/object
                                 :schema/annotation
                                 :schema/fields]))
