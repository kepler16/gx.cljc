(ns k16.gx.ref
  (:refer-clojure :exclude [ref])
  (:require
   [clojure.core.protocols :as p]
   [clojure.set :as set]
   [clojure.walk :as walk]
   [k16.gx.node :as gx.node]
   [k16.gx.traversal :as gx.traversal]))

(defprotocol RefLookup
  (lookup-in [_ subgraph]))

(deftype Ref [ref-paths lookup-fn value]
  RefLookup
  (lookup-in [_ subgraph]
    (lookup-fn subgraph))

  gx.node/Node
  (traverse [self key not-found]
    (get (deref self) key not-found))
  (refs [_]
    ref-paths)

  clojure.lang.IDeref
  (deref [_]
    (walk/postwalk
     (fn [node]
       (if (gx.node/is-node? node)
         @node
         node))
     value))

  p/Datafiable
  (datafy [this]
    {:ref-paths (gx.node/refs this)
     :value value}))

(defn is-ref? [value]
  (instance? Ref value))

(defn ref
  ([key|path] (ref key|path nil))
  ([key|path fallback]
   (let [path (if (vector? key|path)
                key|path
                [key|path])]

     (letfn [(lookup [subgraph]
               (->Ref #{path}
                      lookup
                      (gx.traversal/get-in* subgraph path fallback)))]
       (->Ref #{path} lookup nil)))))

(defn- -with-refs [refs f]
  (let [ref-paths (into #{}
                        (mapcat gx.node/refs)
                        (vals refs))]

    (letfn [(lookup-with [prev-args prev-value]
              (fn [subgraph]
                (let [args (into {}
                                 (map (fn [[var ref]]
                                        [var @(lookup-in ref subgraph)]))
                                 refs)
                      value (if (and (not (identical? ::unresolved prev-args))
                                     (= prev-args args))
                              prev-value
                              (f args))]
                  (->Ref ref-paths
                         (lookup-with args value)
                         value))))]
      (->Ref ref-paths
             (lookup-with ::unresolved nil)
             nil))))

(defn- with-refs* [refs body]
  (let [paired-refs (partition-all 2 refs)
        fq-vars (into #{}
                      (map first)
                      paired-refs)
        refs (into {}
                   (map (fn [[k v]]
                          [(keyword (name k)) v]))
                   paired-refs)
        vars (-> refs keys vec)
        form (walk/postwalk
              (fn [node]
                (if (some #{node} fq-vars)
                  (symbol (name node))
                  node))
              body)]
    `(#'k16.gx.ref/-with-refs
      ~refs (fn [{:keys ~vars}]
              ~@form))))

(defmacro with-refs [refs & body]
  (with-refs* refs body))

(defn deferred-ref [form]
  (let [ref-paths
        (let [deps (atom #{})]
          (walk/postwalk
           (fn [node]
             (if (is-ref? node)
               (do (swap! deps set/union (gx.node/refs node))
                   node)
               node))
           form)
          @deps)]

    (letfn [(lookup-with [form]
              (fn [subgraph]
                (let [resolved (walk/postwalk
                                (fn [node]
                                  (if (is-ref? node)
                                    (lookup-in node subgraph)
                                    node))
                                form)]
                  (->Ref ref-paths
                         (lookup-with resolved)
                         resolved))))]
      (->Ref ref-paths
             (lookup-with form)
             nil))))
