(ns k16.gx.beta.core-test
  (:require [clojure.edn :as edn]
            [k16.gx.beta.core :as gx]
            [k16.gx.beta.schema :as gxs]
            [malli.core :as m]
            [malli.error :as me]
            [k16.gx.beta.registry :refer [defcomponent]]
            #?(:clj [clojure.test :refer [deftest is testing]])
            #?@(:cljs [[cljs.test :refer-macros [deftest is testing]]
                       [test-utils :refer [slurp]]])))

(def TestCoponentProps
  [:map [:a [:map [:nested-a pos-int?]]]])

;; this component is linked in fixtures/graphs.edn
(defcomponent test-component
  {:gx/start {:gx/props-schema TestCoponentProps
              :gx/props {:a '(gx/ref :a)}
              :gx/processor
              (fn [{:keys [props _value]}]
                (let [a (:a props)]
                  (atom
                   (assoc a :nested-a-x2 (* 2 (:nested-a a))))))}
   :gx/stop {:gx/processor (fn [{:keys [_props value]}]
                          nil)}})

(defcomponent test-component-2
  {:gx/start {:gx/props-schema TestCoponentProps
              :gx/props {:a '(gx/ref :a)}
              :gx/processor
              (fn [{:keys [props _value]}]
                (let [a (:a props)]
                  (atom
                   (assoc a :some-value (+ 2 (:nested-a a))))))}
   :gx/stop {:gx/processor (fn [{:keys [_props value]}]
                          nil)}})

(defn load-config []
  (let [path "test/fixtures/graphs.edn"]
    (edn/read-string (slurp path))))

(def graph-config
  {:signals {:gx/start {:order :topological
                        :from-state #{:stopped gx/INITIAL_STATE}
                        :to-state :started}
             :gx/stop {:order :reverse-topological
                       :from-state #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})

(deftest graph-tests
  (let [graph (load-config)
        normalized (gx/normalize-graph graph graph-config)]
    (testing "normalization structure should be valid"
      (is
       (m/validate gxs/?NormalizedGraphDefinition normalized)
       (me/humanize
        (m/explain gxs/?NormalizedGraphDefinition normalized))))

    (testing "topo sorting"
      (is (= (gx/topo-sort normalized :gx/start graph-config)
             '(:a :z :y :b :c :x))
          "should be topologically")
      (is (= (gx/topo-sort normalized :gx/stop graph-config)
             '(:x :c :b :y :z :a))
          "should be reverse-topologically"))

    (let [started (gx/signal normalized :gx/start graph-config)
          stopped (gx/signal started :gx/stop graph-config)]

      (testing "graph should start correctly"
        (is (= (gx/system-state started)
               {:a :started, :z :started, :y :started,
                :b :started :c :started :x :started})
            "all nodes should be started")
        (is (= (dissoc (gx/system-value started) :c :x)
               {:a {:nested-a 1}, :z 1, :y nil, :b 3}))

        (is (= @(:c (gx/system-value started))
               {:nested-a 1, :nested-a-x2 2}))

        (is (= @(:x (gx/system-value started))
               {:nested-a 1, :some-value 3})))

      (testing "graph should stop correctly, nodes without signal handler
                should not change state and value"
        (is (= (gx/system-state stopped)
               {:a :stopped, :z :stopped, :y :stopped,
                :b :stopped :c :stopped :x :stopped})
            "all nodes should be stopped")
        (is (= (gx/system-value stopped)
               {:a {:nested-a 1}, :z 1, :y nil, :b nil :c nil :x nil}))))))

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
         #"Special forms are not supported 'throw'"
         (gx/normalize-graph config custom-config)))))

(deftest component-support-test
  (let [graph {:a {:nested-a 1}
               :c {:gx/component 'k16.gx.beta.core-test/test-component}}
        norm (gx/normalize-graph graph graph-config)
        started (gx/signal norm :gx/start graph-config)
        stopped (gx/signal started :gx/stop graph-config)]
    (is (= (gx/system-state started)
           {:a :started, :c :started}))
    (is (= (:a (gx/system-value started))
           {:nested-a 1}))
    (is (= @(:c (gx/system-value started))
           {:nested-a 1, :nested-a-x2 2}))

    (is (= (gx/system-state stopped)
           {:a :stopped, :c :stopped}))
    (is (= (gx/system-value stopped)
           {:a {:nested-a 1} :c nil}))))
