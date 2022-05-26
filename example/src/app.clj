(ns app
  (:require [k16.gx.beta.registry :as gx.reg]
            [reitit.ring :as reitit-ring]
            [org.httpkit.server :as http-kit]))

(gx.reg/defun get-users
  [_]
  {:status 200
   :content-type "text/plain"
   :body (pr-str [{:user "John"}
                  {:user "Peter"}
                  {:user "Donald"}])})

(defn start-router
  [{router :props}]
  (reitit-ring/router router))

(defn start-handler
  [{router :props}]
  (reitit-ring/ring-handler router))

(defn start-server [{:keys [props]}]
  (http-kit/run-server (:http/handler props)
                       (:http/options props)))

(defn stop-server [{stop-server :value :as args}]
  (when stop-server
    (stop-server))
  nil)

(gx.reg/defcomp router
  {:app/start {:gx/processor start-router}
   :app/stop {:gx/processor (constantly nil)}})

(gx.reg/defcomp handler
  {:app/start {:gx/processor start-handler}
   :app/stop {:gx/processor (constantly nil)}})

(gx.reg/defcomp http-server
  {:app/start {:gx/processor start-server}
   :app/stop {:gx/processor stop-server}})
