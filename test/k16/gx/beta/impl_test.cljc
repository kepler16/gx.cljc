(ns k16.gx.beta.impl-test
  (:require [clojure.test :refer [deftest testing is]]
            [k16.gx.beta.impl :as impl]))

(deftest deep-merge-test
  (testing "it should handle nil as empty map"
    (is (= (impl/deep-merge #:gx{:start {:nested-a 1}} nil)
           #:gx{:start {:nested-a 1}}))
    (is (= (impl/deep-merge nil #:gx{:start {:nested-a 1}})
           #:gx{:start {:nested-a 1}})))
  (testing "it should deep merge signal maps into one"
    (is (= (impl/deep-merge
            ;; signal from component
            #:gx{:start #:gx{:props {:config 1},
                             :props-schema
                             [:map
                              [:config
                               [:map [:nested-a pos-int?]]]],
                             :processor identity},
                 :stop #:gx{:processor identity}}
            ;; signal from config
            #:gx{:start #:gx{:props {:config {:nested-a '(gx/ref :z)}}}})
           ;; resulting signal
           #:gx{:start #:gx{:props {:config {:nested-a '(gx/ref :z)}},
                            :props-schema
                            [:map
                             [:config
                              [:map [:nested-a pos-int?]]]]
                            :processor identity},
                :stop #:gx{:processor identity}}))))
