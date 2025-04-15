(ns k16.gx.malli-validation-test
  (:require
   [clojure.datafy :as d]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [k16.gx :as gx]
   [k16.gx.component :as gx.component]
   [k16.gx.malli :as gx.malli]
   [k16.gx.ref :as gx.ref]
   [matcher-combinators.test]))

(deftest signal-test
  (let [component-def {:props-schema [:map
                                      [:value :int]]

                       :signals
                       {:start {:handler (fn [_ props] {:props props})
                                :result-schema [:map
                                                [:props [:map
                                                         [:value :int]]]]}}}

        graph {:a 1
               :b "2"

               :c-a (gx.component/component
                     {:value (gx.ref/ref :a)}
                     component-def)

               :c-b (gx.component/component
                     {:value (gx.ref/ref :b)}
                     component-def)}

        graph (gx.malli/with-validation graph)

        graph (gx/signal! graph :start)]

    (is (thrown-match? Exception {:path [:c-b]
                                  :errors {:value ["should be an integer"]}}
                       (gx/values graph)))

    (is (= "Component [:c-b] failed props validation"
           (try (gx/values graph)
                (catch Exception ex (ex-message ex)))))

    (is (match? {:a 1
                 :b "2"
                 :c-a {:definition {:signals {:start {:validate-result fn?
                                                      :handler fn?
                                                      :result-schema vector?}}
                                    :validate-props fn?}
                       :error nil
                       :props {:ref-paths #{[:a]}
                               :value {:value {:ref-paths #{[:a]}
                                               :value 1}}}
                       :ref-paths #{[:a]}
                       :state :start
                       :value {:props {:value 1}}}
                 :c-b {:definition {:signals {:start {:validate-result fn?
                                                      :handler fn?
                                                      :result-schema vector?}}
                                    :validate-props fn?}
                       :error {:cause "Component [:c-b] failed props validation"
                               :data {:errors {:value ["should be an integer"]}}}

                       :props {:ref-paths #{[:b]}
                               :value nil}
                       :ref-paths #{[:b]}
                       :state nil
                       :value nil}}
                (walk/prewalk d/datafy graph)))))
