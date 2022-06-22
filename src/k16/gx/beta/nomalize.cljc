(ns k16.gx.beta.nomalize
  #?(:cljs (:require-macros
            [k16.gx.beta.context :refer [merge-err-ctx with-ctx *ctx*]]))
  (:require [clojure.walk :as walk]
            [k16.gx.beta.errors :as gx.err]
            [k16.gx.beta.impl :as impl]
            [k16.gx.beta.schema :as gx.schema]
            #?(:clj [k16.gx.beta.context :refer [merge-err-ctx with-ctx *ctx*]])))

(def locals #{'gx/ref 'gx/ref-keys})

(defn local-form?
  [form]
  (and (seq? form)
       (locals (first form))))

#?(:clj
   (defn quiet-requiring-resolve
     [sym]
     (try
       (requiring-resolve sym)
       (catch Throwable _ nil))))

(defn resolve-symbol
  [sym]
  (when (symbol? sym)
    #?(:cljs (impl/namespace-symbol sym)
       :clj (some-> sym
                    (impl/namespace-symbol)
                    (quiet-requiring-resolve)
                    (var-get)))))

(defn parse-local
  [env form]
  (condp = (first form)
    'gx/ref (get env (second form))

    'gx/ref-keys (select-keys env (second form))))

(defn postwalk-evaluate
  "A postwalk runtime signal processor evaluator, works most of the time.
  Doesn't support special symbols and macros, basically just function application.
  For cljs, consider compiled components or sci-evaluator, would require allowing
  for swappable evaluation stategies. Point to docs, to inform how to swap evaluator,
  or alternative ways to specify functions (that get compiled) that can be used."
  [props form]
  (walk/postwalk
   (fn [x]
     (cond
       (local-form? x)
       (parse-local props x)

       (and (seq? x) (ifn? (first x)))
       (apply (first x) (rest x))

       :else x))
   form))

(def default-context
  {:initial-state :uninitialised
   :normalize {:form-evaluator postwalk-evaluate
               ;; signal, whish is default for static component nodes
               :auto-signal :gx/start
               :props-signals #{:gx/start}}
   :signal-mapping {}
   :signals {:gx/start {:from-states #{:stopped :uninitialised}
                        :to-state :started}
             :gx/stop {:from-states #{:started}
                       :to-state :stopped
                       ;; this is used as a sign of anti-signal and aplies
                       ;; it in reversed order
                       :deps-from :gx/start}}})

(defprotocol IRunnableForm
  (run [this] [this props]))

(defrecord RunnableForm [context env form]
  IRunnableForm
  (run [this]
    ((-> context :normalize :form-evaluator) *ctx* (:form this)))
  (run [this props]
    ((-> context :normalize :form-evaluator) props (:form this))))

(defn runnable? [v]
  (instance? RunnableForm v))

(defn form->runnable
  ([context form-def]
   (form->runnable context form-def false))
  ([context form-def quiet?]
   (let [props* (atom #{})
         resolved-form
         (->> form-def
              (walk/postwalk
               (fn [sub-form]
                 (cond
                   (locals sub-form) sub-form

                   (local-form? sub-form)
                   (do (swap! props* concat (-> sub-form rest flatten))
                       sub-form)

                   (special-symbol? sub-form)
                   (gx.err/throw-gx-err "Special forms are not supported"
                                        {:form-def form-def
                                         :token sub-form})

                   (resolve-symbol sub-form) (resolve-symbol sub-form)

                   (and quiet? (symbol? sub-form)) nil

                   (symbol? sub-form)
                   (gx.err/throw-gx-err "Unable to resolve symbol"
                                        {:form-def form-def
                                         :token sub-form})

                   :else sub-form))))]
     (->RunnableForm context @props* resolved-form))))

(defn quiet-form->runnable
  [context form-def]
  (form->runnable context form-def true))

(defprotocol INodeNormalizer
  (normalize [this]))

(def normalizable? (partial satisfies? INodeNormalizer))

(defrecord Static [context node-def]
  INodeNormalizer
  (normalize [this]
    (let [auto-signal (-> context :normalize :auto-signal)
          other-signals (-> context :signals keys set (disj auto-signal))
          runnable (form->runnable context (:node-def this))
          deps (:env runnable)
          processor (fn auto-signal-processor [{:keys [props]}]
                      (run runnable props))
          resolved-props (->> deps
                              (map (fn [dep] [dep (list 'gx/ref dep)]))
                              (into {}))
          normalized-node {:gx/processor processor
                           :gx/deps deps
                           :gx/resolved-props resolved-props}]
      (->> other-signals
           (map (fn [other-signal] [other-signal {:gx/processor :value}]))
           (into {auto-signal normalized-node})))))

(comment
  (->> {:port 8080}
       (->Static default-context)
       (normalize))

  (->> {:port '(gx/ref :z)}
       (->Static default-context)
       (normalize)))

(defn push-down-props
  [{{:keys [props-signals]} :normalize} {:gx/keys [props] :as node-def}]
  (if (and (seq props) (seq props-signals))
    (reduce-kv (fn [m k v]
                 (if (and (contains? props-signals k)
                          (not (:gx/props v)))
                   (assoc-in m [k :gx/props] props)
                   m))
               node-def
               node-def)
    node-def))

(defn init-deps
  [context component-def]
  (update-vals
   component-def
   (fn [{:gx/keys [props] :as signal-def}]
     (if props
       (let [{:keys [env form]} (some->> props (form->runnable context))]
         (merge signal-def {:gx/deps (set (or env []))
                            :gx/resolved-props form}))
       signal-def))))

(defn remap-signals
  [from-signals to-signals]
  (cond
    (and (seq from-signals) (seq to-signals))
    (if from-signals
      (->> to-signals
           (map (fn [[k v]]
                  [k (v from-signals)]))
           (into {}))
      to-signals)

    (seq from-signals) from-signals

    :else to-signals))

(defn flatten-component
  "Flattens nested components by creating one root component using
   signal mappings from context (if any)"
  [context root-component]
  (let [root-component (assoc root-component
                              :gx/signal-mapping
                              (or
                               (:gx/signal-mapping root-component)
                               (:signal-mapping context)))]
    (loop [{:gx/keys [component signal-mapping] :as current} root-component]
      (if-let [nested component]
        (recur (update nested :gx/signal-mapping
                       #(remap-signals % signal-mapping)))
        (if-let [mapping (seq (:gx/signal-mapping current))]
          (->> mapping
               (map (fn [[k v]]
                      [k (get current v)]))
               (into root-component))
          (dissoc current :gx/signal-mapping))))))

(defrecord Component [context node-def]
  INodeNormalizer
  (normalize [this]
    (merge-err-ctx {:error-type :normalize-node-component}
      (let [node-def (:node-def this)
            context (:context this)
            component-def (some->> node-def
                                   :gx/component
                                   (quiet-form->runnable context)
                                   (run)
                                   (flatten-component context))
            [issues schema component]
            (some->> component-def
                     (merge (dissoc node-def :gx/component))
                     (push-down-props context)
                     (init-deps context)
                     (gx.schema/validate-component context))]
        (cond
          (not component)
          (gx.err/throw-gx-err "Component could not be resolved"
                               {:component node-def})

          issues
          (gx.err/throw-gx-err "Component schema error"
                               {:component component
                                :component-schema schema
                                :schema-error (set issues)})

          :else component)))))

(comment
  (def my-comp
    {:gx/start {:gx/processor identity}
     :gx/stop {:gx/processor identity}})

  (->> {:gx/component 'k16.gx.beta.nomalize/my-comp
        :gx/props {:foo '(gx/ref :a)}}
       (->Component default-context)
       (normalize))
  )

(defrecord ComponentConstructor [context node-def]
  INodeNormalizer
  (normalize ^Component [{:keys [context node-def]}]
    (->Component context node-def)))

(defn function-call?
  [token]
  (and (list? token)
       (symbol? (first token))))

(defn component-type
  [_ {:gx/keys [component]}]
  (cond
    (symbol? component) ::component-def
    (function-call? component) ::component-constructor
    :else ::unsupported-component))

(defn normalized-node?
  [context node-def]
  (gx.schema/normalized? context node-def))

(defn node-type
  [context node-def]
  (cond
    (:gx/component node-def) (component-type context node-def)
    (normalized-node? context node-def) ::normalized-node
    :else ::static))

(defmulti create-normalizer node-type)

(defmethod create-normalizer ::component-def
  [context node-def]
  (->Component context node-def))

(defmethod create-normalizer ::component-constructor
  [context node-def]
  (->ComponentConstructor context node-def))

(defmethod create-normalizer ::static
  [context node-def]
  (->Static context node-def))

(defn normalize-node
  [context node-def]
  (merge-err-ctx {:error-type :normalize-node}
    (normalize (create-normalizer context node-def))))

(defn normalize-graph
  [{:keys [context graph] :as gx-map}]
  (update gx-map graph update-vals (partial normalize-node context)))
