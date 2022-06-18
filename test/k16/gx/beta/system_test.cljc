(ns k16.gx.beta.system-test
  (:require [clojure.test :refer [deftest is use-fixtures] :as t]
            [k16.gx.beta.system :as gx.system]))

(def server
  {:gx/start {:gx/processor (fn [_] {:server "instance"})}
   :gx/stop {:gx/processor (fn [_] nil)}})

(def config
  {:http/options {:port 8080}
   :http/server {:gx/component 'k16.gx.beta.system-test/server
                 :gx/props '(gx/ref :http/options)}})

(use-fixtures
  :each (fn [f]
          (gx.system/register! ::clj-system {:graph config})
          #?@(:clj [(gx.system/signal! ::clj-system :gx/start)
                    (f)]
              :cljs [(.then (gx.system/signal! ::clj-system :gx/start)
                            (fn [_] (f)))])))

(deftest system-test
  (is (= #:http{:options {:port 8080}, :server {:server "instance"}}
         (gx.system/values ::clj-system))))

(deftest system-node-filter-test
  (is (= #:http{:options {:port 8080}}
         (gx.system/values ::clj-system [:http/options]))))