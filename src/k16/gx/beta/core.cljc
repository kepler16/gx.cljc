(ns k16.gx.beta.core
  (:refer-clojure :exclude [ref])
  #?(:cljs (:require-macros
            [k16.gx.beta.context :refer [merge-err-ctx with-ctx *ctx*]]))
  (:require [malli.core :as m]
            [malli.error :as me]
            [promesa.core :as p]
            [k16.gx.beta.nomalize :as gx.normalzie]
            [k16.gx.beta.impl :as impl]
            [k16.gx.beta.schema :as gx.schema]
            [k16.gx.beta.errors :as gx.err]
            #?(:clj [k16.gx.beta.context
                     :refer [merge-err-ctx with-ctx *ctx*]]))
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
  [{:keys [context graph]
    :or {context gx.normalzie/default-context}
    :as gx-map}]
  (let [config-issues (validate-context context)
        gx-map (assoc gx-map :context context)
        ;; remove previous normalization errors
        gx-map' (cond-> gx-map
                  (not (:initial-graph gx-map)) (assoc :initial-graph graph)
                  :always (dissoc :failures))]
    (try
      (cond
        config-issues (throw (ex-info "GX Context error" config-issues))
        :else (gx.normalzie/normalize-graph gx-map'))
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
   (merge-err-ctx {:error-type :deps-sort :signal-key signal-key}
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
    (merge-err-ctx {:error-type :props-validation}
      (gx.err/gx-err-data "Props validation error"
                          {:props-value props
                           :props-schema schema
                           :schema-error (me/humanize error)}))))

(defn- run-props-fn
  [props-fn arg-map]
  (try
    (props-fn arg-map)
    (catch #?(:clj Throwable :cljs :default) e
      (gx.err/throw-gx-err "Props function error"
                           {:ex-message (impl/error-message e)
                            :args arg-map}))))

(defn- run-processor
  [processor arg-map]
  (try
    [nil (processor arg-map)]
    (catch #?(:clj Throwable :cljs :default) e
      [(gx.err/gx-err-data "Signal processor error"
                           {:ex-message (impl/error-message e)
                            :ex (or (ex-data e) e)
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
  [{:keys [context graph initial-graph] :as gx-map} node-key signal-key]
  (let [evaluate-fn (-> context :normalize :form-evaluator)
        signal-config (-> context :signals signal-key)
        {:keys [deps-from from-states to-state]} signal-config
        node (get graph node-key)
        node (if (gx.normalzie/normalizable? node)
               (gx.normalzie/normalize-node node)
               node)
        node-state (:gx/state node)
        signal-def (get node signal-key)
        {:gx/keys [processor props-schema resolved-props]} signal-def
        ;; take deps from another signal of node if current signal has deps-from
        ;; and does not have resolved props
        {:gx/keys [resolved-props resolved-props-fn deps]}
        (if (and deps-from (not resolved-props))
          (get node deps-from)
          signal-def)
        dep-nodes (select-keys graph deps)
        dep-nodes-vals (system-value {:graph dep-nodes})
        failed-dep-node-keys (->> {:graph dep-nodes}
                                  (system-failure)
                                  (filter second)
                                  (map first))]
    (merge-err-ctx {:node-contents (node-key initial-graph)}
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
                             (evaluate-fn *ctx* resolved-props))
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

        :else (assoc node :gx/state to-state)))))

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
                   node (with-ctx {:ctx (system-value gxm)
                                   :err {:error-type :node-signal
                                         :signal-key signal-key
                                         :node-key node-key}}
                          (node-signal gxm node-key signal-key))
                   next-gxm (assoc-in gxm [:graph node-key] node)]
             (p/recur (merge-node-failure next-gxm node) (rest sorted)))

           :else gxm))))))

(defn signal-sync
  ([gx-map signal-key]
   (signal-sync gx-map signal-key #{}))
  ([gx-map signal-key priority-selector]
   (let [gx-map (normalize (dissoc gx-map :failures))
         [error sorted] (topo-sort gx-map signal-key priority-selector)
         gx-map (if error
                  (update gx-map :failures conj error)
                  gx-map)]
     (if (seq (:failures gx-map))
       gx-map
       (loop [gxm gx-map
              sorted sorted]
         (cond
           (seq sorted)
           (let [node-key (first sorted)
                 node (with-ctx {:ctx (system-value gxm)
                                 :err {:error-type :node-signal
                                       :signal-key signal-key
                                       :node-key node-key}}
                        (node-signal gxm node-key signal-key))
                 next-gxm (assoc-in gxm [:graph node-key] node)]
             (recur (merge-node-failure next-gxm node) (rest sorted)))

           :else gxm))))))

(comment
  (defn create-component
    [foo]
    (println foo)
    {:gx/start {:gx/processor (fn my-component [{:keys [props]}]
                                (assoc props :foo foo))}})

  (def component
    {:gx/start {:gx/processor (fn my-component [{:keys [props]}]
                                (assoc props :foo 1))}})

  (def graph {:a {:foo 1}
              :b '(get (gx/ref :a) :foo)
              :c {:gx/component 'k16.gx.beta.core/component
                  :gx/props {:b '(gx/ref :b)}}})
  (normalize {:graph graph})
  @(signal {:graph graph} :gx/start)

  (signal-sync {:graph graph} :gx/start)

  (def rr (form->runnable '(k16.gx.beta.core/create-component (gx/ref :a))))
  (run rr {:a {:foo 1}})

  (create-component {:foo 1})
  (-postwalk-evaluate {:a {:foo 1}} '(get (gx/ref :a) :foo))
  )