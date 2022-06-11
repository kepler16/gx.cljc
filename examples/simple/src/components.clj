(ns components
  (:require [reitit.ring :as reitit-ring]
            [org.httpkit.server :as http-kit]))

(def ring-router-component
  {:gx/start
   {:gx/processor
    (fn start-router [{{:keys [routes]} :props}]
      (reitit-ring/ring-handler
        (reitit-ring/router routes)))}})

(def http-kit-component
  {:gx/start
   {:gx/props-schema [:map
                      [:handler fn?]
                      [:options :map]]
    :gx/processor
    (fn [{{:keys [handler options]} :props}]
      (http-kit/run-server handler options))}

   :gx/stop
   {:gx/processor
    (fn [{server :value}]
      (server))}})
