(ns cyrus-config.coerce-test
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [cyrus-config.coerce :as c :refer :all]
            [schema.core :as ps]
            [spec-coerce.core :as sc]))

(defn parse-csv [csv]
  (if (sequential? csv)
    csv
    (->> (str/split (str csv) #",")
         (map str/trim))))

(defn parse-json [x]
  (if (string? x)
    (json/parse-string x)
    x))

(defn parse-edn [x]
  (if (string? x)
    (try (edn/read-string x)
         (catch RuntimeException e x))
    x))

(s/def ::csv (s/conformer parse-csv))
(sc/def ::csv parse-csv)

(s/def ::json-coll-of-int (s/coll-of ::c/int))
(sc/def ::json-coll-of-int parse-json)

(s/def ::json-string-keyed-map (s/map-of ::c/string any?))
(sc/def ::json-string-keyed-map parse-json)

(s/def ::edn-coll-of-int (s/coll-of ::c/int))
(sc/def ::edn-coll-of-int parse-edn)

(s/def ::branching (s/or :kw (s/and keyword? #{:a})
                         :num ::c/int))


(= (sc/coerce! ::json-coll-of-int "[1, 2, 3]") [1 2 3])

(deftest to-spec
  (testing "Coercions work and are idempotent"
    (are [?in ?spec ?out]
        (do (is (= ?out (sc/coerce! ?spec ?in)))
            (is (= ?out (sc/coerce! ?spec ?out))))

      "123"                         ::c/int                 123
      "1.5"                         ::c/double              1.5
      1                             ::c/double              1.0
      12345                         ::c/string              "12345"
      "foo"                         ::c/keyword             :foo
      'foo                          ::c/keyword             :foo
      ""                            ::c/nonblank-string     nil
      "  "                          ::c/nonblank-string     nil
      " 1/ "                        ::c/nonblank-string     " 1/ "
      "one,two"                     ::csv                   ["one" "two"]
      "[1, 2, 3]"                   ::json-coll-of-int      [1 2 3]
      "{\"foo\": 1, \"bar\": true}" ::json-string-keyed-map {"foo" 1 "bar" true}
      "[1 2 3]"                     ::edn-coll-of-int       [1 2 3]))

  (testing "Throws good error messages"
    (are [?in ?spec ?ex-msg ?ex-data]
        (let [exception (try (sc/coerce! ?spec ?in)
                             (catch Exception e (Throwable->map e)))]
          (is (= ?ex-data (:data exception)))
          (is (= ?ex-msg (:cause exception))))
      "a"       ::c/int           "Failed to coerce value" {:spec  ::c/int,
                                                            :value "a"}
      :hi       ::c/int           "Failed to coerce value" {:spec  ::c/int,
                                                            :value :hi}
      "[1 2"    ::edn-coll-of-int "Failed to coerce value" {:spec  ::edn-coll-of-int,
                                                            :value "[1 2"}
      "[1 1.5]" ::edn-coll-of-int "Failed to coerce value" {:spec  ::edn-coll-of-int,
                                                            :value "[1 1.5]" }))

  (testing "Works with branching specs"
    (are [?in ?spec ?out]
        (= ?out (sc/coerce! ?spec ?in))
      ":a" ::branching :a)))


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
    (are [?in ?schema ?exception-message-regex]
        (is (thrown-with-msg? Exception ?exception-message-regex
                              (coerce-to-schema ?schema ?in)))
      "a" ps/Int #"\(not \(integer"
      "[1, 2" [ps/Int] #"\(not \(sequential"
      "[1.5]" [ps/Int] #"\[\(not \(integer")))
