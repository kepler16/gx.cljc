(ns k16.gx.beta.schema
  (:require
   [malli.util :as mu]))

(def ?SignalConfig
  [:map
   [:order [:enum :topological :reverse-topological]]
   [:from-state [:set :keyword]]
   [:to-state :keyword]
   [:deps-from {:optional true} :keyword]])

(def ?GraphConfig
  [:map
   [:signals
    [:map-of :keyword ?SignalConfig]]])

(def ?SignalDefinition
  [:map
   [:processor ifn?]
   [:props-schema {:optional true} vector?]
   [:props [:map-of keyword? any?]]])

(def ?NodeDefinition
  (mu/union
   [:map
    [:gx/state keyword?]
    [:gx/failure {:optional true} any?]
    [:gx/value any?]]
   [:map-of keyword? ?SignalDefinition]))

(def ?NormalizedGraphDefinition
  [:map-of keyword? ?NodeDefinition])
