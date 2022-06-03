(ns k16.gx.beta.system
  (:require [k16.gx.beta.core :as gx]))

(defonce registry* (atom {}))

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

(defn failures
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (let [{:keys [components static]} (node-props gx-map :gx/failure)
          failed-comps (->> components (filter second) (into {}))
          failed-static (->> static (filter second) (into {}))]
      (cond-> nil
        (seq failed-comps) (assoc :components failed-comps)
        (seq failed-static) (assoc :static failed-static)))))

(defn register! [system-name gx-map]
  (swap! registry* assoc system-name (gx/normalize gx-map)))

(defn get-by-name
  [system-name]
  (get @registry* system-name))

(defn signal!
  [system-name signal-key]
  (when-let [gx-map (get @registry* system-name)]
    (swap! registry* assoc system-name @(gx/signal gx-map signal-key))))
