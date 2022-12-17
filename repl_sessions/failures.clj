(ns failures
  (:require [k16.gx.beta.system :as gx.system]))

(defn start-1 [_]
  (let [a 10
        e 0]
    (/ a e)))

(defn stop-1 [_] :stopped)

(def parent-component {:gx/start {:gx/processor start-1}
                       :gx/stop {:gx/processor stop-1}})

(def graph {:config {:foo {:bar "something"}}
            :parent {:gx/component 'failures/parent-component
                     :gx/props '(gx/ref :config)}
            :child {:foo 1
                    :paren-data '(gx/ref :parent)}})

(def system_name ::sys)

(comment
  (gx.system/register! system_name {:graph graph})
  @(gx.system/signal! system_name :gx/start)
  (print (gx.system/failures-humanized system_name))
  (gx.system/failed-nodes system_name)
  (:exception (first (:causes (last (gx.system/failures system_name)))))
  (:internal-data (first (:failures (ex-data (.getCause *e)))))
  (first (gx.system/failures system_name)))
