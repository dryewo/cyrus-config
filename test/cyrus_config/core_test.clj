(ns cyrus-config.core-test
  (:require [clojure.test :refer :all]
            [cyrus-config.core :as cfg]
            [clojure.spec.test.alpha :as st]
            [schema.core :as ps]
            [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import (cyrus_config.core ConfigNotLoaded)
           (clojure.lang ExceptionInfo)))


(st/instrument)


(defn unmap-all []
  (doseq [{:keys [ns name] :as v} (map meta (#'cfg/find-all-constants))]
    (prn "unmapping" name v)
    (ns-unmap ns name)))

(comment
  (unmap-all))


(use-fixtures
  :each (fn [f]
          (cfg/reload-with-override! {})
          (f)))


(cfg/def DOCSTRINGS_1)
(cfg/def DOCSTRINGS_2 {:info "doc3"})
(cfg/def ^{:doc "doc2"} DOCSTRINGS_3 {:info "doc3"})
(cfg/def ^{:doc "doc2"} DOCSTRINGS_4 "doc1" {:info "doc3"})
(deftest docstrings
  (testing "precedence of docstrings"
    (testing "no docstring by default"
      (is (nil? (:doc (meta #'DOCSTRINGS_1)))))
    (testing ":info has the lowest"
      (is (= "doc3" (:doc (meta #'DOCSTRINGS_2)))))
    (testing "symbol metadata is higher than :info"
      (is (= "doc2" (:doc (meta #'DOCSTRINGS_3)))))
    (testing "string after symbol has the highest"
      (is (= "doc1" (:doc (meta #'DOCSTRINGS_4)))))
    (is (= nil DOCSTRINGS_1 DOCSTRINGS_2 DOCSTRINGS_3 DOCSTRINGS_4))))


(cfg/def SOURCE_1)
(cfg/def SOURCE_2 {:default "a"})
(cfg/def SOURCE_3 {:default "a" :var-name "USER"})
(deftest source
  (testing "::cfg/source is set on the var's meta depending on where the value was taken from"
    (testing "When not found anywhere and has no default, -> nil"
      (is (nil? (get (meta #'SOURCE_1) ::cfg/source :none))))
    (testing "When found in default, -> :default"
      (is (= :default (get (meta #'SOURCE_2) ::cfg/source))))
    (testing "When found in environment, -> :environment"
      (is (= :environment (get (meta #'SOURCE_3) ::cfg/source))))
    (testing "When found in override, -> :override"
      (cfg/reload-with-override! {"SOURCE_1" "1" "SOURCE_2" "2" "USER" "3"})
      (is (= :override
             (get (meta #'SOURCE_1) ::cfg/source)
             (get (meta #'SOURCE_2) ::cfg/source)
             (get (meta #'SOURCE_3) ::cfg/source))))))


(cfg/def CUSTOM_VAR_NAME_1 {:var-name "USER"})
(cfg/def CUSTOM_VAR_NAME_2 {:var-name :user})
(deftest custom-var-name
  (testing "Can specify custom :var-name either as keyword or as normal string"
    (is (= CUSTOM_VAR_NAME_1 (System/getenv "USER")))
    (is (= CUSTOM_VAR_NAME_2 (System/getenv "USER")))))


(cfg/def REQUIRED_1 {:required true})
(deftest required
  (testing "When required and not present"
    (is (instance? ConfigNotLoaded REQUIRED_1))
    (is (= (.error REQUIRED_1) {:code ::cfg/required-not-present :message "Required not present"}))))


(cfg/def DEFAULT_1 {:default "foo"})
(deftest default
  (testing "When not present and has default"
    (is (= DEFAULT_1 "foo"))))


(cfg/def SPEC_1 {:spec int? :default "1"})
(deftest spec
  (testing "Coercion applied to default values"
    (is (= SPEC_1 1)))
  (testing "Coercion works with overrides"
    (cfg/reload-with-override! {"SPEC_1" "2"})
    (is (= SPEC_1 2)))
  (testing "Invalid value"
    (cfg/reload-with-override! {:spec-1 "a"})
    (is (instance? ConfigNotLoaded SPEC_1))))

;; TODO add extended spec conforming tests


(cfg/def SCHEMA_1 {:schema ps/Int :default "1"})
(deftest schema
  (testing "Coercion applied to default values"
    (is (= SCHEMA_1 1)))
  (testing "Coercion works with overrides"
    (cfg/reload-with-override! {"SCHEMA_1" "2"})
    (is (= SCHEMA_1 2))))

;; TODO add extended schema coercion tests


(cfg/def SECRET_1 {:default "Qwerty123" :secret true})
(deftest secret
  (testing "Secret pieces appear as <SECRET> in the output of (cfg/show)"
    (is (= SECRET_1 "Qwerty123"))
    (is (not (re-seq #"Qwerty123" (cfg/show))))))


(cfg/def VALIDATE_1 {:required true})
(cfg/def VALIDATE_2 {:spec int? :default "a"})
(deftest validate
  (testing "Validation throws an exception with error descriptions"
    (is (thrown-with-msg? ExceptionInfo #"<ERROR> because VALIDATE_1 is not set - Required not present" (cfg/validate!)))
    (is (thrown-with-msg? ExceptionInfo #"<ERROR> because VALIDATE_2 contains \"a\" in :default - .* val: \"a\" fails predicate: parseInt" (cfg/validate!)))))


(cfg/def FALSE_DEFAULT_1 {:default false :spec boolean?})
(deftest false-default
  (testing "Setting false as default value works"
    (is (= FALSE_DEFAULT_1 false))
    (is (= :default (::cfg/source (meta #'FALSE_DEFAULT_1))))))


(cfg/def RELOAD_REQUIRED_1 {:spec boolean? :default true})
(cfg/def RELOAD_REQUIRED_2 {:required (not RELOAD_REQUIRED_1)})
(deftest reload-required
  (testing "Can define depending on others"
    (is (= true RELOAD_REQUIRED_1))
    (is (= nil RELOAD_REQUIRED_2))
    (cfg/reload-with-override! {"RELOAD_REQUIRED_1" "false"})
    (is (= false RELOAD_REQUIRED_1))
    (is (= ::cfg/required-not-present (-> (meta #'RELOAD_REQUIRED_2) ::cfg/error :code)))))


(cfg/def RELOAD_DEFAULT_1 {:default "1"})
(cfg/def RELOAD_DEFAULT_2 {:default RELOAD_DEFAULT_1})
(deftest reload-default
  (testing "Can use default value from others"
    (is (= "1" RELOAD_DEFAULT_2))
    (cfg/reload-with-override! {"RELOAD_DEFAULT_1" "2"})
    (is (= "2" RELOAD_DEFAULT_2))
    (cfg/reload-with-override! {"RELOAD_DEFAULT_2" "3"})
    (is (= "3" RELOAD_DEFAULT_2))))

(cfg/def VAR_NAME_1 {:var-name "USER"})
(cfg/def VAR_NAME_2 {:var-name :user})
(deftest var-name
  (testing ":var-name can be keyword or string"
    (is (= (System/getenv "USER") VAR_NAME_1))
    (is (= (System/getenv "USER") VAR_NAME_2))))


(cfg/def KEYWORD_OVERRIDE_1 {:default "aaa"})
(deftest keyword-override
  (testing "keyword keys in override map are converted to ENV_CASE"
    (is (= "aaa" KEYWORD_OVERRIDE_1))
    (cfg/reload-with-override! {:keyword-override-1 "bbb"})
    (is (= "bbb" KEYWORD_OVERRIDE_1))))


(cfg/def TRANSITIVE_DEPENDS_1)
(cfg/def TRANSITIVE_DEPENDS_2 {:required (some? TRANSITIVE_DEPENDS_1) :spec int?})
(cfg/def TRANSITIVE_DEPENDS_3 {:default TRANSITIVE_DEPENDS_2})
(deftest transitive-depends
  (testing "Constants can have their :default and :required be calculated from other constants' values"
    (is (nil? (-> (meta #'TRANSITIVE_DEPENDS_2) ::cfg/error)))
    (cfg/reload-with-override! {:transitive-depends-1 "1"})
    (is (= ::cfg/required-not-present (-> (meta #'TRANSITIVE_DEPENDS_2) ::cfg/error :code)))
    (cfg/reload-with-override! {:transitive-depends-1 "1" :transitive-depends-2 "2"})
    (is (nil? (-> (meta #'TRANSITIVE_DEPENDS_2) ::cfg/error)))
    (is (= "2" TRANSITIVE_DEPENDS_3))))


(cfg/def ENV_CASE)
(meta #'ENV_CASE)
(deftest env-case
  (testing "Can use ENV_CASE symbols"
    (is (nil? ENV_CASE))
    (cfg/reload-with-override! '{ENV_CASE "1"})
    (is (= "1" ENV_CASE))))


(defn parse-csv [csv]
  (->> (str/split (str csv) #",")
       (map str/trim)
       (map #(Integer/parseInt %))))


(cfg/def CUSTOM_PARSED {:spec (s/conformer parse-csv)})
(meta #'CUSTOM_PARSED)
(deftest custom-parsed
  (testing "Conveniently support custom conformers"
    (cfg/reload-with-override! '{CUSTOM_PARSED "1 , 2,3"})
    (is (= [1 2 3] CUSTOM_PARSED)))

  (testing "Error from custom parser"
    (cfg/reload-with-override! '{CUSTOM_PARSED "1,2,a"})
    (is (= ::cfg/invalid-value (-> (meta #'CUSTOM_PARSED) ::cfg/error :code))))

  (testing "Does not call parser when var not set"
    (cfg/reload-with-override! '{})
    (is (= nil CUSTOM_PARSED))))


;; Manual tests
(comment
  ;; Should look nice
  (cfg/validate!)
  (println (cfg/show))

  ;; Spec and schema not allowed at the same time
  (cfg/def COMPILE_ERROR_1 {:spec 1 :schema 2})

  ;; :required true and :default are not allowed at the same time
  (cfg/def COMPILE_ERROR_2 {:required true :default ""})

  ;; :var-name can only be string or keyword
  (cfg/def COMPILE_ERROR_3 {:var-name 'user}))
