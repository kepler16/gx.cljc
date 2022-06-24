(ns k16.gx.beta.nested-components-test
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.normalize :as gx.norm]
            #?(:clj [clojure.test :as t :refer [deftest is]])
            #?@(:cljs [[cljs.test :as t :refer-macros [deftest is]]])))

(def ^:export nested-level-2
  {:l2/start {:gx/processor (fn [_] :hello-from-nested-level-2)}
   :l2/stop {:gx/processor identity}})

(def ^:export nested-level-1
  {:gx/component nested-level-2
   :gx/signal-mapping {:l1/start :l2/start
                       :l1/stop :l2/stop}})

(def ^:export root
  {:gx/component nested-level-1})

(deftest nested-component-resolve-test
  (let [resolved (gx.norm/normalize-sm'
                  (assoc gx/default-context
                         :signal-mapping
                         {:gx/start :l1/start
                          :gx/stop :l1/stop})
                  {:gx/component 'k16.gx.beta.nested-components-test/root})]
    (is (= :hello-from-nested-level-2
           (-> resolved :gx/start :gx/processor (apply [nil]))))))
