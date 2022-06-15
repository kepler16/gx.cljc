(ns k16.gx.beta.wrapper-test
  (:require
   [k16.gx.beta.core :as gx]
   #?(:clj [clojure.test :as t :refer [deftest is]])
   #?@(:cljs [[cljs.test :as t :refer-macros [deftest is]]
              [promesa.core :as p]])))

(def ^:export http-server-component
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              ;; emulate some kind of server instance as an atom
                              (atom {:server-instance props}))}
   :gx/stop {:gx/processor (fn [_] nil)}})


(defonce log-atom (atom []))

(defn ^:export log-wrapper
  [{:keys [signal-key node-key]} processor-fn]
  (fn [params]
    (swap! log-atom
           conj
           (str "1: " node-key " " signal-key))
    (let [val (processor-fn params)]
      (swap! log-atom
             conj
             (str "1: " node-key " " signal-key " done"))
      val)))

(defn ^:export log-wrapper-2
  [{:keys [signal-key node-key]} processor-fn]
  (fn [params]
    (swap! log-atom
           conj
           (str "2: " node-key " " signal-key))
    (let [val (processor-fn params)]
      (swap! log-atom
             conj
             (str "2: " node-key " " signal-key " done"))
      val)))

(deftest wrapper-test
  (let [graph {:props-fn/opts :foo
               :http/options {:port 8080}
               :http/server
               {:gx/start {:gx/wrappers ['k16.gx.beta.wrapper-test/log-wrapper
                                         'k16.gx.beta.wrapper-test/log-wrapper-2]}
                :gx/stop {:gx/wrappers ['k16.gx.beta.wrapper-test/log-wrapper
                                        'k16.gx.beta.wrapper-test/log-wrapper-2]}
                :gx/props '(gx/ref :http/options)
                :gx/component 'k16.gx.beta.wrapper-test/http-server-component}}
        run-checks (fn []
                     (is (= ["1: :http/server :gx/start"
                             "2: :http/server :gx/start"
                             "2: :http/server :gx/start done"
                             "1: :http/server :gx/start done"
                             "1: :http/server :gx/stop"
                             "2: :http/server :gx/stop"
                             "2: :http/server :gx/stop done"
                             "1: :http/server :gx/stop done"]
                            @log-atom)))]
    (reset! log-atom [])
    #?(:clj (do (-> {:graph graph}
                    (gx/signal :gx/start)
                    deref
                    (gx/signal :gx/stop)
                    deref)
                (run-checks))
       :cljs (t/async
              done
              (p/let [started (gx/signal {:graph graph} :gx/start)
                      _ (gx/signal started :gx/stop)]
                (run-checks)
                (done))))))
