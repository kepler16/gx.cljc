(ns errors
  (:require [k16.gx.beta.system :as gx.system]))


(def server
  {:gx/component
   {:gx/start {:gx/processor (fn [_] (clojure.edn/read-string
                                       (slurp "repl_sessions/config_with_error.edn")))}
    :gx/stop {:gx/processor (fn [_] nil)}}})

(def config
  {:options {:port 8080
             :secure? false}
   :server {:gx/component 'errors/server
            :gx/props {:foo '(gx/ref :options)}}})

(gx.system/register! ::err {:graph config})
@(gx.system/signal! ::err :gx/start)
(println (gx.system/failures-humanized ::err))
