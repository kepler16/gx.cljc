---
id: example
title: Practical Example App
sidebar_label: Example App
slug: /example-app
---

# More practical example

You can't do much with static nodes. GX supports components, **component** is a dynamic node with custom signal handlers.

We need graph and app configs. Lets define two signals for start and stop:

```clojure title="resources/graph.config.edn"
{:signals
 {:app/start {:order :topological
              :from-states #{:stopped :uninitialized}
              :to-state :started}
  :app/stop {:order :reverse-topological
             :from-states #{:started}
             :to-state :stopped}}}
```

In app config need:
- http server options
- routes (example only, better to define routes inside code and add as component)
- router component
- handler component
- server component

```clojure title="resources/config.edn"
{:http/options {:port 8080}

 :http/routes ["/users"
               ;; existing functions shold be linked with fully qualified names
               {:get {:handler app/get-users}}]
 :http/router {;; component is a fully qualified name of defined component
               :gx/component app/router
               ;; you can override component's signal props on graph level
               :app/start {:gx/props (gx/ref :http/routes)}}
 :http/handler {:gx/component app/handler
                :app/start {:gx/props (gx/ref :http/router)}}
 :http/server {:gx/component app/http-server
               :app/start {:gx/props (gx/ref-maps :http/options
                                                  :http/handler)}}}

```
Our app is a simple web server with one route `/users`. You may notice new keyword `:gx/component` it is a component node. We using two different types of gx references in our config. Here is a full list of gx ref types:

| type          | description        | example                                                  |
| ------------- | ------------------ | -------------------------------------------------------- |
| `gx/ref`      | gets node's value  | `(gx/ref :a)` => `{:foo 1}`                              |
| `gx/ref-map`  | get node           | `(gx/ref-map :a)` => `{:a {:foo 1}}`                |
| `gx/ref-maps` | gets list of nodes | `(gx/ref-map :a :b)` => `{:a {:foo 1} :b {:bar 1}}` |
| `gx/ref-path` | get-in to node     | `(gx/ref-path :a :foo)` => `1`                           |

Alright, lets write app code:

```clojure title="src/app.clj"
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
```

We defined `get-users` handler and `router` component, they linked in our configuration in `:http/routes` and `:http/router` nodes respectively. Next we add rest of our components:
```clojure title="src/app.clj"
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
```

Sweet, out app is ready. Next we add some boring system routines:

```clojure title="src/system.clj"
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
```

And if we want to launch from terminal, lets add main class:

```clojure title="src/main.clj"
(ns main
  (:require [system])
  (:gen-class))

(defn -main [& _args]
  (system/init-system!)
  (system/start-system!)

  (doto (Runtime/getRuntime)
    ;; graceful shutdown
    (.addShutdownHook
     (Thread. #(system/stop-system!))))
  @(promise))

```

Lets run app from terminal
```
clj -M:main
```
Open your browser: http://localhost:8080/users. Viola! On the next step we will add database component.

Full source code is available on [github](https://github.com/kepler16/gx.cljc/tree/gx-v2/example).