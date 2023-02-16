(ns errors
  (:require
   [clojure.edn :as edn]
   [k16.gx.beta.system :as gx.system]))

(def server
  {:gx/component
   {:gx/start {:gx/processor (fn [_] (edn/read-string
                                      (slurp "repl_sessions/config_with_error.edn")))
               :gx/props-schema [:map
                                 [:port :int]
                                 [:secure? :boolean]
                                 [:foo :int]
                                 [:bar :string]]}
    :gx/stop {:gx/processor (fn [_] nil)}}})

(def config
  {:options {:port 8080
             :secure? false}
   :server {:gx/component 'errors/server
            :gx/props '(gx/ref :options)}
   :user {:server '(gx/ref :server)}})

(gx.system/register! ::err {:graph config})

@(gx.system/signal! ::err :gx/start)

(println (gx.system/failures-humanized ::err))
(println (gx.system/failures ::err))
