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
  [path]
  (let [graph# (edn/read-string (slurp path))]
    (if (:ns &env)
      `(do ~@(for [v# (gather-vars graph#)]
               `(swap! registry* assoc ~(str v#) ~v#))
           '~graph#)
      `'~graph#)))


(comment
  (load-graph! "test/fixtures/graph.edn"))
