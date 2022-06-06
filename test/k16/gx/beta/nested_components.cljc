(ns k16.gx.beta.nested-components
  (:require [k16.gx.beta.core :as gx]
            #?(:clj [clojure.test :as t :refer [deftest is]])
            #?@(:cljs [[cljs.test :as t :refer-macros [deftest is]]])))

(def nested-level-2
  {:l2/start {:gx/processor identity}
   :l2/stop {:gx/processor identity}})

(def nested-level-1
  {:gx/component nested-level-2
   :gx/signal-mapping {:l1/start :l2/start
                       :l1/stop :l2/stop}})

(def root
  {:gx/component nested-level-1})

(comment
  (let [context (assoc gx/default-context
                       :signal-mapping
                       {:gx/start :l1/start
                        :gx/stop :l1/stop})]
    (gx/flatten-component context root))
  ;; => #:gx{:start #:gx{:processor #function[clojure.core/identity]},
  ;;         :stop #:gx{:processor #function[clojure.core/identity]}}
)

(deftest nested-component-resolve-test
  (let [resolved (gx/resolve-component (assoc gx/default-context
                                              :signal-mapping
                                              {:gx/start :l1/start
                                               :gx/stop :l1/stop})
                                       'k16.gx.beta.nested-components/root)]
    (is (= #:gx{:start #:gx{:processor identity},
                :stop #:gx{:processor identity}}
           resolved))))
