(ns k16.gx.beta.schema
  (:require
   [malli.core :as m]
   [malli.generator :as mg]
   [malli.error :as me]))

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
   [:processor fn?]
   [:props-schema {:optional? true} m/schema?]
   [:props [:map any?]]])

(def ?NodeDefinition
  [:merge
   [:map-of :keyword ?SignalDefinition]
   [:map
    ;; TODO interesting idea (c) Alexis
    ;; [:gx/vars [:map any?]]
    [:gx/state :keyword]
    [:gx/failure {:optional true} any?]
    [:gx/value any?]]])

;; (def ?SimpleComponentDefinition
;;   [:map
;;    [:props map?]
;;    [:start fn?]
;;    [:stop fn?]])

;; (def ?ComponentDefinition
;;   [:or
;;    ?NormalizedComponentDefinition
;;    ?SimpleComponentDefinition])

;; (def explain-component (m/explainer ?ComponentDefinition))
;; (def simple-component? (m/validator ?SimpleComponentDefinition))

;; (defn validate-component!
;;   [component]
;;   (if-let [err (explain-component component)]
;;     (throw (ex-info "Component validation error" {:malli-explain err}))
;;     {:component component
;;      :simple? (simple-component? component)}))
