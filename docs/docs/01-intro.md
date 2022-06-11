---
id: intro
title: Introduction to GX
sidebar_label: "Introduction"
slug: /
---
![GX Banner](/img/banner.png)
# Introduction

GX is data driven directed acyclic graph of state machines with configurable signals and nodes for Clojure(Scipt).

## Status

In preview, many things can be changed.

## Install

Leiningen:
```clojure
[kepler16/gx.cljc "<version>"]
```

Deps:
```clojure
{:kepler16/gx.cljc {:mvn/version "<version>"}}
```
## Usage

To start using GX you need two things:
- **Graph** - where nodes of our particular graph are defined
- **Graph Context** - where signals, normalisation hints and other config is defined

### Graph Configuration

**Config** is a simple map with **signals**. Here we define two signals `:my/start` and `:my/stop`:

```clojure
(ns user
 (:require [k16.gx.beta.core :as gx]))

(def graph-config
  {:signals {:my/start {:order :topological
                        :from-states #{:stopped gx/INITIAL_STATE}
                        :to-state :started}
             :my/stop {:order :reverse-topological
                       :from-states #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})
```

Every signal is a map with the following keys:

- `:order` type of signal flow, topological/reverse topological (see examples below)
- `:from-states` a set of states in the graph on which this signal can be called, the initial state is `:uninitialized` and defined as constant in the core namespace (`INITIAL_STATE`)
- `:to-state` the state of the node after the signal is successfully handled
- `:deps-from` this field is used if the signal's dependencies should be copied from another signal

There must be one (and only one) signal, which runs on `from-state = INITIAL_STATE`. It is called a **startup signal**. In our case its `:my/start`.

## Graph

The **Graph** is a plain Clojure map with defined nodes on the root level. Here we create a graph of three nodes. Node value can be any data structure, primitive value, function call, or **gx reference** `gx/ref`:

```clojure
(def fancy-graph
  {:user/data {:name "Angron"
               :also-named "Red Angel"
               :spoken-language "Nagrakali"
               :side :chaos}
   :user/name '(get (gx/ref :user/data) :name)
   :user/lang '(get (gx/ref :user/data) :spoken-language)})
```

 Here we have a static node `:user/data` and two dependent nodes `:user/name` and `:user/lang`. The next step is **normalization**:

 ```clojure
 (def normalized (gx/normalize graph-config fancy-graph))
 ```
 This step is not mandatory since every signal call normalizes unnormalized nodes.
Normalization is a process of converting your graph to a state machine where each node becomes a signal receiver:
 ```clojure
#:user{:data
       ;; startup signal definition
       {:my/start
        #:gx{;; signal processor
             :processor <...>/auto-signal-processor,
             ;; signal dependencies
             :deps #{},
             ;; signal resolved props
             ;; resolved props recalculated on each signal call
             :resolved-props {}},
        ;; current node state
        :gx/state :uninitialized,
        ;; current node value
        :gx/value nil,
        ;; type of node
        :gx/type :static,
        ;; normalization flag
        :gx/normalized? true},
       :name
       {:my/start
        #:gx{:processor <...>/auto-signal-processor,
             :deps #{:user/data},
             :resolved-props #:user{:data (gx/ref :user/data)}},
        :gx/state :uninitialized,
        :gx/value nil,
        :gx/type :static,
        :gx/normalized? true},
       :lang
       {:my/start
        #:gx{:processor <...>/auto-signal-processor,
             :deps #{:user/data},
             :resolved-props #:user{:data (gx/ref :user/data)}},
        :gx/state :uninitialized,
        :gx/value nil,
        :gx/type :static,
        :gx/normalized? true}}
 ```
Now every node is in a normalized state. It has **startup** signal `:my/start` but not `:my/stop`, because we didn't define any signals on nodes. And node without signals becomes `:gx/type = :static` with **startup** signal only.
Next, we send a signal to our graph by calling `gx/signal`. Signals run asynchronously (using [funcool/promesa](https://github.com/funcool/promesa)):
```clojure
(def started @(gx/signal graph-config fancy-graph :my/start))
```
Value in the `started` variable is a normalized graph structure with the new state. GX itself does not store graphs, it simply returns new graphs on every signal. Managing graph stores should happen on the application side.
Here are some utility functions to view the internals of the graph:
```clojure
(gx/system-value started)
;; => #:user{:data
;;           {:name "Angron",
;;            :also-named "Red Angel",
;;            :spoken-language "Nagrakali",
;;            :side :chaos},
;;           :name "Angron",
;;           :lang "Nagrakali"}

(gx/system-state started)
;; => #:user{:data :started, :name :started, :lang :started}

;; this function shows sequence of given signal flow
(gx/topo-sort graph-config started :my/start)
;; => (:user/data :user/name :user/lang)

;; :my/stop is configured with :reverse-topological order
;; stop dependend graph leafs first, then move upwards
(gx/topo-sort graph-config started :my/stop)
;; => (:user/lang :user/name :user/data)

;; shows dependencies of graph nodes for given signal
(gx/graph-dependencies started :my/start)
;; => #:user{:data #{}, :name #{:user/data}, :lang #{:user/data}}

;; :my/stop have no dependencies, all nodes does not handle this signal
(gx/graph-dependencies started :my/stop)
;; => #:user{:data #{}, :name #{}, :lang #{}}
```

[Tutorial](/example-app) contains a more practical example.

