(ns example.core)

(def A
  {:gx/start
   {:processor
    (fn [{:keys [deps state]}]
      (println "Starting A"))}

   :gx/stop
   {:processor
    (fn [{:keys [deps state]}]
      (println "Stopping A"))}})

(def B
  {:gx/start
   {:deps [:a]
    :processor
    (fn [{:keys [deps state]}]
      (println "Starting B, with dep" (get deps :a)))}

   :gx/stop
   {:processor
    (fn [{:keys [deps state]}]
      (println "Stopping B"))}})

(def graph-config
  {:signals {:gx/start {:order :topological}
             :gx/stop {:order :reverse-topological
                       :deps-from :gx/start}}})

(def normalised-graph-example
  {:a A
   :b B})
