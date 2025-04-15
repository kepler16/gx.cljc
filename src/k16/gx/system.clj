(ns k16.gx.system
  (:require
   [k16.gx :as gx]))

(defprotocol GxSystem
  (start! [this])
  (stop! [this]))

(defn system [graph]
  (let [graph (atom (gx/validate! graph))
        state (atom :stopped)]
    (reify GxSystem
      (start! [_]
        (let [next (swap! graph (fn [graph]
                                  (gx/signal!
                                   graph
                                   :start)))]
          (reset! state :started)
          (gx/values next)))

      (stop! [_]
        (swap! graph (fn [graph]
                       (gx/signal!
                        graph
                        :stop
                        {:filter-states #{:start}
                         :order :reverse})))
        (reset! state :stopped)
        nil)

      clojure.lang.IDeref
      (deref [_]
        (when (= :stopped @state)
          (throw (ex-info "System is stopped. You cannot deref a stopped system" 
                          {})))
        (gx/values @graph)))))
