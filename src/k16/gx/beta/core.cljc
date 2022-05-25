(ns k16.gx.beta.core
  (:require [clojure.walk :as walk]
            [k16.gx.beta.impl :as impl]
            [promesa.core :as p]
            [malli.core :as m]
            [malli.error :as me]))

(defonce INITIAL_STATE :uninitialized)

(def default-graph-config
  {:signals {:gx/start {:order :topological
                        :from-states #{:stopped INITIAL_STATE}
                        :to-state :started}
             :gx/stop {:order :reverse-topological
                       :from-states #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})

(defn resolve-symbol
  [sym]
  (when (symbol? sym)
    #?(:cljs (impl/namespace-symbol sym)
       :clj (some-> sym
                    (impl/namespace-symbol)
                    (requiring-resolve)
                    (var-get)))))

(defn postwalk-evaluate
  "A postwalk runtime signal processor evaluator, works most of the time.
  Doesn't support special symbols and macros, basically just function application.
  For cljs, consider compiled components or sci-evaluator, would require allowing
  for swappable evaluation stategies. Point to docs, to inform how to swap evaluator,
  or alternative ways to specify functions (that get compiled) that can be used."
  [env form]
  (walk/postwalk
   (fn [x]
     (cond
       (and (seq? x) (= 'gx/ref (first x)))
       (get env (second x))

       (and (seq? x) (ifn? (first x)))
       (apply (first x) (rest x))

       :else x))
   form))

(defn throw-parse-error
  [msg node-definition token]
  (throw (ex-info (str msg " '" (pr-str token) "'")
                  {:form node-definition
                   :expr token})))

(defn form->runnable [form]
  (let [props* (atom #{})
        resolved-form
        (->> form
             (walk/postwalk
              (fn [sub-form]
                (cond
                  (= 'gx/ref sub-form) sub-form

                  (special-symbol? sub-form)
                  (throw-parse-error "Special forms are not supported"
                                     form
                                     sub-form)

                  (resolve-symbol sub-form) (resolve-symbol sub-form)

                  (symbol? sub-form)
                  (throw-parse-error "Unable to resolve symbol"
                                     form
                                     sub-form)

                  (and (seq? sub-form) (= 'gx/ref (first sub-form)))
                  (do (swap! props* conj (second sub-form))
                      sub-form)

                  :else sub-form))))]
    {:env @props*
     :form resolved-form}))

(defn normalize-signal-def [graph-config signal-definition signal-key]
  (let [signal-config (get-in graph-config [:signals signal-key])
        ;; is this map a map based def, or a runnable form
        def? (and (map? signal-definition)
                  (some #{:gx/props :gx/props-fn
                          :gx/processor :gx/deps
                          :gx/resolved-props}
                        (keys signal-definition)))
        with-pushed-down-form
        (if def?
          signal-definition
          (let [{:keys [form env]} (form->runnable signal-definition)]
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

(defn get-initial-signal
  "Finds first signal, which is launched on normalized graph with
   :uninitialized nodes. Used on static nodes."
  [graph-config]
  (->> graph-config
       :signals
       (filter (fn [[_ body]]
                 ((:from-states body) INITIAL_STATE)))
       (map first)
       first))

(defn normalize-node-def
  "Given a component definition, "
  [node-definition graph-config]
  (if (:gx/normalized? node-definition)
    node-definition
    (let [;; set of signals defined in the graph
          signals (set (keys (:signals graph-config)))
          ;; is this map a map based def, or a runnable form
          def? (and (map? node-definition)
                    (some (into #{} (concat signals [:gx/component]))
                          (keys node-definition)))
          initial-signal (get-initial-signal graph-config)
          with-pushed-down-form (if def?
                                  node-definition
                                  {initial-signal node-definition})
          component (some-> with-pushed-down-form :gx/component resolve-symbol)
          ;; merge in component
          with-component (impl/deep-merge
                          component (dissoc with-pushed-down-form :gx/component))
          normalized-def (merge
                          with-component
                          {:gx/state INITIAL_STATE
                           :gx/value nil})
          signal-defs (select-keys normalized-def signals)
          normalised-signal-defs
          (->> signal-defs
               (map (fn [[k v]]
                      [k (normalize-signal-def graph-config v k)]))
               (into {}))]
      (merge normalized-def
             normalised-signal-defs
           ;; Useful information, but lets consider semantics before
           ;; using the value to determine behaviour
             {:gx/type (if def? :component :static)
              :gx/normalized? true}))))

;; - any state should have only one signal to transition from it
(defn normalize-graph
  "Given a graph definition and config, return a normalised form. Idempotent.
   This acts as the static analysis step of the graph.
   Returns tuple of error explanation (if any) and normamized graph."
  [graph-config graph-definition]
  (let []
        ;; graph-issues (gxs/validate-graph graph-definition)
        ;; config-issues (gxs/validate-graph-config graph-config)]
    (cond
      ;; graph-issues (throw (ex-info "Graph definition error", graph-issues))
      ;; config-issues (throw (ex-info "Graph config error" config-issues))
      :else (->> graph-definition
                 (map (fn [[k v]]
                        [k (normalize-node-def v graph-config)]))
                 (into {})))))

(defn graph-dependencies [graph signal-key]
  (->> graph
       (map (fn [[k node]]
              (let [deps (-> node signal-key :gx/deps)]
                [k (into #{} deps)])))
       (into {})))

(defn topo-sort [graph-config graph signal-key]
  (if-let [signal-config (get-in graph-config [:signals signal-key])]
    (let [deps-from (or (:deps-from signal-config)
                        signal-key)
          graph-deps (graph-dependencies graph deps-from)
          sorted-raw (impl/sccs graph-deps)]
      (when-let [errors (->> sorted-raw
                             (impl/dependency-errors graph-deps)
                             (map impl/human-render-dependency-error)
                             (seq))]
        (throw (ex-info (str errors) {:errors errors})))

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

(defn validate-props
  [schema props]
  (when-let [error (and schema (m/explain schema props))]
    (me/humanize error)))

(defn- run-processor
  [processor arg-map]
  (try
    [nil (processor arg-map)]
    (catch #?(:clj Exception :cljs js/Error) e
      [e nil])))

(defn validate-signal
  [graph-config graph node-key signal-key]
  (let [{:keys [from-states to-state deps-from]}
        (-> graph-config :signals signal-key)
        node (get graph node-key)
        node-state (:gx/state node)
        {:keys [props processor]} (get node signal-key)]
    (assert (get from-states node-state)
            (str "Incompatible from-states '" node-state
                 "', expected one of '" from-states "'"))
    {:to-state to-state
     :deps-from deps-from
     :props props
     :processor processor
     :node node}))

(defn node-signal
  "Trigger a signal through a node, assumes dependencies have been run.
   Subsequent signal calls is supported, but it should be handled in it's
   implementation. For example, http server component checks that it
   already started and does nothing to prevent port taken error or it
   can restart itself by taking recalculated properties from deps.
   Static nodes just recalculates its values.
   If node does not support signal then do nothing."
  [graph-config graph node-key signal-key]
  (let [signal-config (-> graph-config :signals signal-key)
        {:keys [_deps-from from-states to-state]} signal-config
        node (get graph node-key)
        node-state (:gx/state node)
        signal-def (get node signal-key)
        {:gx/keys [processor resolved-props
                   resolved-props-fn deps props-schema]} signal-def
        ;; _ (validate-signal graph node-key signal-key graph-config)
        ;;
        ;; :deps-from is ignored if component have :props
        ;; props (if (and (not props) deps-from)
        ;;         (-> node deps-from :gx/props)
        ;;         props)
        ;; TODO: add props validation using malli schema
        dep-nodes (system-value (select-keys graph deps))]
        ;; props-falures (->> dep-nodes
        ;;                    (system-failure)
        ;;                    (filter :gx/failure))]
    (cond
      ;; Non subsequent signal and node-state != from-states
      ;; ignore signal, return node
      (and (not (from-states node-state))
           (not= node-state to-state)) node
          ;; (seq props-falures)
          ;; (assoc node :gx/failure {:deps-failures props-falures})
      ;; TODO Check that we are actually turning symbols into resolved functions
      ;; in the normalisation step
      (ifn? processor)
      ;; either use resolved-props, or call props-fn and pass in (system-value graph deps), result
      ;; of props-fn, should be validated against props-schema
      (let [props-result (if (fn? resolved-props-fn)
                           (resolved-props-fn dep-nodes)
                           (postwalk-evaluate dep-nodes resolved-props))
            [error data] (if-let [e (validate-props props-schema props-result)]
                           [{:props-value props-result
                             :malli-schema props-schema
                             :malli-error e}]
                           (run-processor
                            processor {:props props-result
                                       :value (:gx/value node)}))]
        (if error
          (assoc node :gx/failure error)
          (-> node
              (assoc :gx/value data)
              (assoc :gx/state to-state))))

      :else node)))

(defn signal [graph-config graph signal-key]
  (let [normalised-graph (normalize-graph graph-config graph)
        sorted (topo-sort graph-config normalised-graph signal-key)]
    (p/loop [graph normalised-graph
             sorted sorted]
      (if (seq sorted)
        (p/let [node-key (first sorted)
                node (node-signal graph-config graph node-key signal-key)
                next-graph (assoc graph node-key node)]
          (p/recur next-graph (rest sorted)))
        graph))))


(comment

  (require '[k16.gx.beta.system :as system])

  (system/register
   :dev
   (fn []
     {:graph-config gx/default-graph-config
      :graph
      {:port '(inc 1234)
       :portttt '(inc (gx/ref :port))
       :pop '(+ (gx/ref :port) (gx/ref :portttt))
       :x {:gx/start 4
           :gx/stop {:gx/processor (fn [{:keys [value]}]
                                     (println "stopping with value " value)
                                     :x-stopped)}}}}))

  (system/signal :dev :gx/start)

  (system/signal :dev :gx/stop)



  (postwalk-evaluate {} {:nested-a 1})
  (do
    (def ?TestCoponentProps
      [:map [:config [:map [:nested-a pos-int?]]]])

    (def my-component
      {:gx/start {:gx/props {:config '(gx/ref :c)}
                  :gx/props-schema ?TestCoponentProps
                  :gx/processor
                  (fn [{:keys [props _value]}]
                    (when-let [a (:config props)]
                      (atom
                       (assoc a :nested-a-x2 (* 2 (:nested-a a))))))}
       :gx/stop {:gx/processor (fn [{:keys [_props value]}]
                                 nil)}})

    (def config {:c {:nested-a 1}
                 :z '(+ 10 (get (gx/ref :c) :nested-a))
                 :comp {:gx/start {:gx/props
                                   {:config
                                    {:nested-a '(gx/ref :z)}}}
                        :gx/component 'k16.gx.beta.core/my-component}})
    (def norm (normalize-graph config default-graph-config))
    ;; norm
    ;; (select-keys norm #{:c})
    (def started (signal norm :gx/start default-graph-config))

    (def stopped (signal started :gx/stop default-graph-config))
    [;;  norm
     started
    ;;  stopped
     ]
    ;; nil
    ;; started
    )

     ;; :c {:gx/start '(inc (gx/ref :b))
     ;;     :gx/stop '(println "stopping")}
     ;; :d {:gx/start {:gx/processor (fn [{:keys [props]}]
     ;;                                (println "starting")
     ;;                                :d-started)
     ;;                :gx/props {:x '(gx/ref :c)}}}

     ;; :conf {:port 345}

     ;; :e {:gx/component my-component
     ;;     :gx/props {:port 3
     ;;                :w '(get (gx/ref :conf) :a)}}})

  (normalize-graph default-graph-config g)


  (signal g :gx/start default-graph-config)

  (signal g :gx/stop default-graph-config)


  (def normalised
    (normalize-graph default-graph-config g))


  (signal g :gx/start default-graph-config)


  (:c normalised))
