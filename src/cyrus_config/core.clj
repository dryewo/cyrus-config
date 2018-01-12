(ns cyrus-config.core
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [cyrus-config.coerce :as c])
  (:import (java.io Writer)
           (clojure.lang ExceptionInfo)
           (java.util LinkedHashSet)
           (java.util.function Predicate)))


(defn envcasize [s]
  (-> s
      name
      (str/upper-case)
      (str/replace "-" "_")))


(def ^:private system-env (into {} (System/getenv)))
(def ^:dynamic ^:private *env-override* {})


(def default-config-spec
  {:required false
   :default  nil
   :secret   false})


(defn effective-config-definition [config-sym config-spec]
  (-> (merge default-config-spec
             {:var-name config-sym}
             config-spec)
      (update :var-name envcasize)))


(s/def ::spec some?)
(s/def ::schema some?)
(s/def ::required boolean?)
(s/def ::default any?)
(s/def ::secret boolean?)
(s/def ::var-name (s/or :string string? :keyword keyword?))
(s/def ::config-definition (s/keys :opt-un [::spec ::schema ::required ::default ::secret ::var-name]))

(s/fdef effective-config-definition
  :args (s/cat :name symbol? :definition ::config-definition))


;; When configuration constant fails to load or validate, it gets this value, which cannot be mistaken with anything else.
(deftype ConfigNotLoaded [error])
(defmethod print-method ConfigNotLoaded [c ^Writer w]
  (print-ctor c (fn [o w] (print-method (.error o) w)) w))


(defn- find-in-sources [k tagged-maps]
  (some (fn [[tag m]]
          (when (contains? m k)
            [(get m k) tag]))
        tagged-maps))


(defn load-constant [v]
  (let [{config-sym :name :keys [::definition name ns]} (meta v)
        evaled-definition    (try
                               (binding [*ns* ns]
                                 (eval definition))
                               (catch Exception e
                                 {}))
        effective-definition (effective-config-definition name evaled-definition)
        {:keys [default var-name required schema spec]} effective-definition
        [raw-value source] (find-in-sources var-name [[:override *env-override*]
                                                      [:environment system-env]
                                                      [:default (when (some? default)
                                                                  {var-name default})]])
        present              (some? source)
        [value error] (if (and required (not present))
                        (let [error {:code ::required-not-present :message "Required not present"}]
                          [(ConfigNotLoaded. error) error])
                        (when (some? raw-value)
                          (try
                            (cond
                              spec
                              [(c/coerce-to-spec spec raw-value)]
                              schema
                              [(c/coerce-to-schema schema raw-value)]
                              :else
                              [(c/coerce-to-spec string? raw-value)])
                            (catch Exception e
                              (let [error {:code ::invalid-value :value raw-value :message (str e)}]
                                [(ConfigNotLoaded. error) error])))))]
    (alter-var-root v (constantly value))
    (alter-meta! v assoc ::source source ::error error ::raw-value raw-value ::effective-definition effective-definition)))


(def ^:private registered-constants (LinkedHashSet.))


(defn register-constant [v]
  (locking registered-constants
    (.remove registered-constants v)
    (.add registered-constants v)))


(defn- find-all-vars []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (::definition (meta v))]
    v))


(defn- prune-registered-constants []
  (let [found-vars (set (find-all-vars))]
    (locking registered-constants
      (.removeIf registered-constants (reify Predicate (test [_ x] (not (contains? found-vars x))))))))


(defn- find-all-constants []
  (prune-registered-constants)
  (locking registered-constants
    (into [] registered-constants)))


(defn all []
  (for [v (find-all-constants)]
    (let [{:keys [::source ::error ::raw-value ::effective-definition doc]} (meta v)
          {:keys [secret var-name default]} effective-definition]
      {:var       v
       :var-name  (envcasize var-name)
       :value     (if error nil @v)
       :raw-value raw-value
       :error     (:message error)
       :source    source
       :secret    secret
       :doc       doc
       :default   default})))


(defn- value-or-secret [secret value]
  (if secret "<SECRET>" (pr-str value)))


(defn- format-all [constants]
  (str/join "\n" (for [{:keys [var var-name value raw-value source error secret doc default]} constants]
                   (let [show-value     (if error "<ERROR>" (value-or-secret secret value))
                         show-raw-value (value-or-secret secret raw-value)]
                     (str var ": "
                          (if source
                            (str (if error
                                   (str show-value " because " var-name " contains " show-raw-value)
                                   (str show-value " from " var-name))
                                 " in " source)
                            (str show-value " because " var-name " is not set"))
                          (when error (str " - " error))
                          (when doc (str " // " doc)))))))


(defn validate! []
  (let [errored-constants  (filter #(some? (:error %)) (all))
        error-descriptions (format-all errored-constants)]
    (when-not (empty? errored-constants)
      (throw (ex-info (str "Errors found when loading config:\n" error-descriptions) {})))))


(defn show []
  (format-all (all)))


(defn- envcasize-keys [env-map]
  (into {} (for [[k v] env-map]
             [(envcasize k) v])))


(defn reload-with-override! [env-override]
  (alter-var-root #'*env-override* (constantly (envcasize-keys env-override)))
  (doseq [v (find-all-constants)]
    (load-constant v)))


(defmacro def [name & declarators]
  (let [docstring   (if (string? (first declarators))
                      (first declarators)
                      nil)
        declarators (if (string? (first declarators))
                      (next declarators)
                      declarators)
        definition  (if (map? (first declarators))
                      (first declarators)
                      {})]
    ;; Evaluation needed to see the actual value of :required
    (let [evaled-definition (eval definition)
          {:keys [info]} evaled-definition]
      ;; Call effective-config-definition just to spec-check the arguments
      (effective-config-definition name evaled-definition)
      (when (and (:required evaled-definition)
                 (contains? evaled-definition :default))
        (throw (ex-info ":default is specified while :required is true." {})))
      (when (and (contains? evaled-definition :spec)
                 (contains? evaled-definition :schema))
        (throw (ex-info "Both :spec and :schema are specified. Please leave only one." {})))
      ;; Not using gensym because it somehow fails to work inside def (produces different symbol)
      `(let [~'unevaled-definition '~definition]
         (def ~(with-meta name (merge (when info {:doc info})
                                      (meta name)
                                      (when docstring {:doc docstring})
                                      {::definition 'unevaled-definition}))
           (ConfigNotLoaded. {:code ::reload-never-called :message "cfg/reload never called."}))
         (register-constant #'~name)
         (load-constant #'~name)))))

(s/fdef def
  :args (s/cat :name symbol? :doc (s/? string?) :definition (s/? map?)))
