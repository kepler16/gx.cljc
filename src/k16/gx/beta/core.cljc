(ns k16.gx.beta.core
  (:require [k16.gx.beta.impl :as impl]
            [clojure.walk :as walk]
            [hyperfiddle.rcf :refer [tests]]))

(defn- ^:dynamic get-env [_])

(defn gx-signal-wrapper
  [env w]
  {:env env
   :processor (fn [{:keys [env _state]}]
                (if (and (seq? w) (var? (first w)))
                  (binding [get-env (fn [k] (get env k))]
                    (eval w))
                  w))})

(defn signal-processor-form->fn
  [node-definition]
  (let [env (atom {})
        node
        (->> node-definition
             (walk/postwalk
              (fn [x]
                (try
                  (cond
                    (= 'gx/ref x) x

                    #?@(:cljs []
                        :default [(and (symbol? x)
                                       (requiring-resolve
                                        (impl/namespace-symbol x)))
                                  (requiring-resolve
                                   (impl/namespace-symbol x))])

                    #?@(:clj [(and (symbol? x) (= \. (first (str x))))
                              #(clojure.lang.Reflector/invokeInstanceMethod
                                %1
                                (subs (str x) 1)
                                (into-array Object %&))]
                        :default [])

                    (symbol? x)
                    (throw (ex-info (str "Unable to resolve symbol " x)
                                    {:form node-definition
                                     :expr x}))



                    (and (seq? x) (= 'gx/ref (first x)))
                    (do (swap! env assoc (second x) any?)
                        (conj (rest x) 'get-env))

                    :else x)
                  (catch #?(:clj Exception :cljs js/Error) e
                    (throw (ex-info (str "Unable to evaluate form " (pr-str x))
                                    {:form x} e)))))))]
    (gx-signal-wrapper @env node)))
;; TODO Update to work with new structure
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
                  (str "Unsupported signal processor:\n" node-definition)
                  {:body node-definition}))))

;; TODO Update to work with new structure
(defn normalize-graph-def
  "Given a component definition, "
  [node-definition]
  (let [component (when (map? node-definition)
                    (some-> node-definition :gx/component))
        new-def {:gx/vars {:normalised true}
                 :gx/status :uninitialized
                 :gx/state nil}]
    (cond
      ;; Already in normalised/semi-normalised form
      (and (map? node-definition)
           (some #{:gx/start :gx/stop} (keys node-definition)))
      (-> new-def
          (merge node-definition)
          (update :gx/start
                  signal-processor-definition->signal-processor)
          (update :gx/stop
                  signal-processor-definition->signal-processor))

      :else
      (merge new-def
             {:gx/start (signal-processor-definition->signal-processor
                         node-definition)}))))

;; TODO Update to work with new structure
(defn normalize-graph
  "Given a graph definition, return a normalised form. idempotent.
  This acts as the static analysis step of the graph"
  [graph-definition graph-config]
  (->> graph-definition
       (map (fn [[k v]]
              [k (normalize-graph-def v)]))
       (into {})))

;; TODO Update
(def ?SignalProcessor
  [:map
   [:processor fn?]
   [:env [:map any?]]])

;; TODO Update
(def ?NormalizedComponentDefinition
  [:map
   [:gx/vars [:map]]
   [:gx/status :keyword]
   [:gx/state any?]
   [:gx/start {:optional true} ?SignalProcessor]
   [:gx/stop {:optional true} ?SignalProcessor]])

(defn graph-dependencies [graph signal-key]
  (->> graph
       (map (fn [[k node]]
              (let [deps (-> node signal-key :env keys)]
                [k (into #{} deps)])))
       (into {})))

(defn topo-sort [graph signal-key graph-config]
  (let [signal-config (get-in graph-config [:signals signal-key])
        env-from (or (:env-from signal-config)
                     signal-key)
        graph-deps (graph-dependencies graph env-from)
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

(defn system-state [graph]
  (->> graph
       (map (fn [[k {:gx/keys [state]}]]
              [k state]))
       (into {})))

(defn- run-processor
  [processor argmap]
  (try
    {:success? true
     :data (processor argmap)}
    (catch Exception e
      {:success? false
       :data e})))

(defn node-signal
  "Trigger a signal through a node, assumes dependencies have been run"
  [graph node-key signal-key graph-config]
  (let [signal-config (get-in graph-config [:signals signal-key])
        node (get graph node-key)
        node-state (get node :gx/state)
        signal-impl (get node signal-key)
        {:keys [env processor]} signal-impl
        {:keys [_order env-from success-status failure-status]
         :or {success-status :started
              failure-status :error}} signal-config
        env' (if (and (not env) env-from)
               (-> node-key :env-from :env)
               env)
        dep-components (select-keys graph (keys env'))
        env-map' (system-state dep-components)]
    (if processor
      (if-let [{:keys [success? data]}
               (run-processor
                processor {:env env-map' :state node-state})]
        (-> node
            (assoc :gx/state data)
            (assoc :gx/status (if success? success-status failure-status)))
        node)
      node)))

(defn signal [graph signal-key graph-config]
  (let [sorted (topo-sort graph signal-key graph-config)]
    (reduce
     (fn [graph node-key]
       (let [node (node-signal graph node-key signal-key graph-config)]
         (assoc graph node-key node)))
     graph
     sorted)))

;; Inline RCF tests, runs on evely ns eval.
;; Evaluates to nil if not enabled (see dev/user.clj)
(tests
 (def graph-config {:signals {:gx/start {:order :topological}
                              :gx/stop {:order :reverse-topological
                                        :success-status :stopped
                                        :env-from :gx/start}}})
 (def graph {:a {:nested-a 1}
             :z '(get (gx/ref :a) :nested-a)
             :y '(println "starting")
             :b {:gx/start '(+ (gx/ref :z) 2)
                 :gx/stop '(println "stopping")}})
 (def normalized (normalize-graph graph graph-config))

 "should normalize graph"
 (set (keys normalized)) := #{:a :z :y :b}

 "check graph deps for gx/start"
 (graph-dependencies normalized :gx/start)
 := {:a #{}, :z #{:a}, :y #{}, :b #{:z}}

 "check graph deps for gx/stop"
 (graph-dependencies normalized :gx/stop)
 := {:a #{}, :z #{}, :y #{}, :b #{}}

 "check topo sort for gx/start"
 (topo-sort normalized :gx/start graph-config)
 := '(:a :z :y :b)

 "check topo sort for gx/stop"
 (topo-sort normalized :gx/stop graph-config)
 := '(:b :y :z :a)

 "all components should be 'uninitialized'"
 (->> normalized
      (vals)
      (map :gx/status)
      (every? #(= :uninitialized %))) := true

 (def started (signal normalized :gx/start graph-config))

 "check data correctness of started nodes"
 (-> started :a :gx/status) := :started
 (-> started :a :gx/state) := {:nested-a 1}
 (-> started :b :gx/status) := :started
 (-> started :b :gx/state) := 3
 (-> started :b :gx/start :env) := {:z any?}
 (-> started :z :gx/status) := :started
 (-> started :z :gx/state) := 1
 (-> started :z :gx/start :env) := {:a any?}
 (-> started :y :gx/status) := :started
 (-> started :y :gx/state) := nil

 (def stopped (signal started :gx/stop graph-config))
 "check data correctness of stopped nodes"
 (-> stopped :b :gx/status) := :stopped
 (-> stopped :b :gx/state) := nil

 "nodes without gx/stop should be unchanged"
 ;; TODO: find out whether nodes should have default stop routine
 (-> stopped :a :gx/status) := :started
 (-> stopped :a :gx/state) := {:nested-a 1}
 (-> stopped :b :gx/start :env) := {:z any?}
 (-> stopped :z :gx/status) := :started
 (-> stopped :z :gx/state) := 1
 (-> stopped :z :gx/start :env) := {:a any?}
 (-> stopped :y :gx/status) := :started
 (-> stopped :y :gx/state) := nil

 nil)
