(ns k16.gx.beta.system
  (:require [k16.gx.beta.core :as gx]))

(def registry* (atom {}))

(defn register [graph-name resolver]
  (swap! registry* assoc graph-name {:resolver resolver}))

(defn resolve-graph [graph-name]
  (let [{:keys [resolver]} (get @registry* graph-name)]
     (resolver)))

(defn signal [graph-name signal-key]
  (let [previous (get @registry* graph-name)
        {:keys [graph-config graph]} (resolve-graph graph-name)
        new-graph @(gx/signal (or (:graph previous) graph) signal-key graph-config)]
    (tap> {:previous previous})
    (swap! registry* assoc graph-name
           {:resolver (:resolver previous)
            :graph new-graph
            :graph-config graph-config
            :previous (dissoc previous :previous)})
    new-graph))
