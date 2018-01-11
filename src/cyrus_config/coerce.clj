(ns cyrus-config.coerce
  (:require [clojure.spec.alpha :as s]
            [clojure.edn :as edn]))


(def ^:private known-conformers
  {int?     #(if (int? %) % (Integer/parseInt %))
   double?  #(if (double? %) % (Double/parseDouble %))
   boolean? #(if (boolean? %) % (Boolean/parseBoolean %))
   keyword? keyword
   string?  str})


(defn- edn-conformer [data]
  (if (string? data)
    (try
      (edn/read-string data)
      (catch Exception e ::s/invalid))
    data))


(defn- conformer-for-spec [spec]
  (s/conformer (get known-conformers spec edn-conformer)))


(defn coerce-to-spec [spec data]
  (let [spec-with-conformer (s/and (conformer-for-spec spec) spec)
        result              (s/conform spec-with-conformer data)]
    (if (= result ::s/invalid)
      (throw (Exception. ^String (s/explain-str spec-with-conformer data)))
      result)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private squeeze-coerce-to-schema
  (try
    (require 'squeeze.core)
    (resolve 'squeeze.core/coerce-config)
    (catch Exception e)))


(defn coerce-to-schema [schema data]
  (when-not squeeze-coerce-to-schema
    (throw (UnsupportedOperationException. "Library is required to use coercion with Prismatic Schema. Include [squeeze \"0.3.1\"] in your project.clj")))
  (squeeze-coerce-to-schema schema data))
