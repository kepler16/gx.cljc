(ns k16.gx.beta.core
  (:require [clojure.walk :as walk]
            [k16.gx.beta.impl :as impl]
            [k16.gx.beta.registry :as reg]
            [k16.gx.beta.schema :as gxs]))

(defonce INITIAL_STATE :uninitialized)

(defn resolve-symbol
  [sym]
  (when (symbol? sym)
    #?(:cljs (impl/namespace-symbol sym)
       :clj (var-get (requiring-resolve
                      (impl/namespace-symbol sym))))))

(defn simple-evaluate
  "A postwalk runtime signal processor evaluator, works most of the time.
  Doesn't support special symbols and macros, basically just function application.
  For cljs, consider compiled components or sci-evaluator, would require allowing
  for swappable evaluation stategies. Point to docs, to inform how to swap evaluator,
  or alternative ways to specify functions (that get compiled) that can be used."
  [props args]
  (walk/postwalk
   (fn [x]
     (cond
       (and (seq? x) (= 'gx/ref (first x)))
       (get props (second x))

       (and (seq? x) (fn? (first x)))
       (apply (first x) (rest x))

      ;; TODO special form support
      ;;  (and (seq? x) (special-symbol? (first x)))
      ;;  (eval x)

       :else x))
   args))

(defn gx-signal-wrapper
  [props w]
  {:props props
   :processor (fn signal-wrapper [{:keys [props _value]}]
                (cond
                  (fn? w) (w)

                  (and (seq? w) (fn? (first w)))
                  (simple-evaluate props w)

                  :else w))})

(defn throw-parse-error
  [msg node-definition token]
  (throw (ex-info (str msg " '" (pr-str token) "'")
                  {:form node-definition
                   :expr token})))

(defn signal-processor-form->fn
  [node-definition]
  (let [props* (atom {})
        node
        (->> node-definition
             (walk/postwalk
              (fn [token]
                (cond
                  (= 'gx/ref token) token

                  (special-symbol? token)
                  (throw-parse-error "Special forms are not supported"
                                     node-definition
                                     token)

                  #?@(:clj [(and (symbol? token) (= \. (first (str token))))
                            (fn [v & args]
                              (clojure.lang.Reflector/invokeInstanceMethod
                               v
                               (subs (str token) 1)
                               (into-array Object args)))]
                      :default [])

                  (resolve-symbol token) (resolve-symbol token)

                  (symbol? token)
                  (throw-parse-error "Unable to resolve symbol"
                                     node-definition
                                     token)

                  (and (seq? token) (= 'gx/ref (first token)))
                  (do (swap! props* assoc (second token) any?)
                      token)

                  :else token))))]
    (gx-signal-wrapper @props* node)))

(defn signal-processor-definition->signal-processor
  [node-definition]
  (cond
    (or (fn? node-definition)
        (map? node-definition))
    (gx-signal-wrapper {} node-definition)

    #?@(:clj
        [(symbol? node-definition)
         (gx-signal-wrapper {} (requiring-resolve node-definition))])

    (list? node-definition)
    (signal-processor-form->fn node-definition)

    :else (throw (ex-info
                  (str "Unsupported signal processor: "
                       (pr-str node-definition))
                  {:body node-definition}))))

(defn get-initial-signal
  [graph-config]
  (->> graph-config
       :signals
       (filter (fn [[_ body]]
                 ((:from-state body) INITIAL_STATE)))
       (map first)))

(defn normalize-node-def
  "Given a component definition, "
  [node-definition graph-config]
  (let [component (when (map? node-definition)
                        (some-> node-definition :gx/component resolve-symbol))
        new-def {:gx/state INITIAL_STATE
                 :gx/value nil}
        signals (set (keys (:signals graph-config)))
        ;; gather initial signals
        initial-signals (get-initial-signal graph-config)]
    (cond
      component (merge component new-def)
      ;; Already in normalised/semi-normalised form
      (and (map? node-definition)
           (some signals (keys node-definition)))
      (reduce
       (fn [nd signal]
         (if (get nd signal)
           (update nd signal signal-processor-definition->signal-processor)
           nd))
       (merge new-def node-definition)
       signals)

      :else
      (reduce
       (fn [nd signal]
         (assoc nd signal (signal-processor-definition->signal-processor
                           node-definition)))
       new-def
       initial-signals))))

;; TODO: write check for signal conflicts
;; - any state should have only one signal to transition from it
(defn normalize-graph
  "Given a graph definition and config, return a normalised form. Idempotent.
   This acts as the static analysis step of the graph.
   Returns tuple of error explanation (if any) and normamized graph."
  [graph-definition graph-config]
  (let [graph-issues (gxs/validate-graph graph-definition)
        config-issues (gxs/validate-graph-config graph-config)]
    (cond
      graph-issues (throw (ex-info "Graph definition error", graph-issues))
      config-issues (throw (ex-info "Graph config error" config-issues))
      :else (->> graph-definition
                 (map (fn [[k v]]
                        [k (normalize-node-def v graph-config)]))
                 (into {})))))

(defn graph-dependencies [graph signal-key]
  (->> graph
       (map (fn [[k node]]
              (let [deps (-> node signal-key :props keys)]
                [k (into #{} deps)])))
       (into {})))

(defn topo-sort [graph signal-key graph-config]
  (if-let [signal-config (get-in graph-config [:signals signal-key])]
    (let [deps-from (or (:deps-from signal-config)
                        signal-key)
          graph-deps (graph-dependencies graph deps-from)
          sorted-raw (impl/sccs graph-deps)]
    ;; handle dependency errors
      (let [errors (->> sorted-raw
                        (impl/dependency-errors graph-deps)
                        (map impl/human-render-dependency-error))]

        (when (seq errors)
          (throw (ex-info (str errors) {:errors errors}))))

      (let [topo-sorted (map first sorted-raw)]
        (if (= :topological (:order signal-config))
          topo-sorted
          (reverse topo-sorted))))
    (throw (ex-info (str "Unknown signal key '" signal-key "'")
                    {:signal-key signal-key}))))

(defn system-property
  [graph property-key]
  (->> graph
       (map (fn [[k node]]
              [k (get node property-key)]))
       (into {})))

(defn system-failure [graph]
  (system-property graph :gx/failure))

(defn system-value [graph]
  (system-property graph :gx/value))

(defn system-state [graph]
  (system-property graph :gx/state))

(defn- run-processor
  [processor arg-map]
  (try
    [nil (processor arg-map)]
    (catch #?(:clj Exception :cljs js/Error) e
      [e nil])))

(defn validate-signal
  [graph node-key signal-key graph-config]
  (let [{:keys [from-state to-state deps-from]}
        (-> graph-config :signals signal-key)
        node (get graph node-key)
        node-state (:gx/state node)
        {:keys [props processor]} (get node signal-key)]
    (assert (get from-state node-state)
            (str "Incompatible from-state '" node-state
                 "', expected one of '" from-state "'"))
    {:to-state to-state
     :deps-from deps-from
     :props props
     :processor processor
     :node node}))

(defn node-signal
  "Trigger a signal through a node, assumes dependencies have been run.
   If node does not support signal then do nothing"
  [graph node-key signal-key graph-config]
  (let [{:keys [deps-from to-state node props processor]}
        (validate-signal graph node-key signal-key graph-config)
        ;; :deps-from is ignored if component have :props
        props (if (and (not props) deps-from)
                (-> node deps-from :props)
                props)
        ;; TODO: add props validation using malli schema
        dep-nodes (select-keys graph (keys props))
        props-falures (->> dep-nodes
                           (system-failure)
                           (filter :gx/failure))]
    (cond
      (seq props-falures)
      (assoc node :gx/failure {:deps-failures props-falures})

      (ifn? processor)
      (let [[error data] (run-processor
                          processor {:props (system-value dep-nodes)
                                     :value (:gx/value node)})]
        (if error
          (assoc node :gx/failure error)
          (-> node
              (assoc :gx/value data)
              (assoc :gx/state to-state))))

      :else (assoc node :gx/state to-state))))

(defn signal [graph signal-key graph-config]
  (let [sorted (topo-sort graph signal-key graph-config)]
    (reduce
     (fn [graph node-key]
       (let [node (node-signal graph node-key signal-key graph-config)]
         (assoc graph node-key node)))
     graph
     sorted)))
