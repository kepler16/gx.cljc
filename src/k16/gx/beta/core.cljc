(ns k16.gx.beta.core
  (:require [clojure.walk :as walk]
            [k16.gx.beta.impl :as impl]
            [k16.gx.beta.registry :as reg]
            [k16.gx.beta.schema :as gxs]
            [k16.gx.alpha :as gx]))

(defonce INITIAL_STATE :uninitialized)

(def default-graph-config
  {:signals {:gx/start {:order :topological
                        :from-state #{:stopped INITIAL_STATE}
                        :to-state :started}
             :gx/stop {:order :reverse-topological
                       :from-state #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})

(defn resolve-symbol
  [sym]
  (when (symbol? sym)
    #?(:cljs (impl/namespace-symbol sym)
       :clj (var-get (requiring-resolve
                      (impl/namespace-symbol sym))))))

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

       (and (seq? x) (fn? (first x)))
       (apply (first x) (rest x))

       :else x))
   form))

;; (defn gx-signal-wrapper
;;   [props w]
;;   {:props props
;;    :processor (fn signal-wrapper [{:keys [props _value]}]
;;                 (cond
;;                   (fn? w) (w)

;;                   (and (seq? w) (fn? (first w)))
;;                   (postwalk-evaluate props w)

;;                   :else w))})

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
                  (some (into #{} [:gx/props :gx/props-fn :gx/processor :gx/deps :gx/resolved-props]) (keys signal-definition)))
        with-pushed-down-form (if def?
                                signal-definition
                                (let [{:keys [form env]} (form->runnable signal-definition)]
                                  {:gx/processor (fn auto-signal-processor [{:keys [props]}]
                                                   (postwalk-evaluate props form))
                                   :gx/deps env
                                   :gx/resolved-props (->> env
                                                           (map (fn [dep]
                                                                  [dep (list 'gx/ref dep)]))
                                                           (into {}))}))
        with-resolved-props (if (:gx/resolved-props with-pushed-down-form)
                              with-pushed-down-form
                              (let [{:keys [form env]} (form->runnable (:gx/props with-pushed-down-form))]
                                (merge with-pushed-down-form
                                       {:gx/resolved-props form
                                        :gx/deps env})))]
    with-resolved-props))


(defn normalize-node-def
  "Given a component definition, "
  [graph-config node-definition]
  (let [;; set of signals defined in the graph
        signals (set (keys (:signals graph-config)))
        ;; is this map a map based def, or a runnable form
        def? (and (map? node-definition)
                  (some (into #{} (concat signals [:gx/component])) (keys node-definition)))
        with-pushed-down-form (if def?
                                node-definition
                                {:gx/start node-definition})
        component (some-> with-pushed-down-form :gx/component resolve-symbol)
        ;; merge in component
        with-component (impl/deep-merge
                        component (dissoc with-pushed-down-form :gx/component))
        normalised-def (merge
                        with-component
                        {:gx/state INITIAL_STATE
                         :gx/value nil})

        signal-defs (select-keys normalised-def signals)
        normalised-signal-defs (->> signal-defs
                                    (map (fn [[k v]]
                                           [k (normalize-signal-def graph-config v k)]))
                                    (into {}))]
    (merge normalised-def normalised-signal-defs)))


(comment
  (def graph-config-a {:signals {:gx/start {:order :topological}}})

  (normalize-signal-def graph-config-a '(get {:s (gx/ref :a)} :s) :a)

  (normalize-node-def  graph-config-a '(get {:s (gx/ref :a)} :s))
  (normalize-node-def  graph-config-a {:gx/start '(get {:s (gx/ref :a)} :s)})

  (normalize-node-def  graph-config-a {:gx/start {:gx/processor (fn [self] self)
                                                  :gx/props {:a '(get {:s (gx/ref :a)} :s)}}}))

;; TODO: write check for signal conflicts
;; - any state should have only one signal to transition from it
(defn normalize-graph
  "Given a graph definition and config, return a normalised form. Idempotent.
   This acts as the static analysis step of the graph.
   Returns tuple of error explanation (if any) and normamized graph."
  [graph-config graph-definition]
  (let [graph-issues (gxs/validate-graph graph-definition)
        config-issues (gxs/validate-graph-config graph-config)]
    (cond
      ;; graph-issues (throw (ex-info "Graph definition error", graph-issues))
      ;; config-issues (throw (ex-info "Graph config error" config-issues))
      :else (->> graph-definition
                 (map (fn [[k v]]
                        [k (normalize-node-def graph-config v)]))
                 (into {})))))

(defn graph-dependencies [graph signal-key]
  (->> graph
       (map (fn [[k node]]
              (let [deps (-> node signal-key :deps)]
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
  (let [
        {:keys [from-state to-state deps-from]}
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
  (let [
        signal-config (-> graph-config :signals signal-key)
        {:keys [deps-from to-state]} signal-config
        node (get graph node-key)
        node-state (:gx/state node)
        signal-def (get node signal-key)
        {:gx/keys [props processor resolved-props props-fn]} signal-def
        ;; _ (validate-signal graph node-key signal-key graph-config)
        ;;
        ;; :deps-from is ignored if component have :props
        props (if (and (not props) deps-from)
                (-> node deps-from :gx/props)
                props)
        ;; TODO: add props validation using malli schema
        dep-nodes (system-value (select-keys graph (:gx/deps signal-def)))]
        ;; props-falures (->> dep-nodes
        ;;                    (system-failure)
        ;;                    (filter :gx/failure))]
        ;;
    (println node-key)
    (def node node)
    (def node-state node-state)
    (def props props)
    (def dep-nodes dep-nodes)
    (def dep-nodes dep-nodes)
    (cond
          ;; (seq props-falures)
          ;; (assoc node :gx/failure {:deps-failures props-falures})

          (ifn? processor)
          (let [props-result (postwalk-evaluate dep-nodes (:gx/resolved-props signal-def))

                [error data] (run-processor
                              processor {:props props-result
                                         :value (:gx/value node)})]
            (if error
              (assoc node :gx/failure error)
              (-> node
                  (assoc :gx/value data)
                  (assoc :gx/state to-state)))))))


(defn signal [graph signal-key graph-config]
  (let [normalised-graph (normalize-graph graph-config graph)
        sorted (topo-sort normalised-graph signal-key graph-config)]
    (def sorted sorted)
    (reduce
     (fn [graph node-key]
       (let [node (node-signal graph node-key signal-key graph-config)]
         (assoc graph node-key node)))
     normalised-graph
     sorted)))


(comment
  (def my-component
    {:gx/start {:gx/processor (fn [{:keys [props]}]
                                (println "starting" (:w props))
                                :d-started)
                :gx/props-schema [:map
                                  [:w int?]
                                  [:port int?]]}})
  (def g
    {:a 2
     :b '(inc (gx/ref :a))})
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

  (def normalised
    (normalize-graph default-graph-config g))

  (signal g :gx/start default-graph-config)

  (:c normalised))
