(ns cyrus-config.coerce-test
  (:require [clojure.test :refer :all]
            [cyrus-config.coerce :refer :all :as c]
            [clojure.spec.alpha :as s]
            [schema.core :as ps]
            [cheshire.core :as json]))


(deftest to-spec
  (testing "Coercions work and are idempotent"
    (are [?in ?spec ?out]
      (do (is (= ?out (coerce-to-spec ?spec ?in)))
          (is (= ?out (coerce-to-spec ?spec ?out))))

      "123" int? 123
      "1.5" double? 1.5
      1 double? 1.0
      12345 string? "12345"
      "foo" keyword? :foo
      'foo keyword? :foo

      "" ::c/nonblank-string nil
      "  " ::c/nonblank-string nil
      " 1/ " ::c/nonblank-string " 1/ "

      ;; From JSON
      "[1, 2, 3]" (from-custom-parser json/parse-string (s/coll-of int?)) [1 2 3]
      "{\"foo\": 1, \"bar\": true}" (from-custom-parser json/parse-string (s/map-of string? any?)) {"foo" 1 "bar" true}

      ;; From EDN
      "[1 2 3]" (from-edn (s/coll-of int?)) [1 2 3]))

  (testing "Throws good error messages"
    (are [in? ?spec ?exception-message-regex]
      (is (thrown-with-msg? Exception ?exception-message-regex
                            (coerce-to-spec ?spec in?)))
      "a" int? #"\"a\" - failed: parseInt"
      1.0 int? #"1.0 - failed: parseInt"
      "[1 2" (from-edn (s/coll-of int?)) #"\"\[1 2\" - failed: read-string"
      "[1 1.5]" (from-edn (s/coll-of int?)) #"1.5 - failed: int\? in: \[1\]")))


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
