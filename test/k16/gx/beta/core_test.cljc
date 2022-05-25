(ns k16.gx.beta.core-test
  (:require [clojure.edn :as edn]
            [k16.gx.beta.core :as gx]
            [k16.gx.beta.registry :refer [defcomponent]]
            [k16.gx.beta.schema :as gxs]
            [malli.core :as m]
            [malli.error :as me]
            #?(:clj [clojure.test :as t :refer [deftest is testing]])
            #?@(:cljs [[cljs.test :as t :refer-macros [deftest is testing]]
                       [test-utils :refer [slurp]]
                       [promesa.core :as p]])))

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
  (let [run-checks
        (fn [started stopped]
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
                   {:a :started,
                    :z :started,
                    :y :started,
                    :b :stopped,
                    :c :stopped,
                    :x :stopped})
                "all nodes should be stopped")
            (is (= (gx/system-value stopped)
                   {:a {:nested-a 1}, :z 1, :y nil, :b nil :c nil :x nil}))))
        graph (load-config)
        normalized (gx/normalize-graph graph-config graph)]
    (testing "normalization structure should be valid"
      (is
       (m/validate gxs/?NormalizedGraphDefinition normalized)
       (me/humanize
        (m/explain gxs/?NormalizedGraphDefinition normalized))))

    (testing "topo sorting"
      (is (= (gx/topo-sort graph-config normalized :gx/start)
             '(:a :z :y :b :c :x))
          "should be topologically")
      (is (= (gx/topo-sort graph-config normalized :gx/stop)
             '(:x :c :b :y :z :a))
          "should be reverse-topologically"))

    #?(:clj (let [started @(gx/signal graph-config normalized :gx/start)
                  stopped @(gx/signal graph-config started :gx/stop)]
              (run-checks started stopped))

       :cljs (t/async
              done
              (p/let [started (gx/signal graph-config normalized :gx/start)
                      stopped (gx/signal graph-config started :gx/stop)]
                (run-checks started stopped)
                (done))))))

(deftest failed-normalization-test
  (let [custom-graph-config {:signals
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
         (gx/normalize-graph custom-graph-config config)))))

(deftest component-support-test
  (let [run-checks (fn [started stopped]
                     (is (= (gx/system-state started)
                            {:a :started, :c :started}))
                     (is (= (:a (gx/system-value started))
                            {:nested-a 1}))
                     (is (= @(:c (gx/system-value started))
                            {:nested-a 1, :nested-a-x2 2}))

                     (is (= (gx/system-state stopped)
                            {:a :started, :c :stopped}))
                     (is (= (gx/system-value stopped)
                            {:a {:nested-a 1} :c nil})))
        graph {:a {:nested-a 1}
               :c {:gx/component 'k16.gx.beta.core-test/test-component}}
        normalized (gx/normalize-graph graph-config graph)]
    #?(:clj (let [started @(gx/signal graph-config normalized :gx/start)
                  stopped @(gx/signal graph-config started :gx/stop)]
              (run-checks started stopped))
       :cljs (t/async
              done
              (p/let [started (gx/signal graph-config normalized :gx/start)
                      stopped (gx/signal graph-config started :gx/stop)]
                (run-checks started stopped)
                (done))))))

(deftest subsequent-normalizations-test
  (let [norm-1 (gx/normalize-graph graph-config (load-config))
        norm-2 (gx/normalize-graph graph-config norm-1)
        norm-3 (gx/normalize-graph graph-config norm-2)]
    (testing "normalization should add :gx/normalized? flag"
      (is (= #{true} (set (map :gx/normalized? (vals norm-1))))))
    (testing "all graphs should be equal"
      (is (= norm-1 norm-2 norm-3)))
    (testing "should normalize and flag new node in graph "
      (let [new-graph (assoc norm-3 :new-node '(* 4 (gx/ref :z)))
            new-norm (gx/normalize-graph graph-config new-graph)]
        (is (:gx/normalized? (:new-node new-norm)))))))
