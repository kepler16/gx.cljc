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

(def ^:export my-component-timeout
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              (p/do (p/delay 10000) props))
              :gx/timeout 10}})

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
         (-> (gx/failures s)
             :my-component
             :causes
             first
             :exception
             (ex-data)))))

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

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
#?(:clj
   (deftest async-timeout-component-test
     (let [graph {:my-component
                  {:gx/component
                   'k16.gx.beta.async-processor-test/my-component-timeout
                   :gx/props {:foo :bar}}}]
       (is (= "Operation timed out."
              (-> @(gx/signal {:graph graph} :gx/start)
                  (gx/failures)
                  :my-component
                  :causes
                  first
                  :title))))))

