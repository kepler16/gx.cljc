(ns k16.gx.beta.core-test
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.schema :as gxs]
            [clojure.edn :as edn]
            [malli.core :as m]
            [malli.error :as me]
            #?(:clj [clojure.test :refer [deftest testing is]])
            #?(:cljs [cljs.test :refer-macros [deftest is testing]])
            #?(:cljs [test-utils :refer [node-slurp]])))

(defn load-config []
  (let [path "test/fixtures/graphs.edn"]
    (edn/read-string (#?(:clj slurp :cljs node-slurp) path))))

(def graph-config
  {:signals {:gx/start {:order :topological
                        :from-state #{:stopped :uninitialized}
                        :to-state :started}
             :gx/stop {:order :reverse-topological
                       :from-state #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})

(comment
  (let [config (load-config)
        normalized (gx/normalize-graph config)]
    (gx/normalize-graph config))
  nil)

(deftest graph-tests
  (let [config (load-config)
        normalized (gx/normalize-graph config)]
    (testing "normalization structure should be valid"
      (is
       (m/validate gxs/?NormalizedGraphDefinition normalized)
       (me/humanize
        (m/explain gxs/?NormalizedGraphDefinition normalized))))

    (testing "topo sorting"
      (is (= (gx/topo-sort normalized :gx/start graph-config)
             '(:a :z :y :b))
          "should be topologically")
      (is (= (gx/topo-sort normalized :gx/stop graph-config)
             '(:b :y :z :a))
          "should be reverse-topologically"))

    (let [started (gx/signal normalized :gx/start graph-config)
          stopped (gx/signal started :gx/stop graph-config)]

      (testing "graph should start correctly"
        (is (= (gx/system-state started)
               {:a :started, :z :started, :y :started, :b :started})
            "all nodes should be started")
        (is (= (gx/system-value started)
               {:a {:nested-a 1}, :z 1, :y nil, :b 3})))

      (testing "graph should stop correctly"
        (is (= (gx/system-state stopped)
               {:a :stopped, :z :stopped, :y :stopped, :b :stopped})
            "all nodes should be stopped")
        (is (= (gx/system-value stopped)
               {:a nil, :z nil, :y nil, :b nil}))))))
