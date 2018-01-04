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
  (doseq [{:keys [ns name] :as v} (map meta (#'cfg/find-all-vars))]
    (prn "unmapping" name v)
    (ns-unmap ns name)))

(comment
  (unmap-all))


(cfg/def definitions-1 {:info "Example cfg for test"})
(deftest definitions
  (testing "cfg/def introduces valid definitions"
    (is (nil? definitions-1))
    (is (= (::cfg/user-spec (meta #'definitions-1)) {:info "Example cfg for test"}))))


(cfg/def source-1 {})
(cfg/def source-2 {:default "a"})
(cfg/def source-3 {:default "a" :var-name :user})
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


(cfg/def schema-1 {:schema ps/Int :default "1"})
(deftest schema
  (testing "Coercion applied to default values"
    (is (= schema-1 1)))
  (testing "Coercion works with overrides"
    (cfg/reload-with-override! {"SCHEMA_1" "2"})
    (is (= schema-1 2))))


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
    (is (thrown-with-msg? ExceptionInfo #"<ERROR> because VALIDATE_2 contains \"a\" - java.lang.NumberFormatException" (cfg/validate!)))))


;; Manual tests
(comment
  ;; Should throw
  (macroexpand-1 '(cfg/def validate-1 {:spec 1 :schema 2}))
  (macroexpand-1 '(cfg/def validate-1 {:required true :default ""}))
  ;; Should not throw
  (macroexpand-1 '(cfg/def validate-1 {:required (not true) :default ""})))
