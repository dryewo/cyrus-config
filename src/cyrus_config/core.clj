(ns cyrus-config.core
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [cyrus-config.coerce :as c])
  (:import (java.io Writer)
           (clojure.lang ExceptionInfo)))


(defn- keywordize [s]
  (-> s
      (name)
      (str/lower-case)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))


(defn envcasize [s]
  (-> s
      name
      (str/upper-case)
      (str/replace "-" "_")))


(defn- read-env-map [env-map]
  (into {} (for [[k v] env-map]
             [(keywordize k) v])))


(def ^:private system-env (read-env-map (System/getenv)))

(def ^:dynamic ^:private *env-override* {})

(defn- effective-env []
  (merge system-env *env-override*))


(def default-config-spec
  {:required false
   :default  nil
   :secret   false})

(defn effective-config-spec [config-sym config-spec]
  (merge default-config-spec
         {:var-name config-sym}
         config-spec))


;; When configuration piece fails to load or validate, it gets this value, which cannot be mistaken with anything else.
(deftype ConfigNotLoaded [error])
(defmethod print-method ConfigNotLoaded [c ^Writer w]
  (print-ctor c (fn [o w] (print-method (.error o) w)) w))


;; If possible, load and coerce, otherwise set to ConfigNotLoaded
(defn- load-value [config-sym config-spec]
  (let [{:keys [var-name required default] :as eff-spec} (effective-config-spec config-sym config-spec)
        eff-var-name (keywordize var-name)
        eff-env      (effective-env)
        present      (contains? eff-env eff-var-name)
        value        (get eff-env eff-var-name default)]
    ;(prn config-sym)
    ;(clojure.pprint/pprint eff-spec)
    (if (and required (not present))
      (ConfigNotLoaded. {:code ::required-not-present :message "Required not present"})
      (when value
        (try
          (cond
            (contains? config-spec :spec)
            (c/coerce-to-spec (:spec config-spec) value)
            (contains? config-spec :schema)
            (c/coerce-to-schema (:schema config-spec) value)
            :else
            (c/coerce-to-spec string? value))
          (catch Exception e
            (ConfigNotLoaded. {:code ::invalid-value :value value :message (str e)})))))))


(defn def* [config-sym config-spec]
  (when (and (:required config-spec)
             (contains? config-spec :default))
    (throw (ex-info "Both :default and :required are specified. Please leave only one." {})))
  (when (and (contains? config-spec :spec)
             (contains? config-spec :schema))
    (throw (ex-info "Both :spec and :schema are specified. Please leave only one." {})))
  (let [initial-value# (load-value config-sym config-spec)]
    `(def ~(with-meta config-sym {::spec config-spec}) '~initial-value#)))

(s/def ::info string?)
(s/def ::spec some?)
(s/def ::schema some?)
(s/def ::required boolean?)
(s/def ::default any?)
(s/def ::secret boolean?)
(s/def ::var-name (s/or :string string? :keyword keyword?))
(s/def ::config-spec (s/keys :req-un [::info]
                             :opt-un [::spec ::schema ::required ::default ::secret ::var-name]))

(s/fdef def*
  :args (s/cat :config-name symbol? :config-spec ::config-spec))


(defn- find-all-vars []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (:cyrus-config.core/spec (meta v))]
    v))


(defn all []
  (into {} (for [v (find-all-vars)]
             (let [{:keys [ns name ::spec]} (meta v)
                   {:keys [var-name secret]} spec]
               (let [error (when (instance? ConfigNotLoaded @v) (.error @v))]
                 [v
                  {:source-var-name (envcasize (or var-name name))
                   :safe-value      (cond
                                      error (:value error)
                                      secret "<SECRET>"
                                      :else @v)
                   :error           (:message error)}])))))


(defn errored []
  (into {} (filter #(some? (:error (val %))) (all))))


(defn format-errors [errored-vars]
  (str/join "\n" (for [[k {:keys [safe-value error source-var-name]}] errored-vars]
                   (str k ": " source-var-name "=" (pr-str safe-value) " - " error))))


(defn format-all [all-vars]
  (str/join "\n" (for [[k {:keys [safe-value source-var-name]}] all-vars]
                   (str k ": " (pr-str safe-value) " from " source-var-name))))


(defn validate! []
  (let [errored-pieces     (errored)
        error-descriptions (format-errors errored-pieces)]
    (when-not (empty? errored-pieces)
      (throw (ex-info (str "Errors found when loading config:\n" error-descriptions "\n") {})))))


(defn show []
  (format-all (all)))


(defn reload-with-override! [env-override]
  (alter-var-root #'*env-override* (constantly (read-env-map env-override)))
  ;; TODO optimize: reload only affected config pieces
  (doseq [v (find-all-vars)]
    (let [{:keys [name ::spec]} (meta v)]
      (alter-var-root v (constantly (load-value name spec))))))


(defn reload! []
  (reload-with-override! {}))


(defn startup! []
  (reload!)
  (validate!))


(defmacro def [config-sym config-spec]
  ;(println (str "(cfg/def " config-sym " " (pr-str config-spec) ")"))
  (def* config-sym (eval config-spec)))
