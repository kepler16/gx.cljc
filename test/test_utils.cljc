(ns test-utils
  #?(:cljs (:require [cljs.test :as t]
                     [promesa.core :as p])))

(defn test-async
  [promise check-fn]
  #?(:clj (check-fn @promise)
     :cljs (t/async done (p/then promise (fn [resolved]
                                           (check-fn resolved)
                                           (done))))))
