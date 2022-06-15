(ns k16.gx.beta.core
  (:refer-clojure :exclude [ref])
  #?(:cljs (:require-macros [k16.gx.beta.error-context :refer [with-err-ctx]]))
  (:require [clojure.walk :as walk]
            [malli.core :as m]
            [malli.error :as me]
            [promesa.core :as p]
            [k16.gx.beta.impl :as impl]
            [k16.gx.beta.schema :as gx.schema]
            [k16.gx.beta.errors :as gx.err]
            #?(:clj [k16.gx.beta.error-context :refer [with-err-ctx]]))
  (:import #?(:clj [clojure.lang ExceptionInfo])))

(def locals #{'gx/ref 'gx/ref-keys})

(defn local-form?
  [form]
  (and (seq? form)
       (locals (first form))))

(def default-context
  {:initial-state :uninitialised
   :normalize {;; signal, whish is default for static component nodes
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

(defn ref
  [key]
  (list 'gx/ref key))

(defn ref-keys
  [& keys]
  (apply list (conj keys 'gx/ref-keys)))

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

(defn form->runnable [form-def]
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

                  (symbol? sub-form)
                  (gx.err/throw-gx-err "Unable to resolve symbol"
                                       {:form-def form-def
                                        :token sub-form})

                  :else sub-form))))]
    {:env @props*
     :form resolved-form}))

(defn normalize-signal-def [signal-def]
  (let [;; is this map a map based def, or a runnable form
        def? (and (map? signal-def)
                  (some #{:gx/props :gx/props-fn
                          :gx/processor :gx/deps
                          :gx/resolved-props}
                        (keys signal-def)))
        with-pushed-down-form
        (if def?
          signal-def
          (let [{:keys [form env]} (form->runnable signal-def)]
            {:gx/processor (fn auto-signal-processor [{:keys [props]}]
                             (postwalk-evaluate props form))
             :gx/deps env
             :gx/resolved-props (->> env
                                     (map (fn [dep]
                                            [dep (list 'gx/ref dep)]))
                                     (into {}))}))
        resolved-props-fn (some-> with-pushed-down-form
                                  :gx/props-fn
                                  (resolve-symbol))
        with-resolved-props
        (if (:gx/resolved-props with-pushed-down-form)
          with-pushed-down-form
          (let [{:keys [form env]} (form->runnable
                                    (:gx/props with-pushed-down-form))]
            (merge with-pushed-down-form
                   {:gx/resolved-props form
                    :gx/resolved-props-fn resolved-props-fn
                    :gx/deps env})))]
    with-resolved-props))

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

(defn resolve-component
  "Resolve component by it's symbol and validate against malli schema"
  [context component]
  (when component
    (with-err-ctx {:error-type :normalize-node-component}
      (let [resolved (some->> component
                              (resolve-symbol)
                              (flatten-component context))
            [issues schema] (when resolved
                              (gx.schema/validate-component context resolved))]
        (cond
          (not resolved)
          (gx.err/throw-gx-err "Component could not be resolved"
                               {:component component})

          issues
          (gx.err/throw-gx-err "Component schema error"
                               {:component resolved
                                :component-schema schema
                                :schema-error (set issues)})

          :else resolved)))))

(defn normalize-node-def
  "Given a component definition, "
  [{:keys [context initial-graph]} node-key node-definition]
  (if (:gx/normalized? node-definition)
    node-definition
    (with-err-ctx {:error-type :normalize-node
                   :node-key node-key
                   :node-contents (node-key initial-graph)}
      (let [{:keys [initial-state]} context
            {:keys [auto-signal]} (:normalize context)
            ;; set of signals defined in the graph
            signals (set (keys (:signals context)))
            ;; is this map a map based def, or a runnable form
            def? (and (map? node-definition)
                      (some (into #{} (conj signals :gx/component))
                            (keys node-definition)))
            with-pushed-down-form
            (if def?
              node-definition
              (->> (disj signals auto-signal)
                   (map (fn [other-signal]
                          [other-signal
                           {;; :value just passes value and
                            ;; supports state transitions of
                            ;; auto-components for all other signals
                            :gx/processor :value}]))
                   (into {auto-signal node-definition})))
            component (some->> with-pushed-down-form
                               :gx/component
                               (resolve-component context))
           ;; merge in component
           ;; TODO support nested gx/component
           ;; TODO support signal-mapping
            with-component (impl/deep-merge
                            component (dissoc with-pushed-down-form
                                              :gx/component))
            normalized-def (merge
                            (push-down-props context with-component)
                            {:gx/state initial-state
                             :gx/value nil})

            signal-defs (select-keys normalized-def signals)
            normalised-signal-defs
            (->> signal-defs
                 (map (fn [[signal-key signal-def]]
                        [signal-key (normalize-signal-def signal-def)]))
                 (into {}))]
        (merge normalized-def
               normalised-signal-defs
               ;; Useful information, but lets consider semantics before
               ;; using the value to determine behaviour
               {:gx/type (if def? :component :static)
                :gx/normalized? true})))))

(defn signal-dependencies
  [{:keys [signals]}]
  (->> signals
       (map (fn [[k v]]
              [k (if-let [f (:deps-from v)]
                   #{f}
                   #{})]))
       (into {})))

(defn validate-context
  "Validates context against schema and checks signal dependency errors"
  [context]
  (or (gx.schema/validate-context context)
      (let [deps (signal-dependencies context)]
        (->> deps
             (impl/sccs)
             (impl/dependency-errors deps)
             (map impl/human-render-dependency-error)
             (seq)))))

(defn normalize
  "Given a graph definition and config, return a normalised form. Idempotent.
   This acts as the static analysis step of the graph.
   Returns tuple of error explanation (if any) and normamized graph."
  [{:keys [context graph] :or {context default-context} :as gx-map}]
  (let [config-issues (validate-context context)
        gx-map (assoc gx-map :context context)
        ;; remove previous normalization errors
        gx-map' (cond-> gx-map
                  (not (:initial-graph gx-map)) (assoc :initial-graph graph)
                  :always (dissoc :failures))]
    (try
      (cond
        config-issues (throw (ex-info "GX Context error" config-issues))
        :else (->> graph
                   (map (fn [[k v]]
                          [k (normalize-node-def gx-map' k v)]))
                   (into {})
                   (assoc gx-map' :graph)))
      (catch ExceptionInfo e
        (update gx-map' :failures conj (gx.err/ex->gx-err-data e))))))

(defn graph-dependencies [graph signal-key]
  (->> graph
       (map (fn [[k node]]
              (let [deps (-> node
                             signal-key
                             :gx/deps)]
                [k (into #{} deps)])))
       (into {})))

(defn topo-sort
  "Sorts graph nodes according to signal topology, returns vector of
   [error, sorted nodes]"
  ([gx-map signal-key]
   (topo-sort gx-map signal-key #{}))
  ([{:keys [context graph]} signal-key priority-selector]
   (with-err-ctx {:error-type :deps-sort :signal-key signal-key}
     (try
       (if-let [signal-config (get-in context [:signals signal-key])]
         (let [deps-from (or (:deps-from signal-config) signal-key)
               selector-deps (reduce (fn [acc k]
                                       (->> [k deps-from :gx/deps]
                                            (get-in graph)
                                            (set)
                                            (assoc acc k)))
                                     {} priority-selector)
               graph-deps
               (->> deps-from
                    (graph-dependencies graph)
                    (map (fn [[k deps :as signal-deps]]
                           (let [node-selector
                                 (->> selector-deps
                                      (filter (fn [[_ d]] (not (contains? d k))))
                                      (map first)
                                      (set))]
                             (if (contains? node-selector k)
                               signal-deps
                               [k (into deps node-selector)]))))
                    (into {}))
               sorted-raw (impl/sccs graph-deps)]
           (when-let [errors (->> sorted-raw
                                  (impl/dependency-errors graph-deps)
                                  (map impl/human-render-dependency-error)
                                  (seq))]
             (gx.err/throw-gx-err "Dependency errors" {:errors errors}))
           [nil
            (let [topo-sorted (map first sorted-raw)]
              ;; if signal takes deps from another signal then it is anti-signal
              (if (:deps-from signal-config)
                (reverse topo-sorted)
                topo-sorted))])
         (gx.err/throw-gx-err (str "Unknown signal key '" signal-key "'")))
       (catch ExceptionInfo e
         [(assoc (ex-data e) :message (ex-message e))])))))

(defn get-component-props
  [graph property-key]
  (->> graph
       (map (fn [[k node]]
              [k (get node property-key)]))
       (into {})))

(defn system-failure [gx-map]
  (get-component-props (:graph gx-map) :gx/failure))

(defn system-value [gx-map]
  (get-component-props (:graph gx-map) :gx/value))

(defn system-state [gx-map]
  (get-component-props (:graph gx-map) :gx/state))

(defn props-validate-error
  [schema props]
  (when-let [error (and schema (m/explain schema props))]
    (with-err-ctx {:error-type :props-validation}
      (gx.err/gx-err-data "Props validation error"
                          {:props-value props
                           :props-schema schema
                           :schema-error (me/humanize error)}))))

(defn- run-props-fn
  [props-fn arg-map]
  (try
    (props-fn arg-map)
    (catch #?(:clj Exception :cljs js/Error) e
      (gx.err/throw-gx-err "Props function error"
                           {:ex-message (impl/error-message e)
                            :args arg-map}))))

(defn- run-processor
  [processor arg-map]
  (try
    [nil (processor arg-map)]
    (catch #?(:clj Exception :cljs js/Error) e
      [(gx.err/gx-err-data "Signal processor error"
                           {:ex-message (impl/error-message e)
                            :args arg-map})
       nil])))

(defn node-signal
  "Trigger a signal through a node, assumes dependencies have been run.
   Subsequent signal calls is supported, but it should be handled in it's
   implementation. For example, http server component checks that it
   already started and does nothing to prevent port taken error or it
   can restart itself by taking recalculated properties from deps.
   Static nodes just recalculates its values.
   If node does not support signal then do nothing."
  [{:keys [context graph initial-graph]} node-key signal-key]
  (let [signal-config (-> context :signals signal-key)
        {:keys [deps-from from-states to-state]} signal-config
        node (get graph node-key)
        node-state (:gx/state node)
        signal-def (get node signal-key)
        {:gx/keys [processor props-schema resolved-props]} signal-def
        ;; take deps from another signal of node if current signal has deps-from
        ;; and does not have resolved props
        {:gx/keys [resolved-props resolved-props-fn deps]}
        (if (and deps-from (not resolved-props))
          (get node deps-from)
          signal-def)
        ;; _ (validate-signal graph node-key signal-key graph-config)
        ;;
        ;; :deps-from is ignored if component have :props
        ;; props (if (and (not props) deps-from)
        ;;         (-> node deps-from :gx/props)
        ;;         props)
        dep-nodes (select-keys graph deps)
        dep-nodes-vals (system-value {:graph dep-nodes})
        failed-dep-node-keys (->> {:graph dep-nodes}
                                  (system-failure)
                                  (filter second)
                                  (map first))]
    (with-err-ctx {:node-contents (node-key initial-graph)}
      (cond
        (or ;; signal isn't defined for this state transition
         (not (contains? from-states node-state))
            ;; node is already in to-state
         (= node-state to-state))
        node

        (seq failed-dep-node-keys)
        (assoc node :gx/failure (gx.err/gx-err-data
                                 "Failure in dependencies"
                                 {:dep-node-keys failed-dep-node-keys}))
        (ifn? processor)
        (let [props-result (if (fn? resolved-props-fn)
                             (run-props-fn resolved-props-fn dep-nodes-vals)
                             (postwalk-evaluate dep-nodes-vals resolved-props))
              [error data] (if-let [validate-error (props-validate-error
                                                    props-schema props-result)]
                             [validate-error]
                             (run-processor
                              processor {:props props-result
                                         :value (:gx/value node)}))]
          (if error
            (assoc node :gx/failure error)
            (-> node
                (assoc :gx/value data)
                (assoc :gx/state to-state))))

        :else node))))

(defn merge-node-failure
  [gx-map node]
  (if-let [failure (:gx/failure node)]
    (update gx-map :failures conj failure)
    gx-map))

(defn signal
  ([gx-map signal-key]
   (signal gx-map signal-key #{}))
  ([gx-map signal-key priority-selector]
   (let [gx-map (normalize (dissoc gx-map :failures))
         [error sorted] (topo-sort gx-map signal-key priority-selector)
         gx-map (if error
                  (update gx-map :failures conj error)
                  gx-map)]
     (if (seq (:failures gx-map))
       (p/resolved gx-map)
       (p/loop [gxm gx-map
                sorted sorted]
         (cond
           (seq sorted)
           (p/let [node-key (first sorted)
                   node (with-err-ctx {:error-type :node-signal
                                       :signal-key signal-key
                                       :node-key node-key}
                          (node-signal gxm node-key signal-key))
                   next-gxm (assoc-in gxm [:graph node-key] node)]
             (p/recur (merge-node-failure next-gxm node) (rest sorted)))

           :else gxm))))))
