(ns resolves
  (:require [k16.gx.beta.system :as gx.system]
            [io.aviso.exception]))

(def server
  {:gx/component {:gx/start {:gx/processor (fn [_] :_foo)}
                  :gx/stop {:gx/processor (fn [_] :_bar)}}})

(def client
  {:gx/component {:gx/start {:gx/processor
                             (fn [{:keys [props]}]
                               (println props)
                               :client_foo)
                             :gx/props-schema int?}
                  :gx/stop {:gx/processor
                            (fn [{:keys [props]}]
                              (println props)
                              :client_bar)}}})

(defn graph []
  {:graph {:http/server server
           :http/client {:gx/component client
                         :gx/props "foo"}}})

(comment
  (gx.system/register! ::test (graph))
  (gx.system/get-by-name ::test)
  (println (gx.system/failures-humanized ::test))
  @(gx.system/signal! ::test :gx/start)
  @(gx.system/signal! ::test :gx/stop)

  nil)

