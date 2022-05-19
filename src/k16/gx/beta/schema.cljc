(ns k16.gx.beta.schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]))

(def ?SignalConfig
  [:map
   [:order [:enum :topological :reverse-topological]]
   [:deps {:optional true} [:sequential keyword?]]
   [:from-state [:set :keyword]]
   [:to-state :keyword]
   [:deps-from {:optional true} :keyword]])

(def ?GraphConfig
  [:map
   [:signals
    [:map-of :keyword ?SignalConfig]]])

(def graph-explainer (m/explainer ?GraphConfig))

(defn conform-graph-config
  "Checks config for errors.
   Returns vector with first element as error explanation
   and second element is config itself"
  [config]
  [(-> config (graph-explainer) (me/humanize)) config])

(defn conform-graph-config!
  "Check config for errors, throws ex-info with error explanation"
  [config]
  (let [[explanation config'] (conform-graph-config config)]
    (if explanation
      (throw (ex-info "Graph config error" {:explanation explanation}))
      config')))

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
