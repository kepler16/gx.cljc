(ns user
  (:require [clojure.test :as t]
            [hyperfiddle.rcf]
            [k16.gx.beta.core]))

;; repl loads user ns by default
;; init RCF
(hyperfiddle.rcf/enable!)

(defn run-k16-tests
  "Run tests with command `clj -X:test`"
  [_]
  (t/run-all-tests #"k16.*"))
