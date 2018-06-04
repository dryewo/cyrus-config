(ns cyrus-config.core-test
  (:require [clojure.test :refer :all]
            [cyrus-config.core :as cfg]
            [clojure.spec.test.alpha :as st]
            [schema.core :as ps]
            [clojure.spec.alpha :as s])
  (:import (cyrus_config.core ConfigNotLoaded)
           (schema.core Schema)
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


(cfg/def docstrings-1)
(cfg/def docstrings-2 {:info "doc3"})
(cfg/def ^{:doc "doc2"} docstrings-3 {:info "doc3"})
(cfg/def ^{:doc "doc2"} docstrings-4 "doc1" {:info "doc3"})
(deftest docstrings
  (testing "precedence of docstrings"
    (testing "no docstring by default"
      (is (nil? (:doc (meta #'docstrings-1)))))
    (testing ":info has the lowest"
      (is (= "doc3" (:doc (meta #'docstrings-2)))))
    (testing "symbol metadata is higher than :info"
      (is (= "doc2" (:doc (meta #'docstrings-3)))))
    (testing "string after symbol has the highest"
      (is (= "doc1" (:doc (meta #'docstrings-4)))))
    (is (= nil docstrings-1 docstrings-2 docstrings-3 docstrings-4))))


(cfg/def source-1)
(cfg/def source-2 {:default "a"})
(cfg/def source-3 {:default "a" :var-name "USER"})
(deftest source
  (testing "::cfg/source is set on the var's meta depending on where the value was taken from"
    (testing "When not found anywhere and has no default, -> nil"
      (is (nil? (get (meta #'source-1) ::cfg/source :none))))
    (testing "When found in default, -> :default"
      (is (= :default (get (meta #'source-2) ::cfg/source))))
    (testing "When found in environment, -> :environment"
      (is (= :environment (get (meta #'source-3) ::cfg/source))))
    (testing "When found in override, -> :override"
      (cfg/reload-with-override! {"SOURCE_1" "1" "SOURCE_2" "2" "USER" "3"})
      (is (= :override
             (get (meta #'source-1) ::cfg/source)
             (get (meta #'source-2) ::cfg/source)
             (get (meta #'source-3) ::cfg/source))))))


(cfg/def custom-var-name-1 {:var-name "USER"})
(cfg/def custom-var-name-2 {:var-name :user})
(deftest custom-var-name
  (testing "Can specify custom :var-name either as keyword or as normal string"
    (is (= custom-var-name-1 (System/getenv "USER")))
    (is (= custom-var-name-2 (System/getenv "USER")))))


(cfg/def required-1 {:required true})
(deftest required
  (testing "When required and not present"
    (is (instance? ConfigNotLoaded required-1))
    (is (= (.error required-1) {:code ::cfg/required-not-present :message "Required not present"}))))


(cfg/def default-1 {:default "foo"})
(deftest default
  (testing "When not present and has default"
    (is (= default-1 "foo"))))


(cfg/def spec-1 {:spec int? :default "1"})
(deftest spec
  (testing "Coercion applied to default values"
    (is (= spec-1 1)))
  (testing "Coercion works with overrides"
    (cfg/reload-with-override! {"SPEC_1" "2"})
    (is (= spec-1 2)))
  (testing "Invalid value"
    (cfg/reload-with-override! {:spec-1 "a"})
    (is (instance? ConfigNotLoaded spec-1))))

;; TODO add extended spec conforming tests


(cfg/def schema-1 {:schema ps/Int :default "1"})
(deftest schema
  (testing "Coercion applied to default values"
    (is (= schema-1 1)))
  (testing "Coercion works with overrides"
    (cfg/reload-with-override! {"SCHEMA_1" "2"})
    (is (= schema-1 2))))

;; TODO add extended schema coercion tests


(cfg/def secret-1 {:default "Qwerty123" :secret true})
(deftest secret
  (testing "Secret pieces appear as <SECRET> in the output of (cfg/show)"
    (is (= secret-1 "Qwerty123"))
    (is (not (re-seq #"Qwerty123" (cfg/show))))))


(cfg/def validate-1 {:required true})
(cfg/def validate-2 {:spec int? :default "a"})
(deftest validate
  (testing "Validation throws an exception with error descriptions"
    (is (thrown-with-msg? ExceptionInfo #"<ERROR> because VALIDATE_1 is not set - Required not present" (cfg/validate!)))
    (is (thrown-with-msg? ExceptionInfo #"<ERROR> because VALIDATE_2 contains \"a\" in :default - java.lang.NumberFormatException" (cfg/validate!)))))


(cfg/def false-default-1 {:default false :spec boolean?})
(deftest false-default
  (testing "Setting false as default value works"
    (is (= false-default-1 false))
    (is (= :default (::cfg/source (meta #'false-default-1))))))


(cfg/def reload-required-1 {:spec boolean? :default true})
(cfg/def reload-required-2 {:required (not reload-required-1)})
(deftest reload-required
  (testing "Can define depending on others"
    (is (= true reload-required-1))
    (is (= nil reload-required-2))
    (cfg/reload-with-override! {"RELOAD_REQUIRED_1" "false"})
    (is (= false reload-required-1))
    (is (= ::cfg/required-not-present (-> (meta #'reload-required-2) ::cfg/error :code)))))


(cfg/def reload-default-1 {:default "1"})
(cfg/def reload-default-2 {:default reload-default-1})
(deftest reload-default
  (testing "Can use default value from others"
    (is (= "1" reload-default-2))
    (cfg/reload-with-override! {"RELOAD_DEFAULT_1" "2"})
    (is (= "2" reload-default-2))
    (cfg/reload-with-override! {"RELOAD_DEFAULT_2" "3"})
    (is (= "3" reload-default-2))))

(cfg/def var-name-1 {:var-name "USER"})
(cfg/def var-name-2 {:var-name :user})
(deftest var-name
  (testing ":var-name can be keyword or string"
    (is (= (System/getenv "USER") var-name-1))
    (is (= (System/getenv "USER") var-name-2))))


(cfg/def keyword-override-1 {:default "aaa"})
(deftest keyword-override
  (testing "keyword keys in override map are converted to ENV_CASE"
    (is (= "aaa" keyword-override-1))
    (cfg/reload-with-override! {:keyword-override-1 "bbb"})
    (is (= "bbb" keyword-override-1))))


(cfg/def transitive-depends-1)
(cfg/def transitive-depends-2 {:required (some? transitive-depends-1) :spec int?})
(cfg/def transitive-depends-3 {:default transitive-depends-2})
(deftest transitive-depends
  (testing "Constants can have their :default and :required be calculated from other constants' values"
    (is (nil? (-> (meta #'transitive-depends-2) ::cfg/error)))
    (cfg/reload-with-override! {:transitive-depends-1 "1"})
    (is (= ::cfg/required-not-present (-> (meta #'transitive-depends-2) ::cfg/error :code)))
    (cfg/reload-with-override! {:transitive-depends-1 "1" :transitive-depends-2 "2"})
    (is (nil? (-> (meta #'transitive-depends-2) ::cfg/error)))
    (is (= "2" transitive-depends-3))))


(cfg/def ENV_CASE)
(meta #'ENV_CASE)
(deftest env-case
  (testing "Can use ENV_CASE symbols"
    (is (nil? ENV_CASE))
    (cfg/reload-with-override! '{ENV_CASE "1"})
    (is (= "1" ENV_CASE))))


;; Manual tests
(comment
  ;; Should look nice
  (println (cfg/show))

  ;; Spec and schema not allowed at the same time
  (cfg/def compile-error-1 {:spec 1 :schema 2})

  ;; :required true and :default are not allowed at the same time
  (cfg/def compile-error-2 {:required true :default ""})

  ;; :var-name can only be string or keyword
  (cfg/def compile-error-3 {:var-name 'user}))
