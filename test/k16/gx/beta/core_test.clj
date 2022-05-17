(ns k16.gx.beta.core-test
  (:require [k16.gx.beta.core :as sut]
            [malli.core :as m]
            [malli.error :as me]

            [clojure.test :as t]
            [clojure.java.io :as io]))

(defn load-graphs []
  (read-string
   (slurp
    (io/resource "./graphs.edn"))))

(t/deftest correct-structure []
  (let [{:keys [g1] :as graphs} (load-graphs)
        g1-normalised (sut/normalize-graph g1)]
    (t/is
     (m/validate sut/?NormalizedComponentDefinition g1-normalised)
     (me/humanize
      (m/explain sut/?NormalizedComponentDefinition g1-normalised)))))
