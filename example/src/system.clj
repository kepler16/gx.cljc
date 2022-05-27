(ns system
  (:require [app]
            [clojure.edn :as edn]
            [k16.gx.beta.core :as gx]))

;; we store all our app state in theese three atoms
(defonce graph-config* (atom nil))
(defonce app-config* (atom nil))
(defonce sys* (atom nil))

;; loads all configurations and runs normalization
(defn init-system!
  []
  (println "Initializing...")
  (reset! app-config* (edn/read-string (slurp "resources/config.edn")))
  (reset! graph-config* (edn/read-string (slurp "resources/graph.config.edn")))
  (reset! sys* (gx/normalize-graph @graph-config* @app-config*)))

;; sends :app/start to our graph
(defn start-system!
  []
  (println "Starting...")
  (let [s (reset! sys* @(gx/signal @graph-config* @sys* :app/start))
        ;; we get :http/options node value to log port number in console
        http-port (-> @sys* gx/system-value :http/options :port)]
    (println "Server listening at port:" http-port)
    s))

;; sends :app/stop to our graph
(defn stop-system!
  []
  (println "Stopping...")
  (reset! sys* @(gx/signal @graph-config* @sys* :app/stop)))

;; full restart, loads configurations in case of any updates
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
