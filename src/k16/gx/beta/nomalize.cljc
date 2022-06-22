(ns k16.gx.beta.nomalize
  #?(:cljs (:require-macros
            [k16.gx.beta.context :refer [merge-err-ctx *ctx*]]))
  (:require [clojure.walk :as walk]
            [k16.gx.beta.errors :as gx.err]
            [k16.gx.beta.impl :as impl]
            [k16.gx.beta.schema :as gx.schema]
            #?(:clj [k16.gx.beta.context :refer [merge-err-ctx *ctx*]])))

(def locals #{'gx/ref 'gx/ref-keys})

(defn local-form?
  [form]
  (and (seq? form)
       (locals (first form))))

#?(:clj
   (defn quiet-requiring-resolve
     [sym]
     (try
       (requiring-resolve sym)
       (catch Throwable _ nil))))

(defn resolve-symbol
  [sym]
  (when (symbol? sym)
    #?(:cljs (impl/namespace-symbol sym)
       :clj (some-> sym
                    (impl/namespace-symbol)
                    (quiet-requiring-resolve)
                    (var-get)))))

(defn parse-local
  [env form]
  (condp = (first form)
    'gx/ref (get env (second form))

    'gx/ref-keys (select-keys env (second form))))

(defn postwalk-evaluate
  "A postwalk runtime signal processor evaluator, works most of the time.
  Doesn't support special symbols and macros, basically just function application.
  For cljs, consider compiled components or sci-evaluator, would require allowing
  for swappable evaluation stategies. Point to docs, to inform how to swap evaluator,
  or alternative ways to specify functions (that get compiled) that can be used."
  ([props form]
   (postwalk-evaluate props form true))
  ([props form parse-locals?]
   (walk/postwalk
    (fn [x]
      (cond
        (local-form? x)
        (if parse-locals?
          (parse-local props x)
          x)

        (and (seq? x) (ifn? (first x)))
        (apply (first x) (rest x))

        :else x))
    form)))

(def default-context
  {:initial-state :uninitialised
   :normalize {:form-evaluator postwalk-evaluate
               ;; signal, whish is default for static component nodes
               :auto-signal :gx/start
               :props-signals #{:gx/start}}
   :signal-mapping {}
   :signals {:gx/start {:from-states #{:stopped :uninitialised}
                        :to-state :started}
             :gx/stop {:from-states #{:started}
                       :to-state :stopped
                       ;; this is used as a sign of anti-signal and aplies
                       ;; it in reversed order
                       :deps-from :gx/start}}})

(defprotocol IRunnableForm
  (initialize [this])
  (run [this] [this props] [this props parse-locals?]))

(defrecord RunnableForm [context env form]
  IRunnableForm
  (initialize [this]
    ((-> this :context :normalize :form-evaluator) nil (:form this) false))
  (run [this props]
    ((-> this :context :normalize :form-evaluator) props (:form this))))

(defn runnable? [v]
  (instance? RunnableForm v))

(defn form->runnable
  ([context form-def]
   (form->runnable context form-def false))
  ([context form-def quiet?]
   (let [props* (atom #{})
         resolved-form
         (->> form-def
              (walk/postwalk
               (fn [sub-form]
                 (cond
                   (locals sub-form) sub-form

                   (local-form? sub-form)
                   (do (swap! props* concat (-> sub-form rest flatten))
                       sub-form)

                   (special-symbol? sub-form)
                   (gx.err/throw-gx-err "Special forms are not supported"
                                        {:form-def form-def
                                         :token sub-form})

                   (resolve-symbol sub-form) (resolve-symbol sub-form)

                   (and quiet? (symbol? sub-form)) nil

                   (symbol? sub-form)
                   (gx.err/throw-gx-err "Unable to resolve symbol"
                                        {:form-def form-def
                                         :token sub-form})

                   :else sub-form))))]
     (->RunnableForm context @props* resolved-form))))

(defn quiet-form->runnable
  [context form-def]
  (form->runnable context form-def true))

(defprotocol INodeNormalizer
  (normalize-node [this]))

(def normalizable? (partial satisfies? INodeNormalizer))

(defn empty-node-def
  [context]
  {:gx/value nil
   :gx/state (-> context :initial-state)})

(defrecord Noop [context node-def]
  INodeNormalizer
  (normalize-node [this] (:node-def this)))

(defrecord Signal [context node-def]
  INodeNormalizer
  (normalize-node [this]
    (let [runnable (form->runnable context (:node-def this))
          processor (fn auto-signal-processor [{:keys [props]}]
                      (run runnable props))
          deps (:env runnable)
          resolved-props (->> deps
                              (map (fn [dep] [dep (list 'gx/ref dep)]))
                              (into {}))
          normalized-signal {:gx/processor processor
                             :gx/deps deps
                             :gx/resolved-props resolved-props}]
      normalized-signal)))

(defrecord Static [context node-def]
  INodeNormalizer
  (normalize-node [{:keys [context node-def]}]
    (let [normalized-signal (normalize-node (->Signal context node-def))
          auto-signal (-> context :normalize :auto-signal)
          other-signals (-> context :signals keys set (disj auto-signal))]
      (->> other-signals
           (map (fn [other-signal] [other-signal {:gx/processor :value}]))
           (into {auto-signal normalized-signal})
           (merge (empty-node-def context))))))

(comment
  (->> {:port 8080}
       (->Static default-context)
       (normalize))

  (->> {:port '(gx/ref :z)}
       (->Static default-context)
       (normalize)))

(defn push-down-props
  [{{:keys [props-signals]} :normalize} {:gx/keys [props] :as node-def}]
  (if (and (seq props) (seq props-signals))
    (reduce-kv (fn [m k v]
                 (if (and (contains? props-signals k)
                          (not (:gx/props v)))
                   (assoc-in m [k :gx/props] props)
                   m))
               node-def
               node-def)
    node-def))

(defn init-deps
  [context component-def]
  (update-vals
   component-def
   (fn [{:gx/keys [props] :as signal-def}]
     (if props
       (let [{:keys [env form]} (some->> props (form->runnable context))]
         (merge signal-def {:gx/deps (set (or env []))
                            :gx/resolved-props form}))
       signal-def))))

(defn remap-signals
  [from-signals to-signals]
  (cond
    (and (seq from-signals) (seq to-signals))
    (if from-signals
      (->> to-signals
           (map (fn [[k v]]
                  [k (v from-signals)]))
           (into {}))
      to-signals)

    (seq from-signals) from-signals

    :else to-signals))

(defn flatten-component
  "Flattens nested components by creating one root component using
   signal mappings from context (if any)"
  [context root-component]
  (let [root-component (assoc root-component
                              :gx/signal-mapping
                              (or
                               (:gx/signal-mapping root-component)
                               (:signal-mapping context)))]
    (loop [{:gx/keys [component signal-mapping] :as current} root-component]
      (if-let [nested component]
        (recur (update nested :gx/signal-mapping
                       #(remap-signals % signal-mapping)))
        (if-let [mapping (seq (:gx/signal-mapping current))]
          (->> mapping
               (map (fn [[k v]]
                      [k (get current v)]))
               (into root-component))
          (dissoc current :gx/signal-mapping))))))

(defrecord Component [context node-def]
  INodeNormalizer
  (normalize-node [this]
    (merge-err-ctx {:error-type :normalize-node-component}
      (let [node-def (:node-def this)
            context (:context this)
            component-def (some->> node-def
                                   :gx/component
                                   (quiet-form->runnable context)
                                   (initialize)
                                   (flatten-component context))
            [issues schema component]
            (some->> component-def
                     (merge (dissoc node-def :gx/component))
                     (merge (empty-node-def context))
                     (push-down-props context)
                     (init-deps context)
                     (gx.schema/validate-component context))]
        (cond
          (not component)
          (gx.err/throw-gx-err "Component could not be resolved"
                               {:component node-def})

          issues
          (gx.err/throw-gx-err "Component schema error"
                               {:component component
                                :component-schema schema
                                :schema-error (set issues)})

          :else component)))))

(comment
  (def my-comp
    {:gx/start {:gx/processor identity
                :gx/props {:a '(gx/ref :a)}}
     :gx/stop {:gx/processor identity}})

  (normalize-single-node
   default-context
   [:c #:gx{:component 'k16.gx.beta.nomalize/test-component-2
            :props {:a '(gx/ref :a)}}])
  (let [{:keys [env form]}
        (->> #:gx{:component 'k16.gx.beta.nomalize/test-component-2
                  :props {:a '(gx/ref :a)}}
             (quiet-form->runnable default-context)
             #_(run))]
    (postwalk-evaluate nil form false))
  )

(defrecord ComponentConstructor [context node-def]
  INodeNormalizer
  (normalize-node [{:keys [context node-def]}]
    (let [component-def (update node-def :gx/component
                                #(->> %
                                      (quiet-form->runnable context)
                                      (initialize)))]
      (->Component context component-def))))

(comment
  (defn my-comp-2 [a]
    (println a)
    {:gx/start {:gx/processor (fn [_] a)}
     :gx/stop {:gx/processor identity}})

  (with-ctx {:ctx {:a 1}}
    (->> {:gx/component '(k16.gx.beta.nomalize/my-comp-2 (gx/ref :a))
          :gx/props {:foo '(gx/ref :a)}}
         (->ComponentConstructor default-context)
         (normalize-node)
        ;;  (normalize-node)
        ;;  :gx/component
        ;;  (quiet-form->runnable default-context)
        ;;  (run)
        ;;  (merge {:gx/props {:foo '(gx/ref :a)}})
        ;;  (->Component default-context)
        ;;  (normalize-node)
         #_(normalize-node))))

(defrecord InlineComponent [context node-def]
  INodeNormalizer
  (normalize-node [{:keys [context node-def]}]
    (merge (empty-node-def context)
           (update-vals node-def #(normalize-node (->Signal context %))))))

(defn function-call?
  [token]
  (and (list? token)
       (symbol? (first token))))

(defn component-type
  [{:gx/keys [component]}]
  (cond
    (not component) ::inline-component
    (symbol? component) ::component-def
    (function-call? component) ::component-constructor
    :else ::unsupported-component))

(defn normalized-node?
  [context node-def]
  (gx.schema/normalized? context node-def))

(defn node-type
  [context node-def]
  (cond
    (:normalized? node-def) ::normalized-node

    (when (map? node-def)
      (let [component-keys (conj (set (keys (:signals context)))
                                 :gx/component)
            node-keys (keys node-def)]
        (some component-keys node-keys))) (component-type node-def)

    :else ::static))

(normalized-node? default-context
                  #:gx{:start '(+ (gx/ref :z) 2),
                       :stop '(println "stopping")})

(defmulti create-normalizer node-type)

(defmethod create-normalizer ::component-def
  [context node-def]
  (->Component context node-def))

(defmethod create-normalizer ::component-constructor
  [context node-def]
  (->ComponentConstructor context node-def))

(defmethod create-normalizer ::inline-component
  [context node-def]
  (->InlineComponent context node-def))

(defmethod create-normalizer ::static
  [context node-def]
  (->Static context node-def))

(defmethod create-normalizer ::normalized-node
  [context node-def]
  (->Noop context node-def))

(defn normalize-single-node
  [context [node-key node-def]]
  (merge-err-ctx {:error-type :normalize-node
                  :node-key node-key
                  :node-contents node-def}
    [node-key (-> (create-normalizer context node-def)
                  (normalize-node)
                  (assoc :normalized? true))]))

(defn normalize-graph
  [{:keys [context graph] :as gx-map}]
  (merge-err-ctx {:error-type :normalize-node}
    (let [normalized (->> graph
                          (map (partial normalize-single-node context))
                          (into {}))]
      (assoc gx-map :graph normalized))))

(comment
  (def TestCoponentProps
    [:map [:a [:map [:nested-a pos-int?]]]])

;; this component is linked in fixtures/graphs.edn
  (def test-component
    {:gx/start {:gx/props-schema TestCoponentProps
                :gx/processor
                (fn [{:keys [props _value]}]
                  (let [a (:a props)]
                    (atom
                     (assoc a :nested-a-x2 (* 2 (:nested-a a))))))}})

  (def test-component-2
    {:gx/start {:gx/props-schema TestCoponentProps
                :gx/props {:a '(gx/ref :a)}
                :gx/processor
                (fn [{:keys [props _value]}]
                  (let [a (:a props)]
                    (atom
                     (assoc a :some-value (+ 2 (:nested-a a))))))}})

  (def graph {:a {:nested-a 1},
              :z '(get (gx/ref :a) :nested-a),
              :y '(println "starting"),
              :b #:gx{:start '(+ (gx/ref :z) 2), :stop '(println "stopping")},
              :c #:gx{:component 'k16.gx.beta.nomalize/test-component},
              :x #:gx{:component 'k16.gx.beta.nomalize/test-component-2
                      :props {:a '(gx/ref :b)}}})
  (normalize-graph {:context default-context
                    :graph graph})

  (normalize-single-node
   default-context
   [:c #:gx{:component 'k16.gx.beta.nomalize/test-component
            :props {:a '(gx/ref :a)}}])

  (node-type default-context #:gx{:component 'k16.gx.beta.nomalize/test-component
                                  :props {:a '(gx/ref :b)}})

  (let [gx-map {:context default-context :graph graph}
        context default-context
        normalized (->> graph
                        (map (partial normalize-single-node context))
                        (into {}))]
    (assoc gx-map
           :graph normalized
           :initial-graph graph))

  (normalize-single-node
   default-context [:a {:nested-a 1}])

  (normalize-single-node
   default-context [:z '(get (gx/ref :a) :nested-a)])

  (normalize-single-node
   default-context [:b #:gx{:start '(+ (gx/ref :z) 2),
                            :stop '(println "stopping")}])

  (node-type default-context
             #:gx{:start '(+ (gx/ref :z) 2),
                  :stop '(println "stopping")})

  (node-type default-context '(get (gx/ref :a) :nested-a))

  (normalize-node
   (->Static default-context '(get (gx/ref :a) :nested-a)))

  (type
   (create-normalizer default-context
                      #:gx{:component 'k16.gx.beta.nomalize/test-component}))
  )