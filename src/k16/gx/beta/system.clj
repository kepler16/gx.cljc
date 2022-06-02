(ns k16.gx.beta.system
  (:require [k16.gx.beta.core :as gx]))

(defonce registry* (atom {}))

(defn register! [system-name gx-map]
  (swap! registry* assoc system-name (gx/normalize gx-map)))

(defn get-by-name
  [system-name]
  (get @registry* system-name))

(defn signal!
  [system-name signal-key]
  (when-let [gx-map (get @registry* system-name)]
    (swap! registry* assoc system-name @(gx/signal gx-map signal-key))))
