(ns k16.gx.beta.schema
  (:require
   [malli.core :as m]
   [malli.generator :as mg]
   [malli.error :as me]
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

(m/validate
 [:map
  [:x string?]
  [[:opt :y] int?]
  [keyword? [:vector int?]]]
 {:x "x"
  :z [1 2 3]})

(def ?NodeDefinition
  [:map
    ;; TODO interesting idea (c) Alexis
    ;; [:gx/vars [:map-of keyword? any?]]
   [:gx/state keyword?]
   [:gx/failure {:optional true} any?]
   [:gx/value any?]
   [:gx/start ?SignalDefinition]]
  #_[:and
   [:map
    ;; TODO interesting idea (c) Alexis
    ;; [:gx/vars [:map-of keyword? any?]]
    [:gx/state keyword?]
    [:gx/failure {:optional true} any?]
    [:gx/value any?]
    [:gx/start ?SignalDefinition]]
   [:map-of keyword? ?SignalDefinition]])

(def ?NormalizedGraphDefinition
  [:map-of keyword? ?NodeDefinition])

(m/explain
 ?NormalizedGraphDefinition
 {:g1
  #:gx{:state :uninitialized,
       :value nil,
       :start
       {:props {},
        :processor identity}}})

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
