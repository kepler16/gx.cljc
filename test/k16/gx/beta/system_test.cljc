(ns k16.gx.beta.system-test
  (:require [clojure.test :refer [deftest is]]
            [test-utils :refer [test-async]]
            [k16.gx.beta.system :as gx.system]))

(def server
  {:gx/start {:gx/processor (fn [_] {:server "instance"})}
   :gx/stop {:gx/processor (fn [_] nil)}})

(def config
  {:http/options {:port 8080}
   :http/server {:gx/component 'k16.gx.beta.system-test/server
                 :gx/props '(gx/ref :http/options)}})

(gx.system/register! ::clj-system {:graph config})

(deftest system-test
  (test-async
   (gx.system/signal! ::clj-system :gx/start)
   (fn [_]
     (is (= #:http{:options {:port 8080}, :server {:server "instance"}}
            (gx.system/values ::clj-system))))))

(deftest system-node-filter-test
  (test-async
   (gx.system/signal! ::clj-system :gx/start)
   (fn [_]
     (is (= #:http{:options {:port 8080}}
            (gx.system/values ::clj-system [:http/options]))))))

(deftest failed-system-test
  (gx.system/register! ::clj-system-err {:graph {:a '(gx/ref :b)
                                                 :b '(gx/ref :a)
                                                 :c '(gx/ref :z)}})
  (test-async
   (gx.system/signal! ::clj-system-err :gx/start)
   (fn [_ err]
     (let [error #?(:clj (.getCause err) :cljs err)]
       (is (= {:failures
               (str "Dependency errors: signal = ':gx/start'"
                    "\n\t• :c depends on :z, but :z doesn't exist"
                    "\n\t• circular :a -> :b -> :a")}
              (ex-data error)))))))


(comment
 @(gx.system/signal! ::clj-system-err :gx/start)
 (fn [_ err]
   (let [error #?(:clj (.getCause err) :cljs err)]
     (is (= {:failures
             (list (str "Dependency errors: signal = ':gx/start'"
                        "\n\t• :c depends on :z, but :z doesn't exist"
                        "\n\t• circular :a -> :b -> :a"))}
            (ex-data error))))))
