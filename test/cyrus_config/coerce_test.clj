(ns cyrus-config.coerce-test
  (:require [clojure.test :refer :all]
            [cyrus-config.coerce :refer :all]
            [clojure.spec.alpha :as s]
            [schema.core :as ps]))


(deftest to-spec
  (testing "Coercions work and are idempotent"
    (are [?in ?spec ?out]
      (do (is (= ?out (coerce-to-spec ?spec ?in)))
          (is (= ?out (coerce-to-spec ?spec ?out))))

      "123" int? 123
      "1.5" double? 1.5
      12345 string? "12345"
      "foo" keyword? :foo
      ;; From EDN
      "[1 2 3]" (s/coll-of int?) [1 2 3]))

  (testing "Throws good error messages"
    (are [in? ?spec ?exception-message-regex]
      (is (thrown-with-msg? Exception ?exception-message-regex
                            (coerce-to-spec ?spec in?)))
      "a" int? #"For input string: \"a\""
      "[1 2" (s/coll-of int?) #"fails predicate.*get known-conformers"
      "[1.5]" (s/coll-of int?) #"val: 1.5 fails predicate: int?")))


(deftest to-schema
  (testing "Coercions work and are idempotent"
    (are [?in ?schema ?out]
      (do (is (= ?out (coerce-to-schema ?schema ?in)))
          (is (= ?out (coerce-to-schema ?schema ?out))))

      "123" ps/Int 123
      "1.5" ps/Num 1.5
      12345 ps/Str "12345"
      "foo" ps/Keyword :foo
      ;; From YAML
      "[1, 2, 3]" [ps/Int] [1 2 3]))

  (testing "Throws good error messages"
    (are [in? ?schema ?exception-message-regex]
      (is (thrown-with-msg? Exception ?exception-message-regex
                            (coerce-to-schema ?schema in?)))
      "a" ps/Int #"\(not \(integer"
      "[1, 2" [ps/Int] #"\(not \(sequential"
      "[1.5]" [ps/Int] #"\[\(not \(integer")))
