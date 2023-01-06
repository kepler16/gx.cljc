(ns k16.gx.beta.impl
  (:refer-clojure :exclude [ref])
  #?(:cljs (:require-macros [k16.gx.beta.error-context :refer [with-err-ctx]]))
  (:require [clojure.walk :as walk]
            [k16.gx.beta.errors :as gx.err]
            [k16.gx.beta.schema :as gx.schema]
            #?(:cljs [clojure.string :as string])
            #?(:cljs [k16.gx.beta.registry :as gx.reg])
            #?(:clj [k16.gx.beta.error-context :refer [with-err-ctx]])))

(defn sccs
  "Returns a topologically sorted list of strongly connected components.
  Tarjan's algorithm."
  ([g] (sccs g []))
  ([g sccs-init]
   (let [strong-connect
         (fn strong-connect [acc v]
           (let [acc (-> acc
                         (assoc-in [:idxs v] (:idx acc))
                         (assoc-in [:low-links v] (:idx acc))
                         (update :idx inc)
                         (update :S conj v)
                         (assoc-in [:on-stack v] true))
                 acc (reduce
                      (fn [acc w]
                        (cond
                          (not (get-in acc [:idxs w]))
                          (let [acc (strong-connect acc w)]
                            (update-in acc
                                       [:low-links v]
                                       min
                                       (get-in acc [:low-links w])))
                          (get-in acc [:on-stack w])
                          (update-in acc
                                     [:low-links v]
                                     min
                                     (get-in acc [:idxs w]))
                          :else acc))
                      acc
                      (get g v))]
             (if (= (get-in acc [:idxs v]) (get-in acc [:low-links v]))
               (let [[S on-stack scc] (loop [S (:S acc)
                                             on-stack (:on-stack acc)
                                             scc #{}]
                                        (let [w (peek S)
                                              S (pop S)
                                              on-stack (dissoc on-stack w)
                                              scc (conj scc w)]
                                          (if (= v w)
                                            [S on-stack scc]
                                            (recur S on-stack scc))))]
                 (-> acc
                     (assoc :S S
                            :on-stack on-stack)
                     (update :sccs conj scc)))
               acc)))]
     (:sccs
      (reduce (fn [acc v]
                (if-not (contains? (:idxs acc) v) (strong-connect acc v) acc))
              {:S ()
               :idx 0
               :sccs sccs-init}
              (keys g))))))

(defn cycles
  [sccs g]
  (filter #(or (>= (count %) 2)
               (get-in g [(first %) (first %)]))
          sccs))

(defn dependency-errors
  [g sccs]
  (concat
   (mapcat
    (fn [[k v]]
      (seq
       (map (fn [does-not-exist]
              {:type :missing
               :from k
               :to does-not-exist})
            (remove #(contains? g %) v))))
    g)
   (map (fn [cycle] {:type :cycle :between cycle})
        (cycles sccs g))))

(defn human-render-dependency-error
  [dependency-error]
  (case (:type dependency-error)
    :missing
    (str (:from dependency-error) " depends on " (:to dependency-error)
         ", but " (:to dependency-error) " doesn't exist")

    :cycle
    (str "circular "
         (apply str (interpose
                     " -> "
                     (concat
                      (reverse (:between dependency-error))
                      [(first (reverse (:between dependency-error)))]))))

    (pr-str dependency-error)))

#?(:cljs (defn resolve-exported-symbol
           [sym-str]
           (let [path (-> sym-str
                          (string/replace #"-" "_")
                          (string/replace #"/" ".")
                          (string/split #"\."))]
             (loop [p path
                    obj goog.global]
               (if (and (seq p) obj)
                 (recur (rest p) (aget obj (first p)))
                 obj)))))

#?(:cljs (defn sym->js-resolve [sym]
           (let [ssym (str sym)]
             (or (get @gx.reg/registry* ssym)
                 (resolve-exported-symbol ssym)))))

(defn namespace-symbol
  "Returns symbol unchanged if it has a namespace, or with clojure.core as it's
  namespace otherwise."
  [sym]
  (cond
    (namespace sym)
    #?(:clj sym :cljs (sym->js-resolve sym))

    :else
    #?(:clj (symbol "clojure.core" (name sym))
       :cljs ((ns-publics 'cljs.core) sym))))

(def mergable? (every-pred map? (complement record?)))

(defn merger
  [left right]
  (if (mergable? left right)
    (merge-with merger left right)
    (or right left)))

(defn deep-merge
  "Recursively merges maps."
  [& maps]
  (reduce merger maps))

(def locals #{'gx/ref 'gx/ref-keys})

(defn local-form?
  [form]
  (and (seq? form)
       (locals (first form))))

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
  [props form]
  (walk/postwalk
   (fn [x]
     (cond
       (local-form? x)
       (parse-local props x)

       (and (seq? x) (ifn? (first x)))
       (try
         (apply (first x) (rest x))
         (catch #?(:clj Throwable :cljs :default) e
           (gx.err/throw-gx-err
             (str "Form evaluate error: " form) {:props props} e)))

       :else x))
   form))

(defn resolve-symbol
  [sym]
  (when (symbol? sym)
    (if-let [nss #?(:cljs (namespace-symbol sym)
                    :clj (try
                           (some-> sym
                                   (namespace-symbol)
                                   (requiring-resolve)
                                   (var-get))
                           (catch Throwable e
                             (gx.err/add-err-cause
                              {:title :symbol-cannot-be-resolved
                               :data sym
                               :exception e}))))]
      nss
      (gx.err/add-err-cause {:title :symbol-cannot-be-resolved
                             :data sym}))))

(defn form->runnable [form-def]
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

                  (symbol? sub-form)
                  (gx.err/throw-gx-err "Unable to resolve symbol"
                                       {:form-def form-def
                                        :token sub-form})

                  :else sub-form))))]
    {:env @props*
     :form resolved-form}))

(defn push-down-props
  [{{:keys [props-signals]} :normalize} {:gx/keys [props] :as node-def}]
  (if (and props (seq props-signals))
    (reduce-kv (fn [m k v]
                 (if (and (contains? props-signals k)
                          (not (:gx/props v)))
                   (assoc-in m [k :gx/props] props)
                   m))
               node-def
               node-def)
    node-def))

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

(defn resolve-component
  "Resolve component by it's symbol and validate against malli schema"
  [context component]
  (when component
    (with-err-ctx {:error-type :normalize-node-component}
      (let [resolved (some->> component
                              (resolve-symbol)
                              (flatten-component context))
            [issues schema] (when resolved
                              (gx.schema/validate-component context resolved))]
        (cond
          (not resolved)
          (gx.err/throw-gx-err "Component could not be resolved"
                               {:component component})

          issues
          (gx.err/throw-gx-err "Component schema error"
                               {:component resolved
                                :component-schema schema
                                :schema-error (set issues)})

          :else resolved)))))
