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

  ;; we don't want semi-started system on production
  ;; check for failures, print and exit if any
  (when-let [failures (seq (failures))]
    (doseq [failure failures]
      (println failure))
    (System/exit 1))

  @(promise))

(comment
  (load-system!)

  (-> @gx.system/registry* ::system)

  (start!)

  (stop!)
  (reset!)
  (failures)

  (def fancy-graph
    {:user/data {:name "Angron"
                 :also-named "Red Angel"
                 :spoken-language "Nagrakali"
                 :side :chaos}
   ;; clojure(script) core functions, fully qualified other functions and
   ;; gx refs will be resolved by GX
   ;; special forms and macros are not supported (e.g. throw, if, loop etc)
     :user/name '(get (gx/ref :user/data) :name)
     :user/lang '(get (gx/ref :user/data) :spoken-language)})

  (def system
    @(gx/signal {:graph fancy-graph} :gx/start))

  (gx/system-value system)
  (gx/system-failure system)
  ;; => #:user{:data nil, :name nil, :lang nil}

  (gx/system-value system)
  )
