(ns system
  (:require [app]
            [clojure.edn :as edn]
            [k16.gx.beta.core :as gx]))

(defonce graph-config* (atom nil))
(defonce app-config* (atom nil))
(defonce sys* (atom nil))

(defn init-system!
  []
  (println "Initializing...")
  (reset! app-config* (edn/read-string (slurp "resources/config.edn")))
  (reset! graph-config* (edn/read-string (slurp "resources/graph.config.edn")))
  (reset! sys* (gx/normalize-graph @graph-config* @app-config*)))

(defn start-system!
  []
  (println "Starting...")
  (let [s (reset! sys* @(gx/signal @graph-config* @sys* :app/start))
        http-port (-> @sys* gx/system-value :http/options :port)]
    (println "Server listening at port:" http-port)
    s))

(defn stop-system!
  []
  (println "Stopping...")
  (reset! sys* @(gx/signal @graph-config* @sys* :app/stop)))

(defn restart-system!
  []
  (stop-system!)
  (init-system!)
  (start-system!))

(comment
  (restart-system!)
  (init-system!)
  (start-system!)
  (stop-system!))

