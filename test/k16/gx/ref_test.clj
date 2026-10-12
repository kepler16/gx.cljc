(ns k16.gx.ref-test
  (:require
   [clojure.datafy :as d]
   [clojure.test :refer [deftest is testing]]
   [k16.gx.ref :as gx.ref]
   [matcher-combinators.test]))

(deftest ref-test
  (let [ref (gx.ref/ref :some-key :fallback-value)]
    (is (= {:ref-paths #{[:some-key]}
            :value nil}
           (d/datafy ref)))
    (let [resolved-ref (gx.ref/lookup-in ref {:some-key :some-value})]
      (is (match? {:value :some-value} (d/datafy resolved-ref)))
      (is :some-value @resolved-ref))
    (is (= :fallback-value @(gx.ref/lookup-in ref {})))))

(deftest ref-in-test
  (let [ref (gx.ref/ref [:some-key :sub-key] :fallback-value)]
    (is (= {:ref-paths #{[:some-key :sub-key]}
            :value nil}
           (d/datafy ref)))
    (is (= :some-value @(gx.ref/lookup-in ref {:some-key {:sub-key :some-value}})))
    (is (= :fallback-value @(gx.ref/lookup-in ref {:some-key {}})))))

(deftest with-refs-test
  (let [ref (gx.ref/with-refs [a (gx.ref/ref :a)
                               b (gx.ref/ref :b)]
              (merge a b))]
    (is (= {:ref-paths #{[:a] [:b]}
            :value nil}
           (d/datafy ref)))
    (is (= {:left :value
            :right :value}
           @(gx.ref/lookup-in ref {:a {:left :value}
                                   :b {:right :value}})))))

(deftest with-refs-memoization-test
  (let [calls (atom 0)
        ref (gx.ref/with-refs [a (gx.ref/ref :a)
                               b (gx.ref/ref :b)]
              (swap! calls inc)
              {:a a :b b})
        resolved (gx.ref/lookup-in ref {:a 1
                                        :b 2})]
    (is (= {:a 1
            :b 2}
           @resolved))
    (is (= 1 @calls))

    (let [re-resolved (gx.ref/lookup-in resolved {:a 1
                                                   :b 2})]
      (is (= {:a 1
              :b 2}
             @re-resolved))
      (is (= 1 @calls))

      (is (= {:a 2
              :b 2}
             @(gx.ref/lookup-in re-resolved {:a 2
                                             :b 2})))
      (is (= 2 @calls)))

    (testing "the unresolved definition does not share a cache between lookups"
      @(gx.ref/lookup-in ref {:a 1
                              :b 2})
      (is (= 3 @calls)))))

(deftest with-refs-nested-in-deferred-ref-memoization-test
  (let [calls (atom 0)
        ref (gx.ref/deferred-ref
             {:inner (gx.ref/with-refs [a (gx.ref/ref :a)]
                       (swap! calls inc)
                       {:a a})})
        resolved (gx.ref/lookup-in ref {:a 1})
        re-resolved (gx.ref/lookup-in resolved {:a 1})]
    (is (= {:inner {:a 1}}
           @re-resolved))
    (is (= 1 @calls))))

(deftest with-refs-does-not-retain-values-test
  (let [ref (gx.ref/with-refs [a (gx.ref/ref :a)]
              {:a a})
        ;; created inside a separate fn so no stack frame keeps the value alive
        weak ((fn []
                (let [value (Object.)]
                  @(gx.ref/lookup-in ref {:a value})
                  (java.lang.ref.WeakReference. value))))]
    (loop [attempt 0]
      (when (and (some? (.get weak)) (< attempt 20))
        (System/gc)
        (Thread/sleep 50)
        (recur (inc attempt))))
    (is (nil? (.get weak)))))
