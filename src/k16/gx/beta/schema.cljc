(ns k16.gx.beta.schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]))

(def ?SignalConfig
  [:map
   [:order [:enum :topological :reverse-topological]]
   [:deps {:optional true} [:sequential keyword?]]
   [:from-states [:set keyword?]]
   [:to-state keyword?]
   [:deps-from {:optional true} keyword?]])

(def ?GraphConfig
  [:map
   [:signals
    [:map-of keyword? ?SignalConfig]]])

(def graph-config-explainer (m/explainer ?GraphConfig))

(defn validate-graph-config
  "Check graph config for errors,
   returns humanized explanation (if any errors)"
  [graph-config]
  (-> graph-config (graph-config-explainer) (me/humanize)))

(def ?SignalDefinition
  [:map
   [:gx/processor ifn?]
   [:gx/props {:optional true} [:map-of keyword? any?]]])

(def ?ComponentDefinition
  [:map-of keyword? ?SignalDefinition])

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
