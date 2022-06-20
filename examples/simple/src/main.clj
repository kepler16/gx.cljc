(ns main
  (:require [app]
            [clojure.edn :as edn]
            [k16.gx.beta.core :as gx]
            ;; this ns contains helpers for managing systems
            [k16.gx.beta.system :as gx.system]))

(defn load-system! []
  ;; register our system, so later we can get it by name
  (gx.system/register!
   ::system
   ;; :context key is optional, fallbacks to gx/default-context
   {:context gx/default-context
    :graph (edn/read-string (slurp "resources/config.edn"))}))

(defn start! []
  (gx.system/signal! ::system :gx/start))

(defn stop! []
  (gx.system/signal! ::system :gx/stop))

(defn reload! []
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

  ;; we don't want semi-started system on production
  ;; check for failures, print and exit if any
  (when-let [failures (seq (failures))]
    (doseq [failure failures]
      (println failure))
    (System/exit 1))

  @(promise))

(comment
  ;; load system from config.edn
  (load-system!)
  ;; start system
  (start!)
  ;; stop system
  (stop!)
  ;; full reload
  (reload!)

  ;; view list of failures (if any)
  (failures)

  ;; states of nodes
  (gx.system/states ::system)
  ;; node values
  (gx.system/values ::system)
  ;; human readable failures
  (gx.system/failures-humanized ::system)


  (def graph {:http/options {:port 8080}
              :http/server {:gx/component 'non.existent/component}})
  (gx.system/register! :sys1 {:graph graph})
  (gx.system/failures :sys1)
  (gx.system/failures-humanized :sys1)
  )
