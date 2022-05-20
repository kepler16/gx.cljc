(ns test-utils
  (:require ["fs" :as fs]))

(defn slurp [path]
  (.readFileSync fs path "utf8"))
