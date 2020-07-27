(ns fiaser.core-test
  (:require [clojure.test :refer :all]
            [fiaser.core :refer :all]
            [clojure.spec.alpha :as s]
            [fiaser.sqlite :as sqlite]))

(deftest sqlite-type-map-conforms-spec
  (testing "sqlite/type-map keys does not conforms spec"
    (is (= (set (keys sqlite/type-map))
           (s/describe :fiaser.specs/known-bases)))))
