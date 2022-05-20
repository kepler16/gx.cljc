(ns k16.gx.beta.core-test
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.schema :as gxs]
            [clojure.edn :as edn]
            [malli.core :as m]
            [malli.error :as me]
            #?(:clj [clojure.test :refer [deftest testing is]])
            #?(:cljs [cljs.test :refer-macros [deftest is testing]])
            #?(:cljs [test-utils :refer [slurp]])))

(defn load-config []
  (let [path "test/fixtures/graphs.edn"]
    (edn/read-string (slurp path))))

(def graph-config
  {:signals {:gx/start {:order :topological
                        :from-state #{:stopped :uninitialized}
                        :to-state :started}
             :gx/stop {:order :reverse-topological
                       :from-state #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})

(deftest graph-tests
  (let [config (load-config)
        normalized (gx/normalize-graph config graph-config)]
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

      (testing "graph should stop correctly, nodes without signal handler
                should not change state and value"
        (is (= (gx/system-state stopped)
               {:a :started, :z :started, :y :started, :b :stopped})
            "all nodes should be stopped")
        (is (= (gx/system-value stopped)
               {:a {:nested-a 1}, :z 1, :y nil, :b nil}))))))

;; TODO: should we support special forms inside config e.g. throw?
;; currently throws error
(deftest failed-normalization-test
  (let [custom-config {:signals
                       {:custom/start {:order :topological
                                       :from-state #{:stopped :uninitialized}
                                       :to-state :started}
                        :custom/stop {:order :reverse-topological
                                      :from-state #{:started}
                                      :to-state :stopped
                                      :deps-from :gx/start}}}
        config {:a {:nested-a 1}
                :z '(get (gx/ref :a) :nested-a)
                :y '(println "starting")
                :d '(throw (ex-info "foo" (gx/ref :a)))
                :b {:custom/start '(+ (gx/ref :z) 2)
                    :custom/stop '(println "stopping")}}]
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error)
         #"Unable to evaluate form 'throw'"
         (gx/normalize-graph config custom-config)))))
