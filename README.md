![GX Library Banner](/docs/static/img/banner.png)

GX is a data-oriented library for defining and manipulating directed acyclic graph's of state machines, for Clojure(Scipt).

Initially designed as an alternative to component systems such as [Integrant](https://github.com/weavejester/integrant), [Component](https://github.com/stuartsierra/component) or [Mount](https://github.com/tolitius/mount).

Graph topologies, signal definitions and normalisation stategies are configurable. GX makes few assumptions about the lifecycle of your system or intended usecase, but takes a batteries included approach with defaults geared towards defining component systems.

## [Documentation - WIP](https://gx.kepler16.com)

# Rationale

TODO expand these topics:
- Data driven
- REPL first approach
- First class error handling/reporting
- Inline transformations for referenced values
- Components are maps without any external dependencies
- Validation of data flow (component props schemas using malli)
- Validation of dependencies

# Example configuration

**config.edn**
```edn
{;; data component
 :http/options {:port 8080}
 ;; data component with a linked fully qualified function
 :http/routes ["/users" {:get {:handler app/get-users}}]

 ;; link to a predefined component with signal processors
 :http/ring-router {:gx/component components/ring-router-component
                    :gx/props {:routes (gx/ref :http/routes)}}

 :http/server {:gx/component components/http-kit-component
               :gx/props {:handler (gx/ref :http/ring-router)
                          :options (gx/ref :http/options)}}

 ;; data component which evaluates to function call on start signal
 :app (println "Application starting on port " (gx/ref :http/options))

 :another {:gx/start (println "another starting")
           :gx/stop (println "another stopping")}}
```

# Usage

## Referencing nodes

Configuration may contain gx/refs - references to a specific node value or
a subset of its value. There are two kind of refs:
- `gx/ref` - gets value of a node
- `gx/ref-keys` - is a shorthand for `(select-keys whole-config [:key1 key2])`
```clojure
;; config.edn file
{:env {:db/uri "some db uri"
       :http/port 8080}
 :env-copy (gx/ref :env)
 :db/opts (gx/ref-keys [:env]) ;; {:env {:db/uri "some db uri"
                               ;;        :http/port 8080}}
 ;; use core function `get` to take a value of a specific key
 :http/opts {:port (get (gx/ref :env) :http/port)}}}) ;; 8080
```

## Creating a new system

System configuration usually defined in an external file, but can be used as a simple map during developmet and experimenting.

## System config
```clojure
;; define a config by loading from external source
(def system-config {:graph (edn/read-string (slurp "config.edn"))})
;; or define it inline
;; note: in source code all fully qualified symbols and gx/refs should be quoted
(def system-config {:graph {:http/options {:port 8080}
                            :http/routes ["/users" {:get {:handler 'app/get-users}}]
                            :http/ring-router
                            {:gx/component 'components/ring-router-component
                             :gx/props {:routes '(gx/ref :http/routes)}}}})
```
## System API functions
Despite all magic is happening in `k16.gx.beta.core` the direct usage of it is considered for advanded cases. The namespase `k16.gx.beta.system` contains convenient utility functions to work with systems:
```clojure
(ns main
  (:require [k16.gx.beta.system :as gx]))

(def system-config
  {:graph {:user {:first-name "John"
                  :last-name "Show"
                  :company '(gx/ref :company)}
           :company {:name "The Wall inc."
                     ;; notice intentionnal mistake: circular dependency
                     :members '[(gx/ref :user)]}}})
```

### **gx/register!**
Register and validate a system by name, may throw validation errors. Returns registered system.
```clojure
(gx/register! ::my-system system-config)
```
This call throws an exception with following formatted message:
```
Normalize error
Dependency errors: signal = ':gx/start'
	• circular :company -> :user -> :company
Dependency errors: signal = ':gx/suspend'
	• circular :company -> :user -> :company
Dependency errors: signal = ':gx/resume'
	• circular :company -> :user -> :company
Dependency errors: signal = ':gx/stop'
	• circular :company -> :user -> :company
```
### **get-by-name**
Even if error is thrown system is still accessible. This function returns (pre)normalized system with following keys:
- `:graph` - normalized graph
- `:initial-graph` - system-config map
- `:context` - GX configuration with sane defaults in core namespace
- `:failures` - list of failures as maps
```clojure
(gx/get-by-name ::my-system)
```

### **gx/failures**
Is used to examine a failures of a system. Returns a list of failures as data structures.
```clojure
(gx/failures ::my-system)
;; =>
;; ({:internal-data {:errors ("circular :company -> :user -> :company")},
;;   :message "Dependency errors",
;;   :error-type :deps-sort,
;;   :signal-key :gx/start}
;;  {:internal-data {:errors ("circular :company -> :user -> :company")},
;;   :message "Dependency errors",
;;   :error-type :deps-sort,
;;   :signal-key :gx/suspend}
;;  {:internal-data {:errors ("circular :company -> :user -> :company")},
;;   :message "Dependency errors",
;;   :error-type :deps-sort,
;;   :signal-key :gx/resume}
;;  {:internal-data {:errors ("circular :company -> :user -> :company")},
;;   :message "Dependency errors",
;;   :error-type :deps-sort,
;;   :signal-key :gx/stop})
```

Lets fix our config by removing `:company` dependency from `:user` and re-register:
```clojure
(def system-config
  {:graph {:user {:first-name "John"
                  :last-name "Show"}
           :company {:name "The Wall inc."
                     :members '[(gx/ref :user)]}}})

;; now registers without errors
(gx/register! ::my-system system-config)
```

### **gx/signal!**
Sends a signal to a system. Signal passes through numerous runtime validations and can throw an exception. Uses [funcool promesa](https://github.com/funcool/promesa) under the hood and returns promise:
```clojure
@(gx/signal! ::my-system :gx/start)
;; retuns a system after signal execution
```

### **gx/values**
Returns values of a system:
```clojure
(gx/values ::my-system)
;; =>
;; {:user {:first-name "John",
;;         :last-name "Show"},
;;  :company
;;  {:name "Ice Wall inc.",
;;   :members [{:first-name "John",
;;              :last-name "Show"}]}}
```
### **gx/states**
Returns states of a system
```clojure
(gx/states ::my-system)
;; =>
;; {:user :started, :company :started}
```

## Creating components

Components is a maps. You can define them anywhere and plug in to your config.
Here is an example of counter component which runs as a background process, it has its own instance stored in a graph's node value.
```clojure
(defn start
  [{:keys [props]}]
  (let [inst* (atom {:stop? false
                     :suspended? false
                     :counter (:counter props)})]
    (future (loop []
              (if (:stop? @inst*)
                (println "stopped")
                (do
                  (Thread/sleep 1000)
                  (when-not (:suspended? @inst*)
                    (println "counting...")
                    (swap! inst* update :counter inc))
                  (recur)))))
    ;; rerutns its instance, this becomes node's value
    inst*))

(defn suspend
  [{:keys [value]}]
  ;; modifying atom in existing node value
  (swap! value assoc :suspended? true)
  ;; value should be returned from every processor handler
  value)

(defn resume
  [{:keys [value]}]
  (swap! value assoc :suspended? false)
  value)

(defn stop
  [{:keys [value]}]
  (swap! value assoc :stop? true)
  ;; we don't need value anymore
  nil)

;; definitions of a component
(def my-counter-component
  {:gx/start {:gx/processor start}
   :gx/suspend {:gx/processor suspend}
   :gx/resume {:gx/processor resume}
   :gx/stop {:gx/processor stop}})

;; configuration with plugged-in component
(def system-config
  {:graph {:user {:first-name "John"
                  :last-name "Show"}
           :company {:name "Ice Wall inc."
                     :members '[(gx/ref :user)]}
           ;; initial counter value
           :init-counter 10
           ;; link as fully qualified symbol
           :counter {:gx/component 'readme-examples/my-counter-component
                     :gx/props {:counter '(gx/ref :init-counter)}}}})

(comment
  (gx/register! ::my-system system-config)

  ;; when you start a component, it begins to print "counting..."
  ;; every second
  @(gx/signal! ::my-system :gx/start)
  ;; suspended component stops printing, but it is ready to continue
  @(gx/signal! ::my-system :gx/suspend)
  ;; continues printing
  @(gx/signal! ::my-system :gx/resume)
  ;; stops completely
  @(gx/signal! ::my-system :gx/stop)

  (gx/values ::my-system)
  ;; =>
  ;; {:user {:first-name "John", :last-name "Show"},
  ;;  :company
  ;;  {:name "Ice Wall inc.", :members [{:first-name "John", :last-name "Show"}]},
  ;;  :init-counter 10,
  ;;  :counter #<Atom@69168527: {:stop? false, :suspended? false, :counter 14}>}
  )
```
# Contributing

## Build
VERSION=2.0.0-SNAPSHOT just build

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
