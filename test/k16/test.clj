(ns k16.test
  (:require
   [clojure.test :as test]
   [matcher-combinators.clj-test :refer [tagged-for-pretty-printing]]
   [matcher-combinators.core :as match]
   [matcher-combinators.matchers :as matchers]))

(defmethod test/assert-expr 'k16.test/-thrown-match? [msg form]
  (let [specs (butlast (rest form))
        body (last form)
        tests (mapv (fn [[class-matcher msg-matcher data-matcher]]
                      `[(matchers/pred (fn [value#]
                                        (instance? ~class-matcher value#))
                                      (str "Must be an instance of " ~class-matcher))
                        ~msg-matcher
                        ~data-matcher])
                    specs)]
    `(let [form# '~(cons 'thrown-match? (rest form))]
       (try ~body
            (test/do-report {:type :fail
                             :message ~msg
                             :expected form#
                             :actual (symbol "the expected exception wasn't thrown")})
            (catch Exception ex#
              (let [data# (mapv (fn [cause#]
                                 [cause# (ex-message cause#) (ex-data cause#)])
                               (take-while some? (iterate ex-cause ex#)))
                    test# ~tests
                    match-result# (match/match test# data#)]
                (if (match/indicates-match? match-result#)
                  (clojure.test/do-report {:type :pass
                                           :message ~msg
                                           :expected form#
                                           :actual ex#})
                  (clojure.test/do-report {:type :fail
                                           :message ~msg
                                           :expected form#
                                           :actual (tagged-for-pretty-printing
                                                    (list '~'not form#)
                                                    match-result#)}))))))))

(defmacro thrown-match? [& args]
  `(test/is (k16.test/-thrown-match? ~@args)))
