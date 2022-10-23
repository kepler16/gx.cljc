(ns k16.gx.beta.core-test
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.registry :as gx.reg :include-macros true]
            [k16.gx.beta.schema :as gx.schema]
            [k16.gx.beta.impl :as impl]
            [test-utils :refer [test-async]]
            [promesa.core :as p]
            [clojure.test :as t :refer [deftest is testing]]))

(def TestCoponentProps
  [:map [:a [:map [:nested-a pos-int?]]]])

;; this component is linked in fixtures/graphs.edn
(def ^:export test-component
  {:gx/start {:gx/props-schema TestCoponentProps
              :gx/props {:a '(gx/ref :a)}
              :gx/processor
              (fn [{:keys [props _value]}]
                (let [a (:a props)]
                  (atom
                   (assoc a :nested-a-x2 (* 2 (:nested-a a))))))}})

(def ^:export test-component-2
  {:gx/start {:gx/props-schema TestCoponentProps
              :gx/props {:a '(gx/ref :a)}
              :gx/processor
              (fn [{:keys [props _value]}]
                (let [a (:a props)]
                  (atom
                   (assoc a :some-value (+ 2 (:nested-a a))))))}})

(defn load-config []
  (gx.reg/load-graph! "test/fixtures/graph.edn"))

(def context gx/default-context)

(comment
  (let [graph (load-config)
        gx-map (gx/normalize {:context context
                              :graph graph})]
    (gx.schema/validate-graph gx-map))
  )

(deftest graph-tests
  (let [graph (load-config)
        gx-map (gx/normalize {:context context
                              :graph graph})]
    (testing "normalization structure should be valid"
      (is (nil? (gx.schema/validate-graph gx-map))
          (gx.schema/validate-graph gx-map)))

    (testing "topo sorting"
      (is (= '(:a :z :y :b :c :x)
             (second (gx/topo-sort gx-map :gx/start)))
          "should be topologically")
      (is (= '(:x :c :b :y :z :a)
             (second (gx/topo-sort gx-map :gx/stop)))
          "should be reverse-topologically"))

    (test-async
     (p/let [gx-started (gx/signal gx-map :gx/start)
             gx-stopped (gx/signal gx-started :gx/stop)]
       [gx-started gx-stopped])
     (fn [[gx-started gx-stopped]]
       (testing "graph should start correctly"
         (is (= {:a :started, :z :started, :y :started,
                 :b :started :c :started :x :started}
                (gx/states gx-started))
             "all nodes should be started")
         (is (= {:a {:nested-a 1}, :z 1, :y nil, :b 3}
                (dissoc (gx/values gx-started) :c :x)))

         (is (= {:nested-a 1, :nested-a-x2 2}
                @(:c (gx/values gx-started))))

         (is (= {:nested-a 1, :some-value 3}
                @(:x (gx/values gx-started)))))

       (testing "graph should stop correctly, nodes without signal handler
                    should not change value but state"
         (is (= {:a :stopped
                 :z :stopped
                 :y :stopped
                 :b :stopped,
                 :c :stopped,
                 :x :stopped}
                (gx/states gx-stopped))
             "all nodes should be stopped")
         (is (= {:a {:nested-a 1}, :z 1, :y nil, :b nil}
                (select-keys (gx/values gx-stopped)
                             [:a :z :y :b])))
         (is (= {:nested-a 1, :nested-a-x2 2}
                @(:c (gx/values gx-stopped))))
         (is (= {:nested-a 1, :some-value 3}
                @(:x (gx/values gx-stopped)))))))))

(deftest failed-normalization-test
  (let [custom-context {:initial-state :uninitialised
                        :normalize {:auto-signal :custom/start
                                    :props-signals #{:custom/start}}
                        :signals
                        {:custom/start {:from-states #{:stopped :uninitialized}
                                        :to-state :started}
                         :custom/stop {:from-states #{:started}
                                       :to-state :stopped
                                       :deps-from :custom/start}}}
        graph {:a {:nested-a 1}
               :z '(get (gx/ref :a) :nested-a)
               :y '(println "starting")
               :d '(throw (ex-info "foo" (gx/ref :a)))
               :b {:custom/start '(+ (gx/ref :z) 2)
                   :custom/stop '(println "stopping")}}
        gx-norm (gx/normalize {:context custom-context
                               :graph graph})
        failure (-> gx-norm :failures first)]
    (is (= {:error-type :normalize-node,
            :node-key :d,
            :node-contents '(throw (ex-info "foo" (gx/ref :a))),
            :message "Special forms are not supported",
            :internal-data
            {:form-def '(throw (ex-info "foo" (gx/ref :a))), :token 'throw}}
           failure))))

(deftest component-support-test
  (let [graph {:a {:nested-a 1}
               :c {:gx/component 'k16.gx.beta.core-test/test-component}}
        gx-map (gx/normalize {:context context
                              :graph graph})]
    (test-async
     (p/let [gx-started (gx/signal gx-map :gx/start)
             gx-stopped (gx/signal gx-started :gx/stop)]
       [gx-started gx-stopped])
     (fn [[gx-started gx-stopped]]
       (is (= {:a :started, :c :started}
              (gx/states gx-started)))
       (is (= {:nested-a 1}
              (:a (gx/values gx-started))))
       (is (= {:nested-a 1, :nested-a-x2 2}
              @(:c (gx/values gx-started))))
       (is (= {:a :stopped, :c :stopped}
              (gx/states gx-stopped)))
       (is (= {:nested-a 1}
              (:a (gx/values gx-stopped))))
       (is (= {:nested-a 1, :nested-a-x2 2}
              @(:c (gx/values gx-stopped))))))))

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

(deftest dependency-error-test
  (let [graph {:a '(gx/ref :b)
               :b '(gx/ref :a)
               :c '(gx/ref :z)}
        gx-norm (gx/normalize {:context context
                               :graph graph})
        failure (-> (gx/topo-sort gx-norm :gx/start) first)]
    (is (= {:internal-data
            {:errors '(":c depends on :z, but :z doesn't exist"
                       "circular :a -> :b -> :a")},
            :message "Dependency errors",
            :error-type :deps-sort,
            :signal-key :gx/start}
           failure))))

(defn my-props-fn
  [{:keys [a]}]
  (assoc a :full-name
         (str (:name a) " " (:last-name a))))

(def my-new-component
  {:gx/start {:gx/props '(gx/ref :a)
              :gx/processor
              (fn my-new-component-handler
                [{:keys [props]}]
                (atom props))}})

(deftest props-fn-test
  (let [graph (gx.reg/load-graph! "test/fixtures/props_fn.edn")
        gx-map {:context context :graph graph}
        started (gx/signal gx-map :gx/start)]
    (test-async
     started
     (fn [gx-started]
       (is (= @(:comp (gx/values gx-started))
              {:name "John" :last-name "Doe" :full-name "John Doe"}))))))

(deftest postwalk-evaluate-test
  (let [env {:http/server {:port 8080}
             :db/url "jdbc://foo/bar/baz"}]

    (t/are [arg result] (= result (impl/postwalk-evaluate env arg))
      '(gx/ref :http/server) {:port 8080}

      '(gx/ref-keys [:http/server :db/url]) {:http/server {:port 8080}
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
      (is (= {:error-type :normalize-node,
              :node-key :d,
              :node-contents '(throw "starting"),
              :message "Special forms are not supported",
              :internal-data {:form-def '(throw "starting"), :token 'throw}}
             (-> gx-norm :failures first)))))

  (testing "unresolved symbol failure"
    (let [graph {:a {:nested-a 1}
                 :z '(get (gx/ref :a) :nested-a)
                 :d '(println "starting")
                 :b {:gx/start '(+ (gx/ref :z) 2)
                     :gx/stop '(some-not-found-symbol "stopping")}}
          gx-norm (gx/normalize {:graph graph
                                 :context gx/default-context})
          failure (-> gx-norm :failures first)]
      (is (= {:error-type :normalize-node,
              :node-key :b,
              :node-contents #:gx{:start '(+ (gx/ref :z) 2),
                                  :stop '(some-not-found-symbol "stopping")},
              :message "Unable to resolve symbol",
              :internal-data
              {:form-def '(some-not-found-symbol "stopping"),
               :token 'some-not-found-symbol}}
             failure))))
  #?(:clj
     (testing "processor failure"
       (let [graph {:a {:nested-a 1}
                    :z '(get (gx/ref :a) :nested-a)
                    :d '(println "starting")
                    :c '(inc :bar)
                    :b {:gx/start '(/ (gx/ref :z) 0)
                        :gx/stop '(println "stopping")}}
             gx-norm (gx/normalize {:graph graph
                                    :context gx/default-context})
             expect (list {:internal-data
                           {:ex-message "Divide by zero",
                            :args {:props {:z 1}, :value nil, :state :uninitialised, :instance nil}},
                           :message "Signal processor error",
                           :error-type :node-signal,
                           :node-key :b,
                           :node-contents #:gx{:start '(/ (gx/ref :z) 0),
                                               :stop '(println "stopping")},
                           :signal-key :gx/start}
                          {:internal-data
                           {:ex-message
                            (str "class clojure.lang.Keyword cannot be cast"
                                 " to class java.lang.Number (clojure.lang."
                                 "Keyword is in unnamed module of loader "
                                 "'app'; java.lang.Number is in module "
                                 "java.base of loader 'bootstrap')"),
                            :args {:props {}, :value nil, :state :uninitialised, :instance nil}},
                           :message "Signal processor error",
                           :error-type :node-signal,
                           :node-key :c,
                           :node-contents '(inc :bar),
                           :signal-key :gx/start})
             p-gx-started (gx/signal gx-norm :gx/start)]
         (is (= expect
                (->> @p-gx-started
                     :failures
                     (map #(update % :internal-data dissoc :ex)))))))))

(def props-validation-component
  {:gx/start {:gx/props '(gx/ref :a)
              :gx/props-schema [:map [:foo string?]]
              :gx/processor
              (fn my-new-component-handler
                [{:keys [props]}]
                (atom props))}})

(deftest props-validation-test
  (let [graph (gx.reg/load-graph! "test/fixtures/props_validation.edn")
        gx-map {:context context :graph graph}]
    (test-async
     (gx/signal gx-map :gx/start)
     (fn [gx-started]
       (is (= {:internal-data
               {:props-value {:name "John", :last-name "Doe", :full-name "John Doe"},
                :props-schema [:map [:foo string?]],
                :schema-error {:foo ["missing required key"]}},
               :message "Props validation error",
               :error-type :props-validation,
               :node-key :comp,
               :node-contents
               #:gx{:component 'k16.gx.beta.core-test/props-validation-component,
                    :start #:gx{:props-fn 'k16.gx.beta.core-test/my-props-fn}},
               :signal-key :gx/start}
              (first (:failures gx-started))))))))

(comment
  (let [graph {:a 1
               :b '(/ (gx/ref :a) 0)
               :c '(gx/ref :b)
               :d '(gx/ref :c)}
        gx-map {:graph graph
                :context gx/default-context}]
    (gx/normalize gx-map))
  )

#?(:clj
   (deftest dependency-node-failures-test
     (let [graph {:a 1
                  :b '(/ (gx/ref :a) 0)
                  :c '(gx/ref :b)
                  :d '(gx/ref :c)}
           gx-map {:graph graph
                   :context gx/default-context}
           expect (list {:internal-data {:dep-node-keys '(:c)},
                         :message "Failure in dependencies",
                         :error-type :node-signal,
                         :node-key :d,
                         :node-contents '(gx/ref :c),
                         :signal-key :gx/start}
                        {:internal-data {:dep-node-keys '(:b)},
                         :message "Failure in dependencies",
                         :error-type :node-signal,
                         :node-key :c,
                         :node-contents '(gx/ref :b),
                         :signal-key :gx/start}
                        {:internal-data
                         {:ex-message "Divide by zero",
                          :args {:props {:a 1}, :state :uninitialised, :value nil, :instance nil}},
                         :message "Signal processor error",
                         :error-type :node-signal,
                         :node-key :b,
                         :node-contents '(/ (gx/ref :a) 0),
                         :signal-key :gx/start})
           p-gx-started (gx/signal gx-map :gx/start)
           failures (-> (:failures @p-gx-started)
                        (vec)
                        (update-in [2 :internal-data] dissoc :ex))]
       (is (= expect failures)))))

#?(:cljs
   (deftest processor-failure-test
     (let [graph {:a '(get)}]
       (test-async
        (gx/signal {:graph graph} :gx/start)
        (fn [gx-map]
          (is (= {:internal-data
                  {:ex-message "Invalid arity: 0",
                   :args {:props {}, :value nil,
                          :instance nil,  :state :uninitialised}},
                  :message "Signal processor error",
                  :error-type :node-signal, :node-key :a,
                  :node-contents '(get),
                  :signal-key :gx/start}
                 (-> (:failures gx-map)
                     (first)
                     (update :internal-data dissoc :ex)))))))))

(def ^:export push-down-props-component
  {:gx/props '(gx/ref-keys [:a])
   :gx/start {:gx/processor :props}
   :gx/stop {:gx/processor :props
             :gx/props '(gx/ref :a)}})

(deftest push-down-props-test
  (let [graph {:a {:name "Lion El'Johnson"}
               :c {:gx/component
                   'k16.gx.beta.core-test/push-down-props-component}}
        gx-map (gx/normalize {:graph graph
                              :context context})
        node-def (-> gx-map :graph :c)]
    (testing "should push down props to signals without props"
      (is (= '(gx/ref-keys [:a])
             (-> node-def :gx/start :gx/props))))
    (testing "should use signal's own props if any"
      (is (= '(gx/ref :a)
             (-> node-def :gx/stop :gx/props))))))

(def ^:export invalid-component
  {:some "invalid component"})

(def ^:export invalid-component-2
  {:gx/start {:gx/processor "non callable val"}})

(deftest component-processor-unresolved-test
  (testing "should resolve all components during normalization stage"
    (let [graph {:c {:gx/component 'k16.gx.beta.core-test/non-existent}}
          gx-map (gx/normalize {:graph graph
                                :context context})]
      (is (= {:message "Component could not be resolved",
              :error-type :normalize-node-component,
              :node-key :c,
              :node-contents
              #:gx{:component 'k16.gx.beta.core-test/non-existent}
              :internal-data
              {:component 'k16.gx.beta.core-test/non-existent}}
             (first (:failures gx-map)))))

    (let [graph {:c {:gx/component 'k16.gx.beta.core-test/invalid-component}}
          gx-map (gx/normalize {:graph graph
                                :context context})]
      (is (= {:message "Component schema error",
              :error-type :normalize-node-component,
              :node-key :c,
              :node-contents
              #:gx{:component 'k16.gx.beta.core-test/invalid-component}
              :internal-data
              {:component {:some "invalid component"}
               :schema-error #{[:some ["disallowed key"]]}}}
             (-> gx-map
                 :failures
                 first
                 (update :internal-data dissoc :component-schema)))))

    (let [graph {:c {:gx/component 'k16.gx.beta.core-test/invalid-component-2}}
          gx-map (gx/normalize {:graph graph
                                :context context})]
      (is (= {:message "Component schema error",
              :error-type :normalize-node-component,
              :node-key :c,
              :node-contents
              #:gx{:component 'k16.gx.beta.core-test/invalid-component-2},
              :internal-data
              {:component #:gx{:start #:gx{:processor "non callable val"}}
               :schema-error
               #{[:gx/start
                  #:gx{:processor ["should be an ifn"]}]}}}
             (-> gx-map
                 :failures
                 first
                 (update :internal-data dissoc :component-schema)))))))

(def ^:export server-component
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              (swap! (:flow props) conj :server))}
   :gx/stop {:gx/processor (fn [{:keys [props]}]
                             (swap! (:flow props) conj :server)
                             nil)}})

(def ^:export db-component
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              (swap! (:flow props) conj :db))}
   :gx/stop {:gx/processor (fn [{:keys [props]}]
                             (swap! (:flow props) conj :db)
                             nil)}})

(def ^:export logger-component
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              (swap! (:flow props) conj :logger))}
   :gx/stop {:gx/processor (fn [{:keys [props]}]
                             (swap! (:flow props) conj :logger)
                             nil)}})

(deftest signal-selector-test
  (let [graph {:options {:port 8080}
               :router {:some "router"}
               :handler {:router '(gx/ref :router)}
               :server {:opts '(gx/ref :options)
                        :handler '(gx/ref :handler)}
               :config-logger {:some "logger config"}
               :logger {:config '(gx/ref :config-logger)}}

        norm (gx/normalize {:graph graph})]

    (is (= [:logger :config-logger] (gx/selector-with-deps norm :gx/start :logger)))

    (test-async
     (p/let [started (gx/signal {:graph graph} :gx/start)
             partial-stop (gx/signal started :gx/stop #{:handler})
             partial-start (gx/signal partial-stop :gx/start #{:handler})
             full-start (gx/signal partial-start :gx/start #{:server})]
       [started partial-stop partial-start full-start])
     (fn [[started partial-stop partial-start full-start]]
       (is (= (gx/states started)
              {:options :started,
               :router :started,
               :handler :started,
               :server :started,
               :config-logger :started,
               :logger :started}))

       (is (= (gx/states partial-stop)
              {:options :started,
               :router :started,
               :handler :stopped,
               :server :stopped,
               :config-logger :started,
               :logger :started}))

       (is (= (gx/states partial-start)
              {:options :started,
               :router :started,
               :handler :started,
               :server :stopped,
               :config-logger :started,
               :logger :started}))

       (is (= (gx/states full-start)
              {:options :started,
               :router :started,
               :handler :started,
               :server :started,
               :config-logger :started,
               :logger :started}))))))

(deftest validate-context-test
  (let [context {:initial-state :uninitialised
                 :normalize {:auto-signal :gx/start
                             :props-signals #{:gx/start}}
                 :signal-mapping {}
                 :signals {:gx/start {:from-states #{:stopped :uninitialised}
                                      :to-state :started
                                      :deps-from :gx/stop}
                           :gx/stop {:from-states #{:started}
                                     :to-state :stopped
                                     :deps-from :gx/start}}}]
    (testing "should check for circular deps-from"
      (is (= "circular :gx/start -> :gx/stop -> :gx/start"
             (-> (gx/with-context-failures {:context context})
                 :failures
                 (first)
                 :internal-data
                 :errors
                 first))))))

(deftest unserolvable-symbol-test
  (let [graph {:a 'foo.bar/baz}
        norm (gx/normalize {:graph graph})]
    (is (= {:message "Unable to resolve symbol",
            :error-type :normalize-node,
            :node-key :a,
            :node-contents 'foo.bar/baz,
            :internal-data {:form-def 'foo.bar/baz, :token 'foo.bar/baz}}
           (first (:failures norm))))))

(def ^:export proc-instance-component
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              {:gx/value props
                               :gx/instance :some-instance})}
   :gx/stop {:gx/processor (fn [{:keys [instance]}]
                             (when-not (= :some-instance instance)
                               (throw (ex-info "wrong instance" instance)))
                             :stopped-val)}})

(deftest instance-test
  (let [graph {:my-comp {:gx/component 'k16.gx.beta.core-test/proc-instance-component
                         :gx/props {:foo 1}}}]
    (test-async
     (p/let [started (gx/signal {:graph graph} :gx/start)
             stopped (gx/signal started :gx/stop)]
       [started stopped])
     (fn [[started stopped]]
       (is (= {:my-comp {:foo 1}} (gx/values started)))
       (is (= {:my-comp :stopped-val} (gx/values stopped)))
       (is (= :some-instance (-> started :graph :my-comp :gx/instance)))
       (is (= nil (-> stopped :graph :my-comp :gx/instance)))))))

(def ^:export empty-map-props-component
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              {:gx/value props
                               :gx/instance :some-instance})
              :gx/props-schema [:map]}
   :gx/stop {:gx/processor (fn [{:keys [instance]}]
                             (when-not (= :some-instance instance)
                               (throw (ex-info "wrong instance" instance)))
                             :stopped-val)}})

(deftest empty-map-props-test
  (let [graph {:my-comp {:gx/component 'k16.gx.beta.core-test/empty-map-props-component
                         :gx/props {}}}]
    (test-async
     (p/let [started (gx/signal {:graph graph} :gx/start)
             stopped (gx/signal started :gx/stop)]
       [started stopped])
     (fn [[started stopped]]
       (is (= {:my-comp nil} (gx/failures started)))
       (is (= {:my-comp {}} (gx/values started)))
       (is (= {} (-> stopped :graph :my-comp :gx/props)))))))

