(ns k16.gx.system-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.gx :as gx]
   [k16.gx.system :as gx.system]
   [k16.test :refer [thrown-match?]]
   [matcher-combinators.test]))

(deftest system-test
  (let [system (gx.system/system
                {:a (gx/ref :b)
                 :b 1
                 :c (gx/component {:a (gx/ref :a)}
                                  {:signals {:start (fn [_ props]
                                                      {:props props})
                                             :stop (fn [_ _]
                                                     :stopped)}})})]

    (is (= {:a 1 :b 1 :c {:props {:a 1}}}
           (gx.system/start! system)))

    (is (= {:a 1 :b 1 :c {:props {:a 1}}}
           @system))

    (is (nil? (gx.system/stop! system)))

    (thrown-match? (Exception
                    #"System is stopped. You cannot deref a stopped system"
                    {})
                   @system)))

(deftest system-start-error-test
  (let [system (gx.system/system
                {:a (gx/component {:signals {:start (fn [_ _] :started)
                                             :stop (fn [_ _] :stopped)}})
                 :b (gx/component {:signals {:start (fn [_ _]
                                                      (throw (ex-info "Failed to start" {})))
                                             :stop (fn [_ _] :stopped)}})})]

    (thrown-match? (Exception
                    "Failed to resolve component :b"
                    {:type :gx/component-error
                     :path [:b]})
                   (Exception
                    "Failed to start"
                    {})
                   (gx.system/start! system))

    (is (nil? (gx.system/stop! system)))

    (thrown-match? (Exception
                    #"System is stopped. You cannot deref a stopped system"
                    {})
                   @system)))

(deftest system-stop-error-test
  (let [system
        (gx.system/system
         {:resource
          (gx/component
           {:signals
            {:start (fn [_ _]
                      :started)
             :stop (fn [_ _]
                     (throw (ex-info "Stop failure" {}))
                     :stopped)}})})]

    (is (= {:resource :started}
           (gx.system/start! system)))

    (try (gx.system/stop! system)
         (is false)
         (catch Exception ex
           (is (= "Failed to stop system" (ex-message ex)))

           (let [error (:resource (:errors (ex-data ex)))]
             (is (= "Stop failure" (ex-message (ex-cause error)))))))))
