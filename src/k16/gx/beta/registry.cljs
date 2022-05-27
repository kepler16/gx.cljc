(ns k16.gx.beta.registry
  (:require-macros [k16.gx.beta.registry :refer [register-graph!]])
  (:require ["fs" :as fs]
            [clojure.edn :as edn]))

(defn slurp [path]
  (.readFileSync fs path "utf8"))

(defn load-graph!
  [path]
  (let [graph (edn/read-string (slurp path))]
    (register-graph! graph)))