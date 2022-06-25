(ns k16.gx.beta.async-processor-test
  (:require [k16.gx.beta.core :as gx]
            [promesa.core :as p]
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
                :gx/props {:foo :bar}}}
        started (gx/signal {:graph graph} :gx/start)]
    #?(:clj (is (= "{:foo :bar}"
                   (-> (gx/system-value @started)
                       :my-component)))
       :cljs (t/async
              done (p/then started (fn [s]
                                     (is (= "{:foo :bar}"
                                            (-> (gx/system-value s)
                                                :my-component)))
                                     (done)))))))

(defn- run-check [s]
  (is (= {:foo :bar}
         #?(:clj (-> (gx/system-failure s)
                     :my-component
                     :internal-data
                     :ex
                     (ex-cause)
                     (ex-data))
            :cljs (-> (gx/system-failure s)
                      :my-component
                      :internal-data
                      :ex)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(deftest async-rejected-component-test
  (let [graph {:my-component
               {:gx/component
                'k16.gx.beta.async-processor-test/my-component-reject
                :gx/props {:foo :bar}}}
        started (gx/signal {:graph graph} :gx/start)]
    #?(:clj (run-check @started)
       :cljs (t/async
              done (p/then started (fn [s] (run-check s) (done)))))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(deftest async-exception-component-test
  (let [graph {:my-component
               {:gx/component
                'k16.gx.beta.async-processor-test/my-component-exception
                :gx/props {:foo :bar}}}
        started (gx/signal {:graph graph} :gx/start)]
    #?(:clj (run-check @started)
       :cljs (t/async
              done (p/then started (fn [s] (run-check s) (done)))))))
