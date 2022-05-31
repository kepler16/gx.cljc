(ns k16.gx.beta.core-test
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.registry :as gx.reg :include-macros true]
            [k16.gx.beta.schema :as gxs]
            [malli.core :as m]
            [malli.error :as me]
            #?(:clj [clojure.test :as t :refer [deftest is testing]])
            #?@(:cljs [[cljs.test :as t :refer-macros [deftest is testing]]
                       [promesa.core :as p]
                       [k16.gx.beta.impl :as impl]])))

(def TestCoponentProps
  [:map [:a [:map [:nested-a pos-int?]]]])

;; this component is linked in fixtures/graphs.edn
(def test-component
  {:gx/start {:gx/props-schema TestCoponentProps
              :gx/props {:a (gx/ref :a)}
              :gx/processor
              (fn [{:keys [props _value]}]
                (let [a (:a props)]
                  (atom
                   (assoc a :nested-a-x2 (* 2 (:nested-a a))))))}
   :gx/stop {:gx/processor (fn [{:keys [_props value]}]
                             nil)}})

(def test-component-2
  {:gx/start {:gx/props-schema TestCoponentProps
              :gx/props {:a (gx/ref :a)}
              :gx/processor
              (fn [{:keys [props _value]}]
                (let [a (:a props)]
                  (atom
                   (assoc a :some-value (+ 2 (:nested-a a))))))}
   :gx/stop {:gx/processor (fn [{:keys [_props value]}]
                             nil)}})

(defn load-config []
  (gx.reg/load-graph! "test/fixtures/graph.edn"))

(def context
  {:signals {:gx/start {:order :topological
                        :from-states #{:stopped gx/INITIAL_STATE}
                        :to-state :started}
             :gx/stop {:order :reverse-topological
                       :from-states #{:started}
                       :to-state :stopped
                       :deps-from :gx/start}}})

(deftest graph-tests
  (let [run-checks
        (fn [gx-started gx-stopped]
          (testing "graph should start correctly"
            (is (= (gx/system-state gx-started)
                   {:a :started, :z :started, :y :started,
                    :b :started :c :started :x :started})
                "all nodes should be started")
            (is (= (dissoc (gx/system-value gx-started) :c :x)
                   {:a {:nested-a 1}, :z 1, :y nil, :b 3}))

            (is (= @(:c (gx/system-value gx-started))
                   {:nested-a 1, :nested-a-x2 2}))

            (is (= @(:x (gx/system-value gx-started))
                   {:nested-a 1, :some-value 3})))

          (testing "graph should stop correctly, nodes without signal handler
                    should not change state and value"
            (is (= (gx/system-state gx-stopped)
                   {:a :started,
                    :z :started,
                    :y :started,
                    :b :stopped,
                    :c :stopped,
                    :x :stopped})
                "all nodes should be stopped")
            (is (= (gx/system-value gx-stopped)
                   {:a {:nested-a 1}, :z 1, :y nil, :b nil :c nil :x nil}))))
        graph (load-config)
        gx-map (gx/normalize {:context context
                              :graph graph})]
    (testing "normalization structure should be valid"
      (is
       (m/validate gxs/?NormalizedGraphDefinition (:graph gx-map))
       (me/humanize
        (m/explain gxs/?NormalizedGraphDefinition (:graph gx-map)))))

    (testing "topo sorting"
      (is (= (gx/topo-sort gx-map :gx/start)
             '(:a :z :y :b :c :x))
          "should be topologically")
      (is (= (gx/topo-sort gx-map :gx/stop)
             '(:x :c :b :y :z :a))
          "should be reverse-topologically"))

    #?(:clj (let [gx-started @(gx/signal gx-map :gx/start)
                  gx-stopped @(gx/signal gx-started :gx/stop)]
              (run-checks gx-started gx-stopped))

       :cljs (t/async
              done
              (p/let [gx-started (gx/signal gx-map :gx/start)
                      gx-stopped (gx/signal gx-started :gx/stop)]
                (run-checks gx-started gx-stopped)
                (done))))))

(deftest failed-normalization-test
  (let [custom-context {:signals
                        {:custom/start {:order :topological
                                        :from-states #{:stopped :uninitialized}
                                        :to-state :started}
                         :custom/stop {:order :reverse-topological
                                       :from-states #{:started}
                                       :to-state :stopped
                                       :deps-from :gx/start}}}
        graph {:a {:nested-a 1}
               :z '(get (gx/ref :a) :nested-a)
               :y '(println "starting")
               :d '(throw (ex-info "foo" (gx/ref :a)))
               :b {:custom/start '(+ (gx/ref :z) 2)
                   :custom/stop '(println "stopping")}}
        gx-norm (gx/normalize {:context custom-context
                               :graph graph})]
    (is (:failures gx-norm))
    (is (-> gx-norm :failures first :message)
        "Special forms are not supported 'throw'")
    (is (-> gx-norm :failures first :data)
        {:data {:type :parse-error :form '(throw "starting") :expr 'throw}})))

(deftest component-support-test
  (let [run-checks (fn [gx-started gx-stopped]
                     (is (= (gx/system-state gx-started)
                            {:a :started, :c :started}))
                     (is (= (:a (gx/system-value gx-started))
                            {:nested-a 1}))
                     (is (= @(:c (gx/system-value gx-started))
                            {:nested-a 1, :nested-a-x2 2}))

                     (is (= (gx/system-state gx-stopped)
                            {:a :started, :c :stopped}))
                     (is (= (gx/system-value gx-stopped)
                            {:a {:nested-a 1} :c nil})))
        graph {:a {:nested-a 1}
               :c {:gx/component 'k16.gx.beta.core-test/test-component}}
        gx-map (gx/normalize {:context context
                              :graph graph})]
    #?(:clj (let [gx-started @(gx/signal gx-map :gx/start)
                  gx-stopped @(gx/signal gx-started :gx/stop)]
              (run-checks gx-started gx-stopped))
       :cljs (t/async
              done
              (p/let [gx-started (gx/signal gx-map :gx/start)
                      gx-stopped (gx/signal gx-started :gx/stop)]
                (run-checks gx-started gx-stopped)
                (done))))))

(deftest subsequent-normalizations-test
  (let [gx-norm-1 (gx/normalize {:context context
                              :graph (load-config)})
        gx-norm-2 (gx/normalize gx-norm-1)
        gx-norm-3 (gx/normalize gx-norm-2)]
    (testing "normalization should add :gx/normalized? flag"
      (is (= #{true} (set (map :gx/normalized? (vals (:graph gx-norm-1)))))))
    (testing "all graphs should be equal"
      (is (= gx-norm-1 gx-norm-2 gx-norm-3)))
    (testing "should normalize and flag new node in graph "
      (let [new-gx (assoc-in gx-norm-3 [:graph :new-node] '(* 4 (gx/ref :z)))
            new-gx-norm (gx/normalize new-gx)]
        (is (:gx/normalized? (:new-node (:graph new-gx-norm))))))))

(deftest cyclic-dependency-test
  (let [graph {:a (gx/ref :b)
               :b (gx/ref :a)}
        gx-norm (gx/normalize {:context context
                               :graph graph})]
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error)
         #"(\"There's a circular dependency between :a -> :b -> :a\")"
         (gx/signal gx-norm :gx/start)))))

(defn my-props-fn
  [{:keys [a]}]
  (assoc a :full-name
         (str (:name a) " " (:last-name a))))

(def my-new-component
  {:gx/start {:gx/props (gx/ref :a)
              :gx/processor
              (fn my-new-component-handler
                [{:keys [props]}]
                (atom props))}})

(deftest props-fn-test
  (let [run-checks
        (fn [gx-started]
          (is (= @(:comp (gx/system-value gx-started))
                 {:name "John" :last-name "Doe" :full-name "John Doe"})))
        graph (gx.reg/load-graph! "test/fixtures/props_fn.edn")
        gx-map {:context context :graph graph}
        started (gx/signal gx-map :gx/start)]
    #?(:clj (run-checks @started)
       :cljs (t/async done (-> started (p/then (fn [s]
                                                 (run-checks s)
                                                 (done))))))))

(deftest postwalk-evaluate-test
  (let [env {:http/server {:port 8080}
             :db/url "jdbc://foo/bar/baz"}]

    (t/are [arg result] (= result (gx/postwalk-evaluate env arg))
      (gx/ref :http/server) {:port 8080}

      (gx/ref-map :http/server) #:http{:server {:port 8080}}

      (gx/ref-path :http/server :port) 8080

      (gx/ref-maps :http/server :db/url) {:http/server {:port 8080}
                                          :db/url "jdbc://foo/bar/baz"})))

#?(:cljs
   (deftest globaly-registered-components-test
     ;; load graph, scan for symbols, populate registry
     (gx.reg/load-graph! "test/fixtures/props_fn.edn")
     (t/are [js-v v] (= v js-v)
       (impl/sym->js-resolve 'k16.gx.beta.core-test/test-component)
       test-component

       (impl/sym->js-resolve 'k16.gx.beta.core-test/test-component-2)
       test-component-2

       (impl/sym->js-resolve 'k16.gx.beta.core-test/my-new-component)
       my-new-component

       (impl/sym->js-resolve 'k16.gx.beta.core-test/my-props-fn)
       my-props-fn)))

(deftest failures-test
  (testing "special forms is not supported"
    (let [graph {:a {:nested-a 1}
                 :z '(get (gx/ref :a) :nested-a)
                 :d '(throw "starting")
                 :b {:gx/start '(+ (gx/ref :z) 2)
                     :gx/stop '(println "stopping")}}
          gx-norm (gx/normalize {:graph graph
                                 :context gx/default-context})]
      (is (= (-> gx-norm :failures first)
             {:message "Special forms are not supported",
              :data
              {:internal-data {:form-def '(throw "starting"), :token 'throw},
               :error-type :normalize-node,
               :node-key :d,
               :node-value '(throw "starting")}}))))

  (testing "unresolved symbol failure"
    (let [graph {:a {:nested-a 1}
                 :z '(get (gx/ref :a) :nested-a)
                 :d '(println "starting")
                 :b {:gx/start '(+ (gx/ref :z) 2)
                     :gx/stop '(some-not-found-symbol "stopping")}}
          gx-norm (gx/normalize {:graph graph
                                 :context gx/default-context})]
      (is (= (-> gx-norm :failures first)
             {:message "Unable to resolve symbol",
              :data
              {:internal-data
               {:form-def '(some-not-found-symbol "stopping"),
                :token 'some-not-found-symbol},
               :error-type :normalize-node,
               :node-key :b,
               :node-value
               #:gx{:start '(+ (gx/ref :z) 2),
                    :stop '(some-not-found-symbol "stopping")}}}))))

  (testing "processor failure"
    (let [graph {:a {:nested-a 1}
                 :z '(get (gx/ref :a) :nested-a)
                 :d '(println "starting")
                 :c '(inc :bar)
                 :b {:gx/start '(+ (gx/ref :z) :foo)
                     :gx/stop '(println "stopping")}}
          gx-norm (gx/normalize {:graph graph
                                 :context gx/default-context})
          err-msg (str "class clojure.lang.Keyword cannot be cast to "
                       "class java.lang.Number (clojure.lang.Keyword "
                       "is in unnamed module of loader 'app'; "
                       "java.lang.Number is in module java.base of "
                       "loader 'bootstrap')")
          expect (list {:internal-data
                        {:ex-message err-msg,
                         :args {:props {:z 1}, :value nil}},
                        :message "Signal processor error",
                        :error-type :node-signal,
                        :node-key :b,
                        :node-value #:gx{:start '(+ (gx/ref :z) :foo),
                                         :stop '(println "stopping")},
                        :signal-key :gx/start}
                       {:internal-data
                        {:ex-message err-msg,
                         :args {:props {}, :value nil}},
                        :message "Signal processor error",
                        :error-type :node-signal,
                        :node-key :c,
                        :node-value '(inc :bar),
                        :signal-key :gx/start})]
      #?(:clj (is (= (-> @(gx/signal gx-norm :gx/start) :failures)
                     expect))
         :cljs (t/async
                done
                (-> (gx/signal gx-norm :gx/start)
                    (p/then #((is (= % expect)) (done)))))))))
