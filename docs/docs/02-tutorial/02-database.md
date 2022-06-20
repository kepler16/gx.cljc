---
id: database
title: Practical Example App
sidebar_label: Adding database
slug: /adding-database
---

# Adding database component

We will be using simple sqlite in memory setup for the sake of simplicity.

## Our deps.edn should look like this

```clojure title="deps.edn"
{:paths ["src"]
 :mvn/repos {"github-kepler"
             {:url "https://maven.pkg.github.com/kepler16/gx.cljc"}}
 :deps {org.clojure/java.jdbc  {:mvn/version "0.7.8"}
        org.xerial/sqlite-jdbc {:mvn/version "3.23.1"}
        metosin/reitit {:mvn/version "0.5.18"}
        http-kit/http-kit {:mvn/version "2.3.0"}
        kepler16/gx.cljc {:local/root "../../"}}
 :aliases
 {:main {:main-opts  ["-m" "main"]}}}
```

## Start by updating our config file

We'll add three more nodes:
- database config
- database instance component
- database populate component, to pupulate data on every startup, because our database is in-memory only

New config options:

```clojure title="resources/config.edn"
;; db config
:sqlite/db-uri {:connection-uri "jdbc:sqlite::memory:"}

;; db instance which depends on db config
:sqlite/database {:gx/component database/sqlite
                  :gx/props (gx/ref :sqlite/db-uri)}

;; populate component which depends on db instance
:sqlite/db-populator {:gx/component database/db-populator
                      :gx/props (gx/ref :sqlite/database)}
```

And add a new `database` namespace with db-related components:

```clojure title="src/database.clj"
(ns database
  (:require [clojure.java.jdbc :as jdbc]))

;; start database instance function
(defn start!
  [{db-spec :props}]
  ;; assuming, that in real life a database connection pool
  ;; should be instantiated
  {:connection (jdbc/get-connection db-spec)})

;; stop database instance function
(defn stop!
  [{db-spec :value}]
  (.close (:connection db-spec))
  nil)

;; database component
(def sqlite
  {:gx/start {:gx/processor start!}
   :gx/stop {:gx/processor stop!}})

;; we populate our database with a sample data
(defn populate!
  [{db-spec :props}]
  (jdbc/execute!
   db-spec "create table users (id integer, name text, last_name text)")
  (jdbc/insert-multi!
   db-spec :users [{:id 1 :name "John" :last_name "Doe"}
                   {:id 2 :name "Peter" :last_name "Parker"}
                   {:id 3 :name "Richard" :last_name "Walker"}
                   {:id 4 :name "Sergey" :last_name "Matvienko"}])
  true)

;; db-populator has only :gx/start handler
;; and there is no teardown logic during stop
;; in case of :gx/stop only status will be changed to :stopped
;; and value will be left intact
(def db-populator
  {:gx/start {:gx/processor populate!}})
```

Somehow we need to pass our database instance to a router, lets update routers's config props:

```clojure title="resouces/config.edn"
:http/ring-router {:gx/component components/ring-router
                   :gx/props {:routes (gx/ref :http/routes)
                              ;; ref to a database instance
                              :database (gx/ref :sqlite/database)}}
```

We add new middlewrare to inject database instance into request map

```clojure title="src/middlewares.clj"
(ns middlewares)

(defn database-middleware
  [handler database]
  (fn [request]
    ;; all handlers can access db by key from req map
    (handler (assoc request :db-spec database))))
```

Next, update our router:

```clojure title="src/components.clj"
(ns components
  (:require [reitit.ring :as reitit-ring]
            [org.httpkit.server :as http-kit]
            ;; import our middlewares
            [middlewares :as mw]))

(def ring-router
  {:gx/start
   {:gx/processor
    ;; processor now receives database in props
    (fn start-router [{{:keys [routes database]} :props}]
      (reitit-ring/router
       routes {:data
               ;; inject database via middleware
               {:middleware [[mw/database-middleware database]]}}))}})
```

and a route handler:
```clojure title="src/app.clj"
(ns app)

(defn get-users-handler
  [req]
  {:status 200
   :content-type "text/plain"
   :body (pr-str
          ;; we getting db-spec key from request map
          (j/query (:db-spec req) ["select * from users"]))})
```

Open your browser: http://localhost:8080/users to see changes.

Full source code is available on [github](https://github.com/kepler16/gx.cljc/tree/master/examples/simple).