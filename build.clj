(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def global-opts
  {:repository "github-kepler"
   :lib 'kepler16/authzed.clj})

(defn release-prepare [opts]
  (->> opts
      (merge global-opts)
      (bb/clean)
      (bb/jar)))

(defn release-publish [opts]
    (->> opts
        (merge global-opts)
        (bb/deploy)))
