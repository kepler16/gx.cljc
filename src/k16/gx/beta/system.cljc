(ns k16.gx.beta.system
  (:require [k16.gx.beta.core :as gx]
            [k16.gx.beta.errors :as gx.errors]
            [promesa.core :as p]))

(defonce registry* (atom {}))

(defn- filter-nodes
  [graph selector]
  (if (seq selector)
    (select-keys graph selector)
    graph))

(defn- ->err-msg
  [node-key message exception]
  (str node-key "\n\t" message (when exception
                                 (str "\n\t" (ex-message exception)))))

(defn throw-root-exception!
  [step failures]
  (let [{:keys [message causes node-key]} (last failures)
        {:keys [exception]} (first causes)
        msg (->err-msg node-key message exception)]
    (throw (or exception (ex-info msg {:step step
                                       :failure (last failures)
                                       :subsequent-failures
                                       (-> failures
                                           (butlast)
                                           (reverse))})))))

(defn states
  "Gets list of states of the graph as map.
   Optionally accepts selector as set of nodes from the graph.
   Selector works as a filter."
  ([system-name]
   (states system-name nil))
  ([system-name selector]
   (when-let [gx-map (get @registry* system-name)]
     (filter-nodes (gx/states gx-map) selector))))

(defn states-map
  "Prints node states as a map grouped by states"
  ([system-name]
   (states-map system-name nil))
  ([system-name selector]
   (some->> (states system-name selector)
            (reduce (fn [acc [k v]]
                      (update acc v conj k))
                    {}))))

(defn values
  "Gets list of values of the graph as map.
   Optionally accepts selector as set of nodes from the graph.
   Selector works as a filter."
  ([system-name]
   (values system-name nil))
  ([system-name selector]
   (when-let [gx-map (get @registry* system-name)]
     (filter-nodes (gx/values gx-map) selector))))

(defn failures
  "Gets list of failures of the graph as map.
   Optionally accepts selector as set of nodes from the graph.
   Selector works as a filter."
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (seq (:failures gx-map))))

(defn failed-nodes
  [system-name]
  (when-let [gx-map (get @registry* system-name)]
    (into {}
          (comp
           (filter (fn [[_k node]]
                     (-> node :gx/failure :causes)))
           (map (fn [[k node]]
                  [k (-> node :gx/failure :causes)])))
          (:graph gx-map))))

(defn failures-humanized
  "Returns all failures as single humanized formatted string (ready for output)."
  [system-name]
  (when-let [failures (failures system-name)]
    (gx.errors/humanize-all failures)))

(defn register!
  "Registers a new system. Throws a normalization exception on any failure."
  [system-name gx-map]
  (let [normalized (gx/normalize gx-map)]
    (swap! registry* assoc system-name normalized)
    (if-let [failures (seq (:failures normalized))]
      (throw-root-exception! :gx/normalize failures)
      normalized)))

(defn get-by-name
  "Gets a system by its name"
  [system-name]
  (get @registry* system-name))

(defn signal!
  "Sends signal to a system and updates it in registry.

   - Accepts system name and a signal key.
   - Returns a promise with a new system on success.
   - Returns a resolved promise with nil if system does not exist.
   - Rejects with `clojure.lang.ExceptionInfo`"
  ([system-name signal-key]
   (signal! system-name signal-key nil))
  ([system-name signal-key selector]
   (if-let [gx-map (get @registry* system-name)]
     (-> (gx/signal gx-map signal-key selector)
         (p/then (fn [graph]
                   (swap! registry* assoc system-name graph)
                   (if-let [failures (:failures graph)]
                     (throw-root-exception! signal-key failures)
                     graph)))
         (p/catch (fn [e] e)))
     (p/resolved nil))))

(comment
  (try
    (register! :sys {:graph {:a '(gx/ref :b)
                             :b '(gx/ref :a)
                             :c '(gx/ref :z)
                             :z {:gx/component 'non.existend/component}}})
    (catch Exception e
      (println (ex-message (or (ex-cause e) e)))))
  ;; clj
  (try
    @(signal! :sys :gx/start)
    (catch Exception e
      (println (type e))
      (println (ex-message (or (ex-cause e) e)))))

  ;; cljs
  (-> (signal! :sys :gx/start)
      ;; (p/then js/console.log)
      (p/catch #(js/console.log (ex-message %))))

  ;; common
  (println
   (failures-humanized :sys))

  (values :sys)
  (first (failures :sys)))
