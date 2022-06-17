(ns k16.gx.beta.system-test
  (:require [clojure.test :refer [deftest is] :as t]
            [k16.gx.beta.system :as gx.system]))

(def server
  {:gx/start {:gx/processor (fn [_] {:server "instance"})}
   :gx/stop {:gx/processor (fn [_] nil)}})

(def config
  {:http/options {:port 8080}
   :http/server {:gx/component 'k16.gx.beta.system-test/server}})

#?(:clj
   (deftest system-test
     (do
       (gx.system/register! ::clj-system {:graph config})
       (gx.system/signal! ::clj-system :gx/start)
       (is (= #:http{:options {:port 8080}, :server {:server "instance"}}
              (gx.system/values ::clj-system))))))

#?(:cljs
   (deftest system-test
     (do
       (gx.system/register! ::clj-system {:graph config})
       (.then
        (gx.system/signal! ::clj-system :gx/start)
        (fn [_]
          (is (= #:http{:options {:port 8080}, :server {:server "instance"}}
                 (gx.system/values ::clj-system))))))))