(ns k16.gx.beta.core-test
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.schema :as gxs]
            [malli.core :as m]
            [malli.error :as me]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(defn load-config []
  (read-string
   (slurp
    (io/file "test/fixtures/graphs.edn"))))

(def graph-config
  {:signals {:gx/start {:order :topological
                        :from-state #{:stopped :uninitialized}
                        :to-state :started}
             :gx/stop {:order :reverse-topological
                       :from-state #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})

(t/deftest graph-tests
  (let [config (load-config)
        normalized (gx/normalize-graph config)]
    (t/testing "normalization structure should be valid"
      (t/is
       (m/validate gxs/?NormalizedGraphDefinition normalized)
       (me/humanize
        (m/explain gxs/?NormalizedGraphDefinition normalized))))

    (t/testing "topo sorting"
      (t/is (= (gx/topo-sort normalized :gx/start graph-config)
               '(:a :z :y :b))
            "should be topologically")
      (t/is (= (gx/topo-sort normalized :gx/stop graph-config)
               '(:b :y :z :a))
            "should be reverse-topologically"))

    (let [started (gx/signal normalized :gx/start graph-config)
          stopped (gx/signal started :gx/stop graph-config)]

      (t/testing "graph should start correctly"
        (t/is (= (gx/system-state started)
                 {:a :started, :z :started, :y :started, :b :started})
              "all nodes should be started")
        (t/is (= (gx/system-value started)
                 {:a {:nested-a 1}, :z 1, :y nil, :b 3})))

      (t/testing "graph should stop correctly"
        (t/is (= (gx/system-state stopped)
                 {:a :stopped, :z :stopped, :y :stopped, :b :stopped})
              "all nodes should be stopped")
        (t/is (= (gx/system-value stopped)
                 {:a nil, :z nil, :y nil, :b nil}))))))
