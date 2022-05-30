(ns k16.gx.beta.registry
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]))

(defonce registry* (atom {}))

(defn gather-vars
  [graph]
  (let [syms* (atom #{})]
    (walk/postwalk
     (fn [x]
       (when (and (symbol? x)
                  (namespace x)
                  (not= "gx" (namespace x)))
         (swap! syms* conj x)
       x))
     graph)
    @syms*))

(defmacro load-graph!
  "Loads graph from path. Additionally on cljs side, scans for symbols
   and populates registry (advanced compilation support)"
  [path]
  (let [graph# (edn/read-string (slurp path))]
    (if (:ns &env)
      `(do ~@(for [v# (gather-vars graph#)]
               `(swap! registry* assoc ~(str v#) ~v#))
           '~graph#)
      `'~graph#)))
