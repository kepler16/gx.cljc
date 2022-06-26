![GX Library Banner](/docs/static/img/banner.png)

GX is a data-oriented library for defining and manipulating directed acyclic graph's of state machines, for Clojure(Scipt).

Initially designed as an alternative to component systems such as [Integrant](https://github.com/weavejester/integrant), [Component](https://github.com/stuartsierra/component) or [Mount](https://github.com/tolitius/mount).

Graph topologies, signal definitions and normalisation stategies are configurable. GX makes few assumptions about the lifecycle of your system or intended usecase, but takes a batteries included approach with defaults geared towards defining component systems.

# [Documentation - WIP](https://gx.kepler16.com)

# Example

**graph.edn**
``` edn
{:http/options {:port 8080}

 :http/routes
 ["/users"
  {:get
   {:handler app/get-users}}]

 :http/ring-router {:gx/component components/ring-router-component
                    :gx/props {:routes (gx/ref :http/routes)}}

 :http/server {:gx/component components/http-kit-component
               :gx/props {:handler (gx/ref :http/ring-router)
                          :options (gx/ref :http/options)}}

 :app (println "Application starting on port " (gx/ref :http/options))

 :another {:gx/start (println "another starting")
           :gx/stop (println "another stopping")}}

```

**graph-context.edn** - define the signal schema and normalisation strategy.

``` edn
{:initial-state :uninitialised,
 :normalize {:auto-signal :gx/start
             :props-signals #{:gx/start}}
 :signal-mapping {}
 :signals {:gx/start {:from-states #{:stopped :uninitialised},
                      :to-state :started}
           :gx/stop {:from-states #{:started}
                     :to-state :stopped
                     :deps-from :gx/start}}}
```

**main.clj**

``` clojure
(ns main)

(def graph-config
  {:graph (edn/read-string (slurp "graph.edn"))
   :context (edn/read-string (slurp "graph-context.edn"))})

(def started-system
  (gx/signal graph-config :gx/start))

(def stopped-system
  (gx/signal started-system :gx/stop))
```

# Contributing

## Build
VERSION=2.0.0-SNAPSHOT just build

## Release
VERSION=2.0.0-SNAPSHOT just release

## To run clj tests

```bash
clj -X:test
```

## To run node tests

```bash
# install deps
npm i
# run tests
clojure -M:dev:shadow-cljs compile test
# or with advanced compilation
clojure -M:dev:shadow-cljs release test
```
