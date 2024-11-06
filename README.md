<div align="center">
  <h1>gx</h1>
</div>

<div align="center">
  <p>
    A data-oriented Clojure library for defining and executing directed acyclic graph's of state machines.
  </p>
</div>

## Reference

- **[About](#about)**
- **[Quick Start](#quick-start)**
- **[Concepts](#concepts)**
  - **[Refs](#refs)**
  - **[Components](#components)**
  - **[Signal Processing](#signal-processing)**
- **[Validation](#validation)**
  - **[Malli Support](./docs/malli.md)**
- **[Components as Data](#components-as-data)**

## About

Initially designed as an alternative to component systems such as [Integrant](https://github.com/weavejester/integrant),
[Component](https://github.com/stuartsierra/component) or [Mount](https://github.com/tolitius/mount).

## Quick Start

```clojure
(ns app.example
  (:require
   [org.httpkit.server :as http]))

(def server-component
  {:signals {:start (fn start-server [_ {:keys [system port]}]
                      (http/run-server (create-router system)
                                       {:port port}))
             :stop (fn stop-server [server _]
                     (.close server))}})

(def graph
  {:config {:db-path "./data/db"
            :http-port 8080}

   :db (gx/with-refs [db-name (gx/ref [:config :db-path])]
         (jdbc/get-datasource {:dbtype "sqlite"
                               :dbname db-name}))

   :system {:db (gx/ref :db)}

   :http/server (gx/component {:system (gx/ref :system)
                               :port (gx/ref [:config :http-port])}
                              server-component)})

(comment
  (def live-graph
    (gx/signal! graph :start))

  (gx/signal! live-graph :stop))
```

## Concepts

### **[Refs](#refs)**

A ref can be thought of like a stateless pointer to some other part of the graph - represented as a path.

It can be 'resolved' by calling `lookup-in` and passing some resolved or partially resolved graph for the ref to use
when looking up its value. This is done internally by `gx/values` and `gx/signal!`.

A resolved ref can be derefed using plain old `deref` or the `@` reader macro to obtain its resolved value.

Refs can be inspected to see what their dependencies on other parts of the graph are, which is useful for determining in
what order to resolve a graph.

### **[Components](#components)**

Components are similar to refs in that they can have dependencies on other parts of the graph and are resolved to a
value. Unlike refs however, components are stateful and transition through a lifecycle. This transition happens by
passing `signals` through the graph which are then processed by components.

Components are made up of input props (which are essentially [refs](#refs) to other values in the graph) and signal
handlers.

### **[Signal Processing](#signal-processing)**

### **[Validation](#validation)**

Component definitions can describe validating functions which will be called as part of executing signals

### **[Components As Data](#components-as-data)**
