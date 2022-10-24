(ns components
  (:require [reitit.ring :as reitit-ring]
            [org.httpkit.server :as http-kit]
            [middlewares :as mw]))

(def ring-router
  {:gx/start
   {;; :gx/processor contains signal function
    ;; processor function must accept map with two keys
    ;; :props - resolved props
    ;; :value - current node's value
    ;; data returned by handler is a node's new value
    :gx/processor
    (fn start-router [{{:keys [routes database]} :props}]
      (reitit-ring/router
       routes {:data
               ;; inject our database via middleware
               {:middleware [[mw/database-middleware database]]}}))}})

(def ring-handler
  {:gx/start
   {:gx/processor (fn start-router [{{:keys [router]} :props}]
                    (reitit-ring/ring-handler router))}})

(def http-server
  {:gx/start
   {;; incoming props will be validated against malli schema
    ;; during signal
    :gx/props-schema [:map
                      [:handler fn?]
                      [:options :map]]
    :gx/processor (fn [{{:keys [handler options]} :props}]
                    (http-kit/run-server handler options))}

   :gx/stop {:gx/processor (fn [{server :value}]
                             (server))}})
