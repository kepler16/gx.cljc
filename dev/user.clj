(ns user
  (:require [clojure.test :as t]
            [k16.gx.beta.core]))

(defn run-k16-tests
  [_]
  (t/run-all-tests #"k16.*-test"))
