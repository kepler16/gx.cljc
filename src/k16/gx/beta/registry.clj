(ns k16.gx.beta.registry
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]))

(defmacro register-graph!
  [graph]
  `(walk/postwalk
    (fn [x#]
      (when (and (symbol? x#) (namespace x#))
        (alter-meta! x# #(assoc % :export true)))
      x#)
    ~graph))

(defn load-graph!
  [path]
  (edn/read-string (slurp path)))
