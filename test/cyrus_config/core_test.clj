(ns cyrus-config.core-test
  (:require [clojure.test :refer :all]
            [cyrus-config.core :as cfg]
            [clojure.spec.test.alpha :as st]
            [schema.core :as ps]
            [clojure.spec.alpha :as s])
  (:import (cyrus_config.core ConfigNotLoaded)
           (schema.core Schema)
           (clojure.lang ExceptionInfo)))

;; ACHTUNG Place `cfg/def` only on top level

(st/instrument)


(defn unmap-all []
  (doseq [{:keys [ns name] :as v} (map meta (#'cfg/find-all-vars))]
    ;(prn "unmapping" name v)
    (ns-unmap ns name)))

(comment
  (unmap-all))


(cfg/def definitions-1 {:info ""})
(deftest definitions
  (testing "cfg/def introduces valid definitions"
    (is (nil? definitions-1))
    (is (= (::cfg/spec (meta #'definitions-1)) {:info ""}))))


(cfg/def custom-var-name-1 {:info "" :var-name "USER"})
(cfg/def custom-var-name-2 {:info "" :var-name :user})
(deftest custom-var-name
  (testing "Can specify custom :var-name either as keyword or as normal string"
    (is (= custom-var-name-1 (System/getenv "USER")))
    (is (= custom-var-name-2 (System/getenv "USER")))))


(cfg/def required-1 {:info "" :required true})
(deftest required
  (testing "When required and not present"
    (is (instance? ConfigNotLoaded required-1))
    (is (= (.error required-1) {:code ::cfg/required-not-present :message "Required not present"}))))


(cfg/def default-1 {:info "" :default "foo"})
(deftest default
  (testing "When not present and has default"
    (is (= default-1 "foo"))))


(deftest required-and-default
  (testing "Do not allow specifying both :default and :required"
    (is (thrown? ExceptionInfo (cfg/def* 'foo {:info "" :required true :default ""})))))


(deftest spec-and-schema
  (testing "Do not allow to specify both :spec and :schema"
    (is (thrown? ExceptionInfo (cfg/def* 'foo {:info "" :spec int? :schema ps/Int})))))


(cfg/def spec-1 {:info "" :spec int? :default "1"})
(deftest spec
  (testing "Coercion applied to default values"
    (is (= spec-1 1)))
  (testing "Coercion works with overrides"
    (cfg/reload-with-override! {"SPEC_1" "2"})
    (is (= spec-1 2))))


(cfg/def schema-1 {:info "" :schema ps/Int :default "1"})
(deftest schema
  (testing "Coercion applied to default values"
    (is (= schema-1 1)))
  (testing "Coercion works with overrides"
    (cfg/reload-with-override! {"SCHEMA_1" "2"})
    (is (= schema-1 2))))


(cfg/def secret-1 {:info "" :default "Qwerty123" :secret true})
(deftest secret
  (testing "Secret pieces appear as <SECRET> in the output of (cfg/all)"
    (is (= secret-1 "Qwerty123"))
    (is (= "<SECRET>" (get-in (cfg/all) [#'cyrus-config.core-test/secret-1 :safe-value])))))
