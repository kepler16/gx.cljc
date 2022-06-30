(ns test-utils
  #?(:cljs (:require [cljs.test :as t]
                     [promesa.core :as p])))

(defn test-async
  [promise check-fn]
  #?(:clj (try (check-fn @promise)
               (catch Throwable e
                 (check-fn nil e)))
     :cljs (t/async done
                   (-> promise
                       (p/then (fn [resolved]
                                 (check-fn resolved)
                                 (done)))
                       (p/catch (fn [err]
                                  (check-fn nil err)
                                  (done)))))))
