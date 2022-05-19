(ns test-utils
  (:require ["fs" :as fs]))

(defn node-slurp [path]
  (.readFileSync fs path "utf8"))
