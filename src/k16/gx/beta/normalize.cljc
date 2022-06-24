(ns k16.gx.beta.normalize
  #?(:cljs (:require-macros
            [k16.gx.beta.context :refer [merge-err-ctx]]))
  (:require [clojure.walk :as walk]
            [k16.gx.beta.errors :as gx.err]
            [k16.gx.beta.impl :as impl]
            [k16.gx.beta.schema :as gx.schema]
            #?(:clj [k16.gx.beta.context :refer [merge-err-ctx]])))

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
  ([props form]
   (postwalk-evaluate props form true))
  ([props form parse-locals?]
   (walk/postwalk
    (fn [x]
      (cond
        (local-form? x)
        (if parse-locals?
          (parse-local props x)
          x)

        (and (seq? x) (ifn? (first x)))
        (apply (first x) (rest x))

        :else x))
    form)))

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
  (parse [this])
  (run [this] [this props] [this props parse-locals?]))

(defrecord RunnableForm [context env form]
  IRunnableForm
  (parse [_this]
    ((-> context :normalize :form-evaluator) nil form false))
  (run [_this props]
    ((-> context :normalize :form-evaluator) props form)))

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

(defn empty-node-instance
  [context]
  {:gx/value nil
   :gx/state (-> context :initial-state)})

(defn deps->resolved-props [deps]
  (->> deps
       (map (fn [dep] [dep (list 'gx/ref dep)]))
       (into {})))

(defn form->signal-def [context form]
  (let [runnable (form->runnable context form)
        processor (fn auto-signal-processor [{:keys [props]}]
                    (run runnable props))
        deps (:env runnable)
        normalized-signal {:gx/processor processor
                           :gx/deps deps}]
    normalized-signal))

(defn normalize-signal [context signal-def]
  (let [processor-defined? (:gx/processor signal-def)
        partial-signal (if processor-defined?
                         signal-def
                         (form->signal-def context signal-def))

        {:gx/keys [deps processor resolved-props]
         :or {deps #{}}}
        partial-signal

        resolved-props (or resolved-props (deps->resolved-props deps))]

    (merge
     (when processor-defined?
       signal-def)
     {:gx/processor processor
      :gx/deps deps
      :gx/resolved-props resolved-props})))

(comment
  (def s1
    (normalize-signal
     default-context
     {:gx/processor (fn [{:keys [props]}] props)}))
  ((:gx/processor s1) {:props {:a 1}}))

(defn normalize-sm-auto [context sm-def]
  (let [normalized-signal (normalize-signal context sm-def)
        auto-signal (-> context :normalize :auto-signal)
        other-signals (-> context :signals keys set (disj auto-signal))]
    (->> other-signals
         (map (fn [other-signal] [other-signal {:gx/processor :value
                                                :gx/deps #{}
                                                :gx/resolved-props {}}]))
         (into {auto-signal normalized-signal})
         (merge (empty-node-instance context)))))

(comment
  (->> {:port 8080}
       (normalize-sm-auto default-context))

  (->> {:port '(gx/ref :z)}
       (normalize-sm-auto default-context)))

(defn init-props
  [context component-def]
  (update-vals
   component-def
   (fn [signal-def]
     (if (and (map? signal-def)
              (some #{:gx/props :gx/props-fn} (keys signal-def)))
       (let [{:gx/keys [props props-fn]} signal-def
             parsed-props (some->> props (form->runnable context))
             parsed-props-fn (some->> props-fn (form->runnable context))]
         (merge signal-def
                {:gx/deps (set (or (:env parsed-props) []))
                 :gx/resolved-props (:form parsed-props)
                 :gx/resolved-props-fn (:form parsed-props-fn)}))
       signal-def))))

(comment
  (init-props default-context
              {:gx/start {:gx/props-fn 'k16.gx.beta.normalize/my-props-fn
                          :gx/props '{:a (gx/ref :b)}}
               :gx/value {}}))

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

(defn normalize-sm-with-component [context sm-def]
  (merge-err-ctx {:error-type :normalize-node-component}
    (let [component-def (:gx/component sm-def)
          parsed-component (some->> component-def
                                    (quiet-form->runnable context)
                                    (parse)
                                    (flatten-component context))
          [issues schema component]
          (some->> parsed-component
                   (impl/deep-merge sm-def)
                   (merge (empty-node-instance context))
                   (push-down-props context)
                   (init-props context)
                   (gx.schema/validate-component context))]
      (cond
        (not component)
        (gx.err/throw-gx-err "Component could not be resolved"
                             {:component component-def})

        (seq issues)
        (gx.err/throw-gx-err "Component schema error"
                             {:component parsed-component
                              :component-schema schema
                              :schema-error (set issues)})

        :else component))))

(comment
  (defn ^:export my-props-fn
    [{:keys [a]}]
    (assoc a :full-name
           (str (:name a) " " (:last-name a))))

  (def ^:export my-new-component
    {:gx/start {:gx/props '(gx/ref :a)
                :gx/processor
                (fn my-new-component-handler
                  [{:keys [props]}]
                  (atom props))}})

  (try
    (normalize-node
     default-context
     [:comp {:gx/component 'k16.gx.beta.normalize/my-new-component
             :gx/start {:gx/props-fn 'k16.gx.beta.normalize/my-props-fn
                        :gx/props '(gx/ref :a)}}])
    (catch clojure.lang.ExceptionInfo e
      (ex-data e)))

  (sm-def-type
   default-context
   {:gx/component 'k16.gx.beta.normalize/my-new-component}))

(defn normalize-sm-inline [context sm-def]
  (let [signals-only (select-keys sm-def (-> context :signals keys))]
    (merge (empty-node-instance context)
           sm-def
           (update-vals signals-only (partial normalize-signal context)))))

(defn context->defined-signals [context]
  (set (keys (:signals context))))

(defn normal-sm-def? [context sm-def]
  (and (map? sm-def)
       (let [sm-valid-keys (into #{:gx/component}
                                 (context->defined-signals context))
             sm-def-keys (keys sm-def)]
         (some sm-valid-keys sm-def-keys))))

(defn normalize-sm [context sm-def]
  (let [;; is this sm-def in normal form
        normal? (normal-sm-def? context sm-def)

        partial-sm (if normal?
                     sm-def
                     (normalize-sm-auto context sm-def))

        ;; collect and normalise component if once exists
        ;; recursively calls normalize-sm
        component (let [component (:gx/component partial-sm)
                        component (if (symbol? component)
                                    (resolve-symbol component)
                                    component)]
                    (when component
                      (let [signal-mapping (or (:gx/signal-mapping partial-sm)
                                               (:signal-mapping context))]
                        (normalize-sm (merge context {:signal-mapping signal-mapping})
                                      component))))

        ;; merge component
        sm-with-component (impl/deep-merge
                           component
                           partial-sm)

        top-level-props (:gx/props sm-with-component)

        ;; select signal definitions
        signal-defs (select-keys sm-with-component (context->defined-signals context))
        ;; normalise signal definitions
        normalized-signals (update-vals signal-defs #(normalize-signal context %))

        sm (merge
            (empty-node-instance context)
            ;; include the component heritage
            (when component
              {:gx/component
               ;; dissoc the empty node keys so that coming
               ;; back on the normalise step doesnt include them
               (apply dissoc component (-> context
                                           empty-node-instance
                                           keys))})
            sm-with-component
            normalized-signals)]
     (impl/deep-merge
      component
      sm)))

(comment
  (context->defined-signals default-context)
  (normalize-sm default-context {}))

(defn- sm-def-type
  [context sm-def]
  (cond
    ;; (:gx/normalized? sm-def) ::normalized-sm

    (normal-sm-def? context sm-def)
    (let [{:gx/keys [component]} sm-def]
      (cond
        (nil? component) ::inline-sm
        (symbol? component) ::component-def
        ;; (function-call? component) ::component-constructor
        :else ::unsupported-component))

    :else ::auto-sm))

(defmulti normalize-sm' sm-def-type)

(defmethod normalize-sm' ::component-def
  [context sm-def]
  (normalize-sm-with-component context sm-def))

(defmethod normalize-sm' ::inline-sm
  [context sm-def]
  (normalize-sm-inline context sm-def))

(defmethod normalize-sm' ::auto-sm
  [context sm-def]
  (normalize-sm-auto context sm-def))

(defmethod normalize-sm' ::normalized-sm
  [_context node-def]
  node-def)

(defn normalize-node
  [context sm-def]
  (merge-err-ctx {:error-type :normalize-node
                  :node-contents sm-def}
    (normalize-sm' context sm-def)))

(defn normalize-graph
  [{:keys [context graph] :as gx-map}]
  (merge-err-ctx {:error-type :normalize-node}
    (let [normalized (->> graph
                          (map (fn [[k sm-def]]
                                 (merge-err-ctx {:node-key k}
                                   [k (normalize-node context sm-def)])))
                          (into {}))]
      (assoc gx-map :graph normalized))))
