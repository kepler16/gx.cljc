(ns user
  (:require [clojure.test :as t]
            [k16.gx.beta.core :as gx]
            [k16.gx.beta.system :as system]))

(defn run-k16-tests
  [_]
  (t/run-all-tests #"k16.*-test"))

(comment
 (system/register
  :dev
  (fn []
    {:graph-config gx/default-graph-config
     :graph
     {:port '(inc 1234)
      :portttt '(inc (gx/ref :port))
      :pop '(+ (gx/ref :port) (gx/ref :portttt))}})))
