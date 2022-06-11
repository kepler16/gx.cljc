(ns main
  (:require [app]
            [clojure.edn :as edn]
            [k16.gx.beta.core :as gx]
            [k16.gx.beta.system :as gx.system]))

(defn load-system! []
  (gx.system/register!
   ::system
   {:context gx/default-context
    :graph (edn/read-string (slurp "resources/config.edn"))}))

(defn start! []
  (gx.system/signal! ::system :gx/start))

(defn stop! []
  (gx.system/signal! ::system :gx/stop))

(defn reset! []
  (stop!)
  (load-system!)
  (start!))

(defn failures []
  (gx.system/failures-humanized ::system))

(defn -main [& _args]
  (load-system!)

  (doto (Runtime/getRuntime)
    (.addShutdownHook
     (Thread. #(stop!))))

  (start!)

  @(promise))

(comment
  (load-system!)

  (-> @gx.system/registry* ::system)

  (start!)

  (stop!)
  (reset!)
  (failures))
