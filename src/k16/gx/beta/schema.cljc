(ns k16.gx.beta.schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]))

(def ?SignalConfig
  [:map {:closed true}
   [:order [:enum :topological :reverse-topological]]
   [:deps {:optional true} [:sequential keyword?]]
   [:from-states [:set keyword?]]
   [:to-state keyword?]
   [:deps-from {:optional true} keyword?]])

(def ?Context
  [:map {:closed true}
   [:initial-state keyword?]
   [:signal-mapping {:optional true} [:map-of keyword? keyword?]]
   [:normalize
    [:map
     [:auto-signal keyword?]
     [:props-signals [:set keyword?]]]]
   [:signals
    [:map-of keyword? ?SignalConfig]]])

(def graph-config-explainer (m/explainer ?Context))

(defn validate-graph-config
  "Check graph config for errors,
   returns humanized explanation (if any errors)"
  [graph-config]
  (-> graph-config (graph-config-explainer) (me/humanize)))

(def ?SignalDefinition
  [:map {:closed true}
   [:gx/processor ifn?]
   ;; push-down props
   [:gx/props {:optional true} [:map-of keyword? any?]]])

(def ?ComponentDefinition
  (mu/union
   [:map {:closed true} [:gx/props {:optional true} [:map-of keyword? any?]]]
   [:map-of keyword? ?SignalDefinition]))

(m/explain ?ComponentDefinition
           {:gx/start {:gx/processor 1}
            :gx/props {:foo 1}})

(def ?NodeDefinition
  (mu/union
   [:map
    ;; TODO some cool stuff in the future (c) Alexis
    ;; [:gx/vars map?]
    [:gx/prev-state {:optional true} keyword?]
    [:gx/state keyword?]
    [:gx/failure {:optional true} any?]
    [:gx/value any?]]
   ?ComponentDefinition))

(def ?NormalizedGraphDefinition
  [:map-of keyword? ?NodeDefinition])
