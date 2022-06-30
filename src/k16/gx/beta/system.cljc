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

(defn- filter-nodes
  [graph selector]
  (if (seq selector)
    (select-keys graph selector)
    graph))

(defn states
  ([system-name]
   (states system-name nil))
  ([system-name selector]
   (when-let [gx-map (get @registry* system-name)]
     (filter-nodes (gx/states gx-map) selector))))

(defn values
  ([system-name]
   (values system-name nil))
  ([system-name selector]
   (when-let [gx-map (get @registry* system-name)]
     (filter-nodes (gx/values gx-map) selector))))

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
  "Sends signal to system synchronously in clojure, asynchronously in cljs"
  ([system-name signal-key]
   (signal! system-name signal-key nil))
  ([system-name signal-key selector]
   (when-let [gx-map (get @registry* system-name)]
     #?(:clj
        (swap! registry* assoc system-name
               @(gx/signal gx-map signal-key selector))
        :cljs
        (.then (gx/signal gx-map signal-key selector)
               (fn [v] (swap! registry* assoc system-name v)))))))
