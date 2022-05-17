(ns k16.gx.beta.core
  (:require [k16.gx.beta.impl :as impl]
            [clojure.walk :as walk]))

;; TODO Update to work with new structure
(defn signal-processor-form->fn [body]
  (->> body
       (walk/postwalk
        (fn [x]
          (try
            (cond
              #?@(:cljs []
                  :default [(and (symbol? x)
                                 (requiring-resolve (impl/namespace-symbol x)))
                            (requiring-resolve (impl/namespace-symbol x))])

              #?@(:clj [(and (symbol? x) (= \. (first (str x))))
                        #(clojure.lang.Reflector/invokeInstanceMethod
                          %1
                          (subs (str x) 1)
                          (into-array Object %&))]
                  :default [])

              (symbol? x)
              (throw (ex-info (str "Unable to resolve symbol " x)
                              {:form body
                               :expr x}))

              (seq? x)
              (apply (first x) (rest x))

              :else x)
            (catch #?(:clj Exception :cljs js/Error) e
              (throw (ex-info (str "Unable to evaluate form " (pr-str x)) {:form x} e))))))))

;; TODO Update to work with new structure
(defn signal-processor-definition->signal-processor [body]
  (cond
    (fn? body)
    body

    #?@(:clj
        [(symbol? body)
         (requiring-resolve body)])

    (list? body)
    (signal-processor-form->fn body)

    :else (throw (ex-info (str "Unsupported signal processor:\n" body) {:body body}))))

;; TODO Update to work with new structure
(defn normalize-graph-def
  "Given a component definition, "
  [graph-config definition]
  (let [component (when (map? definition)
                    (some-> definition :gx/component))
        _ (tap> component)
        new-def {:gx/vars {:normalised true}
                 :gx/status :uninitialized
                 :gx/state nil}]
    (cond
      ;; Already in normalised/semi-normalised form
      (and (map? definition)
           (some #{:gx/start :gx/stop} (keys definition)))
      (update (merge new-def definition)
              :gx/start signal-processor-definition->signal-processor
              :gx/stop signal-processor-definition->signal-processor)

      :else
      (merge new-def
             {:gx/start (signal-processor-definition->signal-processor definition)}))))

;; TODO Update to work with new structure
(defn normalize-graph
  "Given a graph definition, return a normalised form. idempotent.
  This acts as the static analysis step of the graph"
  [graph-config graph-definition]
  (->> graph-definition
       (map (fn [[k v]]
              [k (normalize-graph-def graph-config v)]))
       (into {})))

;; TODO Update
(def ?SignalProcessor
  [:map
   [:processor fn?]
   [:deps
    [:vector :keyword]]])

;; TODO Update
(def ?NormalizedComponentDefinition
  [:map
   [:vars [:map]]
   [:status :keyword]
   [:state any?]
   [:gx/start {:optional true} ?SignalProcessor]
   [:gx/stop {:optional true} ?SignalProcessor]])

(defn graph-dependencies [graph signal-key]
  (->> graph
       (map (fn [[k node]]
              (let [deps (-> node signal-key :env keys)]
                [k (into #{} deps)])))
       (into {})))

(defn topo-sort [graph-config graph signal-key]
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

(defn system-state [graph]
  (->> graph
       (map (fn [[k {:keys [state]}]]
              [k state]))
       (into {})))

(defn node-signal
  "Trigger a signal through a node, assumes dependencies have been run"
  [graph-config graph node-key signal-key]
  (let [signal-config (get-in graph-config [:signals signal-key])
        node (get graph node-key)
        node-state (get node :state)
        {:keys [order deps-from]} signal-config
        signal-impl (get node signal-key)
        {:keys [env processor]} signal-impl
        dep-components (select-keys graph (keys env))
        env-map' (system-state dep-components)]

    (let [new-node-state (processor {:env env-map' :state node-state})]
      (assoc node :state new-node-state))))


(defn signal [graph-config graph signal-key]
  (let [sorted (topo-sort graph-config graph signal-key)]
    (reduce
     (fn [graph k]
       (let [node (node-signal graph-config graph k signal-key)]
         (assoc graph k node)))
     graph
     sorted)))


(comment

  (def graph
    {:a {:state "A STATE"
         :gx/start
         {:env {}
          :processor
          (fn [{:keys [env state]}]
            (println "Starting A, with dep")
            :a/started)}

         :gx/stop
         {:processor
          (fn [{:keys [env state]}]
            (println "Stopping A")
            :a/stopped)}}

     :b {:gx/start
         {:env {:a any?}
          :processor
          (fn [{:keys [env state]}]
            (println "Starting B, with deps" env state)
            :b/started)}

         :gx/stop
         {:processor
          (fn [{:keys [env state]}]
            (println "Stopping B")
            :b/stopped)}}})

  (def graph-config
    {:signals {:gx/start {:order :topological}
               :gx/stop {:order :reverse-topological
                         :deps-from :gx/start}}})

  (graph-dependencies graph :gx/start)
  (graph-dependencies graph :gx/stop)

  (topo-sort graph-config graph :gx/start)
  (topo-sort graph-config graph :gx/stop)

  (signal graph-config graph :gx/start)
  (signal graph-config graph :gx/stop)

  (system-state
   (signal graph-config graph :gx/start))

  (def graph-definition-1
    {:a {:a 1}
     :z '(get (gx/ref :a) :a)
     :y '(println "starting")
     :b {:gx/start '(+ (gx/ref :z) 2)
         :gx/stop '(println "stopping")}})

  ;; (def normalized-1
  ;;   (normalize-graph graph-definition-1))

  nil)
