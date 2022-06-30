(ns k16.gx.beta.async-processor-test
  (:require [k16.gx.beta.core :as gx]
            [promesa.core :as p]
            [test-utils :refer [test-async]]
            #?(:clj [clojure.test :as t :refer [deftest is]])
            #?(:cljs [cljs.test :as t :refer-macros [deftest is]])))

(def ^:export my-component
  {:gx/start {:gx/processor
              (fn [{:keys [props]}]
                (p/resolved (pr-str props)))}})

(def ^:export my-component-reject
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              (p/rejected (ex-info "error!" props)))}})

(def ^:export my-component-exception
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              (throw (ex-info "error!" props)))}})

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(deftest async-success-component-test
  (let [graph {:my-component
               {:gx/component 'k16.gx.beta.async-processor-test/my-component
                :gx/props {:foo :bar}}}]
    (test-async (gx/signal {:graph graph} :gx/start)
                (fn [started]
                  (is (= "{:foo :bar}" (-> (gx/values started)
                                           :my-component)))))))

(defn- run-check [s]
  (is (= {:foo :bar}
         #?(:clj (-> (gx/failures s)
                     :my-component
                     :internal-data
                     :ex
                     (ex-cause)
                     (ex-data))
            :cljs (-> (gx/failures s)
                      :my-component
                      :internal-data
                      :ex)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(deftest async-rejected-component-test
  (let [graph {:my-component
               {:gx/component
                'k16.gx.beta.async-processor-test/my-component-reject
                :gx/props {:foo :bar}}}]
    (test-async (gx/signal {:graph graph} :gx/start) run-check)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(deftest async-exception-component-test
  (let [graph {:my-component
               {:gx/component
                'k16.gx.beta.async-processor-test/my-component-exception
                :gx/props {:foo :bar}}}]
    (test-async (gx/signal {:graph graph} :gx/start) run-check)))
