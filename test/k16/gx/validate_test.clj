(ns k16.gx.validate-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.gx :as gx]
   [k16.gx.ref :as gx.ref]
   [k16.test :refer [thrown-match?]]
   [matcher-combinators.test]))

(deftest direct-cycle-test
  (let [graph {:a {:b (gx.ref/ref :c)}

               :c (gx.ref/ref [:a :b])}]
    (thrown-match? (Exception
                    "Graph contains cyclic references"
                    {:errors {[:a :b] ["Contains cyclic references with [:c]"]
                              [:c] ["Contains cyclic references with [:a :b]"]}})
                   (gx/validate! graph))))

(deftest second-hand-cycle-test
  (let [graph {:a (gx.ref/ref :c)
               :b (gx.ref/ref :a)
               :c (gx.ref/ref :b)}]
    (thrown-match? (Exception
                    "Graph contains cyclic references"
                    {:errors {[:b] ["Contains cyclic references with [:c], [:a]"]
                              [:c] ["Contains cyclic references with [:b], [:a]"]
                              [:a] ["Contains cyclic references with [:b], [:c]"]}})
                   (gx/validate! graph))))

(deftest indirect-cycle-test
  (let [graph {:a {:b (gx.ref/ref :c)}

               :c (gx.ref/ref [:a])}]
    (thrown-match? (Exception
                    "Graph contains cyclic references"
                    {:errors
                     {[:a :b] ["Contains cyclic references with [:c]"]
                      [:c] ["Contains cyclic references with [:a :b]"]}})
                   (gx/validate! graph))))
