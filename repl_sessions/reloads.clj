(ns reloads
  (:require [k16.gx.beta.system :as gx.system]))

(gx.system/values ::sys)
(gx.system/get-by-name ::sys)

;; example config
(def config {:config {:foo :bar}
             :instance {:val '(gx/ref :config)}})

(gx.system/register! ::sys {:graph config})

@(gx.system/signal! ::sys :gx/start)

;; update something on the fly
;; stop
@(gx.system/signal! ::sys :gx/stop)

;; update something in initial graph
(swap! gx.system/registry*
       update-in [::sys  :initial-graph :config]
       assoc :foo :baz)
;; overwrite existing normalized nodes with updated from initial-graph
(swap! gx.system/registry*
       update-in [::sys :graph]
       merge (select-keys (-> gx.system/registry*
                              deref
                              ::sys
                              :initial-graph)
                          [:config :instance]))
;; start
@(gx.system/signal! ::sys :gx/start)

