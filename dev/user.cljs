(ns user
  (:require [cljs.js]))

(let [eval *eval*
      st (cljs.js/empty-state)]
  (set! *eval*
        (fn [form]
          (binding [cljs.env/*compiler* st
                    *ns* (find-ns cljs.analyzer/*cljs-ns*)
                    cljs.js/*eval-fn* cljs.js/js-eval]
            (eval form)))))
