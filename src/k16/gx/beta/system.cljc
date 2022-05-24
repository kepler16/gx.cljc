(ns k16.gx.beta.system
  (:require [k16.gx.beta.core :as gx]))

(defonce registry* (atom {}))

(defn register [graph-name resolver]
  (swap! assoc graph-name {:resolver resolver}))

(defn resolve [graph-name]
  (let [{:keys [resolver previous]} (get @registry* graph-name)]
     (resolver)))

(defn signal [graph-name signal-key]
  (let [{:keys [previous]} (get @registry* graph-name)
        {:keys [graph-config graph]} (resolve graph-name)]
    (gx/signal graph signal-key graph-config)))
