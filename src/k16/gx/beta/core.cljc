(ns k16.gx.beta.core
  (:require [k16.gx.beta.impl :as impl]
            [k16.gx.beta.schema :as gxs]
            [clojure.walk :as walk]))

(defn evaluate
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

(comment
  (impl/namespace-symbol 'inc)

  (let [graph-config
        {:signals {:transit/start {:order :topological
                                   :from-state #{:stopped :uninitialized}
                                   :to-state :started}
                   :transit/stop {:order :reverse-topological
                                  :from-state #{:started}
                                  :to-state :stopped
                                  :deps-from :gx/start}}}
        config {:a {:nested-a 1}
                :z '(inc (get (gx/ref :a) :nested-a))}
        norm (normalize-graph config graph-config)
        started (signal norm :transit/start graph-config)]
    (system-value started))
  )

(defn gx-signal-wrapper
  [props w]
  {:props props
   :processor (fn signal-wrapper [{:keys [props _value]}]
                (cond
                  (fn? w) (w)

                  (and (seq? w) (fn? (first w)))
                  (evaluate props w)

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
                (try
                  (cond
                    (= 'gx/ref token) token

                    (special-symbol? token)
                    (throw-parse-error "Special forms are not supported"
                                       node-definition
                                       token)

                    #?@(:cljs [(and (symbol? token)
                                    (impl/namespace-symbol token))
                               (impl/namespace-symbol token)]
                        :clj [(and (symbol? token)
                                   (requiring-resolve
                                    (impl/namespace-symbol token)))
                              (var-get
                               (requiring-resolve
                                (impl/namespace-symbol token)))])

                    #?@(:clj [(and (symbol? token) (= \. (first (str token))))
                              (fn [v & args]
                                (clojure.lang.Reflector/invokeInstanceMethod
                                 v
                                 (subs (str token) 1)
                                 (into-array Object args)))]
                        :default [])

                    (symbol? token)
                    (throw-parse-error "Unable to resolve symbol"
                                       node-definition
                                       token)

                    (and (seq? token) (= 'gx/ref (first token)))
                    (do (swap! props* assoc (second token) any?)
                        token)

                    :else token)
                  (catch #?(:clj Exception :cljs js/Error) e
                    (throw (ex-info (str "Unable to evaluate form '"
                                         (pr-str token) "'")
                                    {:form token} e)))))))]
    ;; (println node)
    (gx-signal-wrapper @props* node)))

;; (defn normalize-component-def
;;   [component]
;;   (let [{:keys [component simple?]} (schema/validate-component! component)]
;;     (if simple?
;;       )))

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

;; TODO Update to work with new structure
(defn get-initial-signals
  [graph-config]
  (->> graph-config
       :signals
       (filter (fn [[_ body]]
                 ((:from-state body) :uninitialized)))
       (map first)))

;; TODO Update to work with new structure
(defn normalize-graph-def
  "Given a component definition, "
  [node-definition graph-config]
  (let [component (when (map? node-definition)
                    (some-> node-definition :gx/component))
        new-def {:gx/vars {:normalised true}
                 :gx/state :uninitialized
                 :gx/value nil}
        signals (set (keys (:signals graph-config)))
        ;; gather initial signals, which handle :uninitialized state
        initial-signals (get-initial-signals graph-config)]
    (cond
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

;; TODO Update to work with new structure
(defn normalize-graph
  "Given a graph definition, return a normalised form. idempotent.
  This acts as the static analysis step of the graph"
  [graph-definition graph-config]
  (gxs/conform-graph-config! graph-config)
  (->> graph-definition
       (map (fn [[k v]]
              [k (normalize-graph-def v graph-config)]))
       (into {})))

(defn graph-dependencies [graph signal-key]
  (->> graph
       (map (fn [[k node]]
              (let [deps (-> node signal-key :props keys)]
                [k (into #{} deps)])))
       (into {})))

(defn topo-sort [graph signal-key graph-config]
  (let [signal-config (get-in graph-config [:signals signal-key])
        deps-from (or (:deps-from signal-config)
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
        (reverse topo-sorted)))))

(defn system-property
  [graph property-key]
  (->> graph
       (map (fn [[k node]]
              [k (get node property-key)]))
       (into {})))

(defn system-value [graph]
  (system-property graph :gx/value))

(defn system-state [graph]
  (system-property graph :gx/state))

(defn- run-processor
  [processor arg-map]
  (try
    {:success? true
     :data (processor arg-map)}
    (catch #?(:clj Exception :cljs js/Error) e
      {:success? false
       :data e})))

(defn validate-signal!
  [graph node-key signal-key graph-config]
  (let [signal-config (-> graph-config :signals signal-key)
        from-state (:from-state signal-config)
        node (get graph node-key)
        node-state (:gx/state node)]
    (assert (get from-state node-state)
            (str "Incompatible from-state " node-state
                 ", expecting one of " from-state))
    {:signal-config signal-config
     :node node}))

(defn node-signal
  "Trigger a signal through a node, assumes dependencies have been run"
  [graph node-key signal-key graph-config]

  (let [{:keys [signal-config node]}
        (validate-signal! graph node-key signal-key graph-config)
        {:keys [props processor]} (get node signal-key)
        {:keys [deps-from to-state]} signal-config
        props (if (and (not props) deps-from)
                (-> node deps-from :props)
                props)
        dep-components (select-keys graph (keys props))
        props-map (system-value dep-components)]
    (if processor
      (let [{:keys [success? data]}
            (run-processor
             processor {:props props-map :value (:gx/value node)})]
        (if success?
          (-> node
              (assoc :gx/value data)
              (assoc :gx/state to-state))
          (assoc node :gx/failure data)))
      node)))

(defn signal [graph signal-key graph-config]
  (let [sorted (topo-sort graph signal-key graph-config)]
    (reduce
     (fn [graph node-key]
       (let [node (node-signal graph node-key signal-key graph-config)]
         (assoc graph node-key node)))
     graph
     sorted)))
