(ns k16.gx.beta.system
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.errors :as gx.errors]))

(defonce registry* (atom {}))

(defn reset-node
  "Override normalized graph's node from :initial-graph, it's used when
   node's component changes it's internals. new-contents - is optional argment
   which is taken over value of :initial-graph. Does nothig if normalized
   graph doesn't have a node-key."
  ([gx-map node-key]
   (reset-node gx-map node-key nil))
  ([gx-map node-key new-contents]
   (if (node-key (:graph gx-map))
     (assoc-in gx-map
               [:graph node-key]
               (or new-contents (-> gx-map :initial-graph node-key)))
     gx-map)))

(defn node-props
  [{:keys [graph]} property-key]
  (let [[comps static]
        (->> graph
             (sort-by (fn [[_ v]] (:gx/type v)))
             (partition-by (fn [[_ v]] (= :static (:gx/type v))))
             (map (partial into {})))]
    {:components (gx/get-component-props comps property-key)
     :static (gx/get-component-props static property-key)}))

(defn states
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (node-props gx-map :gx/state)))

(defn values
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (node-props gx-map :gx/value)))

(defn node-failures
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (let [{:keys [components static]} (node-props gx-map :gx/failure)
          failed-comps (->> components (filter second) (into {}))
          failed-static (->> static (filter second) (into {}))]
      (cond-> nil
        (seq failed-comps) (assoc :components failed-comps)
        (seq failed-static) (assoc :static failed-static)))))

(defn failures
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (:failures gx-map)))

(defn failures-humanized
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (map gx.errors/humanize (:failures gx-map))))

(defn register! [system-name gx-map]
  (swap! registry* assoc system-name (gx/normalize gx-map)))

(defn get-by-name
  [system-name]
  (get @registry* system-name))

(defn signal!
  ([system-name signal-key]
   (signal! system-name signal-key #{}))
  ([system-name signal-key priority-selector]
   (when-let [gx-map (get @registry* system-name)]
     (swap! registry* assoc system-name
            @(gx/signal gx-map signal-key priority-selector)))))
