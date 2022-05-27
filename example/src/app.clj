(ns app
  (:require [k16.gx.beta.core :as gx]
            [reitit.ring :as reitit-ring]
            [org.httpkit.server :as http-kit]))

;; /users route handler
(defn get-users
  [_args]
  {:status 200
   :content-type "text/plain"
   :body (pr-str [{:user "John"}
                  {:user "Peter"}
                  {:user "Donald"}])})

;; router component
(def router
  {:app/start {;; :gx/processor contains signal function
               ;; processor function must accept map with two keys
               ;; :props - resolved props
               ;; :value - current node's value
               ;; data returned by handler is a new node value
               :gx/processor
               ;; router's :app/start signal function
               ;; this handler gets :http/routes value as props
               (fn start-router [{routes :props}]
                 (reitit-ring/router routes))
               ;; props is resolved from graph and passed to processor
               ;; this props can be overriden by signal props in config.edn
               :gx/props (gx/ref :http/routes)}
   :app/stop {:gx/processor (constantly nil)}})

;; handler component
(def handler
  {:app/start {:gx/processor
               ;; handler's :app/start signal function
               ;; get router from props and create ring handler
               (fn start-handler [{router :props}]
                 (reitit-ring/ring-handler router))
               :gx/props (gx/ref :http/router)}
   ;; we set handler to nil on :app/stop
   :app/stop {:gx/processor (constantly nil)}})

;; servers's :app/start signal function with more complex
;; props definition (see config.edn)
;; http-kit returns stop function, which will be stored in node's value
(defn start-server [{:keys [props]}]
  (http-kit/run-server (:http/handler props)
                       (:http/options props)))

;; we stop server by getting stop function from node value
(defn stop-server [{stop-server :value}]
  (when stop-server
    (stop-server))
  nil)

;; http server component
(def http-server
  {:app/start {:gx/processor start-server
               :gx/props (gx/ref-maps :http/options
                                      :http/handler)}
   :app/stop {:gx/processor stop-server}})
