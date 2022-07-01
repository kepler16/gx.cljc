(ns k16.gx.beta.system-test
  (:require #?(:cljs [cljs.core :refer [ExceptionInfo]])
            [clojure.test :refer [deftest is]]
            [k16.gx.beta.system :as gx.system]
            [test-utils :refer [test-async]])
  #?(:clj (:import [clojure.lang ExceptionInfo])))

(def ^:export server
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
  (try
    (gx.system/register! ::clj-system-err {:graph {:a '(gx/ref :b)
                                                   :b '(gx/ref :a)
                                                   :c '(gx/ref :z)}})
    (catch ExceptionInfo err
      (is (= {:failures
              [{:internal-data
                {:errors
                 [":c depends on :z, but :z doesn't exist"
                  "circular :a -> :b -> :a"]},
                :message "Dependency errors",
                :error-type :deps-sort,
                :signal-key :gx/start}
               {:internal-data
                {:errors
                 [":c depends on :z, but :z doesn't exist"
                  "circular :a -> :b -> :a"]},
                :message "Dependency errors",
                :error-type :deps-sort,
                :signal-key :gx/suspend}
               {:internal-data
                {:errors
                 [":c depends on :z, but :z doesn't exist"
                  "circular :a -> :b -> :a"]},
                :message "Dependency errors",
                :error-type :deps-sort,
                :signal-key :gx/resume}
               {:internal-data
                {:errors
                 [":c depends on :z, but :z doesn't exist"
                  "circular :a -> :b -> :a"]},
                :message "Dependency errors",
                :error-type :deps-sort,
                :signal-key :gx/stop}]}
             (ex-data err))))))

