(ns cyrus-config.coerce
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [spec-coerce.core :as sc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom parsers

(defn unblank [s]
  (let [str-s (str s)]
    (when-not (str/blank? str-s)
      str-s)))

(s/def ::nonblank-string (s/nilable string?))
(sc/def ::nonblank-string unblank)

(s/def ::int int?)
(s/def ::double double?)
(s/def ::keyword keyword?)
(s/def ::string string?)
(s/def ::boolean boolean?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private squeeze-coerce-to-schema
  (try
    (require 'squeeze.core)
    (resolve 'squeeze.core/coerce-config)
    (catch Exception e)))


(defn coerce-to-schema [schema data]
  (when-not squeeze-coerce-to-schema
    (throw (UnsupportedOperationException. "Library is required to use coercion with Prismatic Schema. Include [squeeze \"0.3.2\"] in your project.clj")))
  (squeeze-coerce-to-schema schema data))
