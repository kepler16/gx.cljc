## Malli Validation

A Malli schema compatible graph preprocessor is provided as part of the `k16.gx.malli` namespace which adds support for
defining Malli schemas instead of validating functions.

This preprocessor will traverse the gx graph to find components with defined Malli schemas, replacing them with gx
validating functions instead.

For example:

```clojure
(ns example.malli
  (:require
   [k16.gx :as gx]
   [k16.gx.malli :as gx.malli]))

(def echo-component
  {:props-schema [:map
                  [:value :int]]
   :signals {:start {:handler (fn [_ props] true)
                     :result-schema :boolean}}})

(def graph
  ;; The `with-validation` preprocessor will generate gx validating functions for malli schemas
  (gx.malli/with-validation
    {:a 1

     :c1 (gx/component
          {:value (gx/ref :a)}
          echo-component)}))

(gx/signal! graph :start)
```
