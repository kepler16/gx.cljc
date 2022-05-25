---
id: intro
title: Introduction to GX
sidebar_label: "Introduction"
slug: /
---
![GX Banner](/img/banner.png)
# Introduction

GX is data driven directed acyclic graph state machine with configurable signals and nodes for Clojure(Scipt).

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
- Graph configuration (**config**) - contains signal definitions
- Graph itself - contains nodes of our state machine
### Graph Configuration

**Config** is a clojure map with **signals**. Here we define two signals `:fancy/start` and `:fancy/stop`:

```clojure
(ns user
 (:require [k16.gx.beta.core :as gx]))

(def graph-config
  {:signals {:fancy/start {:order :topological
                           :from-states #{:stopped gx/INITIAL_STATE}
                           :to-state :started}
             :fancy/stop {:order :reverse-topological
                          :from-states #{:started}
                          :to-state :stopped
                          :deps-from :gx/start}}})
```

Every signal is map with following keys:

- `:order` type of signal flow, topological/reverse topological (see below)
- `:from-states` a set of states in graph on which this signal can be called, initlal state is `:uninitialized` and stored as constant in core namespace (`INITIAL_STATE`)
- `:to-state` in what state node should be after signal successifully handled
- `:deps-from` use this field if signal's dependencies should be copied from another signal

There must be one (and only one) signal, which runs on `from-state = INITIAL_STATE`. It is called **startup signal**. In our case its `:fancy/start`.
## Graph

**Graph** is a plain clojure map with defined nodes on root level. Here we create graph of three nodes. Node value can be any data structure, primitive value, function call or gx reference `gx/ref`:

```clojure
(def fancy-graph
  {:user/data {:name "Angron"
               :also-named "Red Angel"
               :spoken-language "Nagrakali"
               :side :chaos}
   :user/name (get (gx/ref :user/data) :name)
   :user/lang (get (gx/ref :user/data) :spoken-language)})
```

 Here we have static node `:user/data` and two dependend nodes `:user/name` and `:user/lang`. The next step is **normalization**:

 ```clojure
 (def normalized (gx/normalize graph-config fancy-graph))
 ```
 And `normalized` is looks like this:
 ```clojure
#:user{:data
       #:gx{:start
            #:gx{:processor
                 k16.gx.beta.core/normalize-signal-def/auto-signal-processor
                 :deps #{}
                 :resolved-props {}}
            :state :uninitialized
            :value nil
            :type :static
            :normalized? true}
       :name
       #:gx{:start
            #:gx{:processor
                 k16.gx.beta.core/normalize-signal-def/auto-signal-processor
                 :deps #{:user/data}
                 :resolved-props #:user{:data (gx/ref :user/data)}}
            :state :uninitialized
            :value nil
            :type :static
            :normalized? true}
       :lang
       #:gx{:start
            #:gx{:processor
                 k16.gx.beta.core/normalize-signal-def/auto-signal-processor
                 :deps #{:user/data}
                 :resolved-props #:user{:data (gx/ref :user/data)}}
            :state :uninitialized
            :value nil
            :type :static
            :normalized? true}}
 ```
Now every node is in normalized state. It has **startup** signal `:gx/start` but not `:gx/stop`. Its because we didn't define any signals on nodes. And node is without signal becomes `:gx/type = :static` with **startup** signal only.

Next we need to send signal to our graph by calling `gx/signal`:
```clojure
(def started @(gx/signal graph-config fancy-graph :gx/start))
```

to be continued...
