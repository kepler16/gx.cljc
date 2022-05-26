(ns main
  (:require [system])
  (:gen-class))

(defn -main [& _args]
  (system/init-system!)
  (system/start-system!)

  (doto (Runtime/getRuntime)
    (.addShutdownHook
     (Thread. #(system/stop-system!))))
  @(promise))
