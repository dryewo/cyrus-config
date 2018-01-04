(ns cyrus-config.core
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [cyrus-config.coerce :as c])
  (:import (java.io Writer)
           (clojure.lang ExceptionInfo)))


(defn keywordize [s]
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


(def default-config-spec
  {:required false
   :default  nil
   :secret   false})

(defn effective-config-spec [config-sym config-spec]
  (merge default-config-spec
         {:var-name config-sym}
         config-spec))

(s/def ::info string?)
(s/def ::spec some?)
(s/def ::schema some?)
(s/def ::required boolean?)
(s/def ::default any?)
(s/def ::secret boolean?)
(s/def ::var-name (s/or :string string? :keyword keyword?))
(s/def ::config-spec (s/keys :opt-un [::info ::spec ::schema ::required ::default ::secret ::var-name]))

(s/fdef effective-config-spec
  :args (s/cat :config-name symbol? :config-spec ::config-spec))


;; When configuration piece fails to load or validate, it gets this value, which cannot be mistaken with anything else.
(deftype ConfigNotLoaded [error])
(defmethod print-method ConfigNotLoaded [c ^Writer w]
  (print-ctor c (fn [o w] (print-method (.error o) w)) w))


(defn- find-in-sources [k tagged-maps]
  (some (fn [[tag m]]
          (when (contains? m k)
            [(get m k) tag]))
        tagged-maps))


(defn load-piece [v]
  (let [{config-sym :name :keys [::effective-spec ::user-spec]} (meta v)
        {:keys [default var-name required schema]} effective-spec
        {:keys [spec]} user-spec
        [raw-value source] (find-in-sources var-name [[:override *env-override*]
                                                      [:environment system-env]
                                                      [:default (when default {var-name default})]])
        present (some? source)
        [value error] (if (and required (not present))
                        (let [error {:code ::required-not-present :message "Required not present"}]
                          [(ConfigNotLoaded. error) error])
                        (when raw-value
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
    (alter-meta! v assoc ::source source ::error error ::raw-value raw-value)))


(defn- find-all-vars []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (::user-spec (meta v))]
    v))


(defn all []
  (into {} (for [v (find-all-vars)]
             (let [{:keys [::source ::error ::raw-value ::effective-spec]} (meta v)
                   {:keys [secret var-name info]} effective-spec]
               [v
                {:var-name  (envcasize var-name)
                 :value     (if error nil @v)
                 :raw-value raw-value
                 :error     (:message error)
                 :source    source
                 :secret    secret
                 :info      info}]))))


(defn errored []
  (into {} (filter #(some? (:error (val %))) (all))))


(defn- value-or-secret [secret value]
  (if secret "<SECRET>" (pr-str value)))


(defn- format-all [pieces]
  (str/join "\n" (for [[k {:keys [var-name value raw-value source error secret info]}] pieces]
                   (let [show-value     (if error "<ERROR>" (value-or-secret secret value))
                         show-raw-value (value-or-secret secret raw-value)]
                     (str k ": "
                          (if source
                            (if error
                              (str show-value " because " var-name " contains " show-raw-value)
                              (str show-value " from " var-name " in " source))
                            (str show-value " because " var-name " is not set"))
                          (when error (str " - " error))
                          (when info (str " // " info)))))))


(defn validate! []
  (let [errored-pieces     (errored)
        error-descriptions (format-all errored-pieces)]
    (when-not (empty? errored-pieces)
      (throw (ex-info (str "Errors found when loading config:\n" error-descriptions) {})))))


(defn show []
  (format-all (all)))


(defn reload-with-override! [env-override]
  (alter-var-root #'*env-override* (constantly (read-env-map env-override)))
  (doseq [v (find-all-vars)]
    (let [{:keys [name ::spec]} (meta v)]
      (load-piece v))))


(defmacro def [config-sym config-spec]
  ;; Evaluation needed to see the actual value of :required
  (let [evaled-config-spec (eval config-spec)]
    (when (and (:required evaled-config-spec)
               (contains? evaled-config-spec :default))
      (throw (ex-info ":default is specified while :required is true." {})))
    (when (and (contains? evaled-config-spec :spec)
               (contains? evaled-config-spec :schema))
      (throw (ex-info "Both :spec and :schema are specified. Please leave only one." {}))))
  ;; Not using gensym because it somehow fails to work inside def
  `(let [~'effective-spec (-> (effective-config-spec '~config-sym ~config-spec)
                              (update :var-name keywordize))]
     (def ~(with-meta config-sym {::user-spec config-spec ::effective-spec 'effective-spec})
       (ConfigNotLoaded. {:code ::reload-never-called :message "cfg/reload never called."}))
     (load-piece #'~config-sym)))
