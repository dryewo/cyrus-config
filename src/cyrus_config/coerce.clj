(ns cyrus-config.coerce
  (:require [clojure.spec.alpha :as s]
            [clojure.edn :as edn]
            [clojure.string :as str]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom parsers


(defn wrapped-string-parser [parser-fn]
  (fn [data]
    (if (string? data)
      (try
        (parser-fn data)
        (catch Exception _ ::s/invalid))
      data)))


(defmacro from-custom-parser [parser-fn spec]
  `(s/and
     (s/spec-impl '~parser-fn (wrapped-string-parser ~parser-fn) nil true)
     ~spec))


(defn from-edn [spec]
  (from-custom-parser edn/read-string spec))


(defn unblank [s]
  (let [str-s (str s)]
    (when-not (str/blank? str-s)
      str-s)))


(s/def ::nonblank-string (s/conformer unblank))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn wrapped-parser [spec parser-fn]
  (fn [data]
    (if (s/valid? spec data)
      data
      (try
        (parser-fn (str data))
        (catch Exception _ ::s/invalid)))))


;; TODO make it look pretty: {int? 'Integer/parseInt double? 'Double/parseDouble ...}
(def known-parsers
  {int?     (s/spec-impl 'Integer/parseInt (wrapped-parser int? #(Integer/parseInt %)) nil true)
   double?  (s/spec-impl 'Double/parseDouble (wrapped-parser double? #(Double/parseDouble %)) nil true)
   boolean? (s/spec-impl 'Boolean/parseBoolean (wrapped-parser boolean? #(Boolean/parseBoolean %)) nil true)
   keyword? (s/spec-impl 'keyword (wrapped-parser keyword? #(keyword %)) nil true)
   string?  (s/conformer str)})


(defn conformer-for-spec [spec]
  (get known-parsers spec spec))


(defn coerce-to-spec [spec data]
  (let [spec-with-conformer (conformer-for-spec spec)
        result              (s/conform spec-with-conformer data)]
    (if (= result ::s/invalid)
      (throw (Exception. (str "Error coercing " (pr-str data) ": "
                              (str/trim-newline (s/explain-str spec-with-conformer data)))))
      result)))


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
