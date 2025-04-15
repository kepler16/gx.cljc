(ns k16.gx.component
  (:require
   [clojure.core.protocols :as p]
   [k16.gx.node :as gx.node]
   [k16.gx.ref :as gx.ref]
   [k16.gx.thread :as gx.thread]))

(defprotocol Instance
  (has-error? [_])
  (signal! [_ signal-key props opts]))

(defn- execute-with-timeout
  [handler signal ref-path value props default-timeout-ms opts]
  (let [{:keys [validate-props validate-result
                timeout-ms handler]}
        handler

        timeout-ms (or timeout-ms
                       default-timeout-ms
                       10000)]
    (when validate-props
      (validate-props props opts))
    (let [fut (gx.thread/vthread
               (handler value props))
          res (deref fut timeout-ms ::timeout)]
      (when (= ::timeout res)
        (throw (ex-info (str "Component at "
                             ref-path
                             " timed out after "
                             timeout-ms
                             "ms while handling signal "
                             signal)
                        {})))

      (when validate-result
        (validate-result res opts))

      res)))

(defn- normalize-signal [definition signal]
  (let [signal (if (fn? signal)
                 {:handler signal}
                 signal)]
    (merge {:validate-props (:validate-props definition)
            :timeout-ms (:timeout-ms definition)}
           signal)))

(deftype Component
         [definition props
          state error value]
  Instance
  (has-error? [_]
    (not (nil? error)))
  (signal! [_ signal-key partial-graph opts]
    (try
      (let [{:keys [filter-states timeout-ms ref-path]} opts

            resolved-props (gx.ref/lookup-in props partial-graph)
            signal-definition (get-in definition [:signals signal-key])
            signal-handler (normalize-signal definition signal-definition)

            should-execute?
            (and signal-definition
                 (not= state signal-key)
                 (or (not (seq filter-states))
                     (some #{state} filter-states)))]
        (if should-execute?
          (let [next-value (execute-with-timeout signal-handler
                                                 signal-key
                                                 ref-path
                                                 value
                                                 @resolved-props
                                                 timeout-ms
                                                 opts)]
            (Component. definition
                        resolved-props
                        signal-key
                        nil
                        next-value))
          (Component. definition
                      props
                      state
                      nil
                      value)))
      (catch Exception ex
        (Component. definition
                    props
                    state
                    (or (ex-cause ex) ex)
                    nil))))

  gx.node/Node
  (traverse [_ key not-found]
    (get value key not-found))
  (refs [_]
    (gx.node/refs props))

  clojure.lang.IDeref
  (deref [_]
    (when error
      (throw error))
    value)

  p/Datafiable
  (datafy [_]
    {:ref-paths (gx.node/refs props)

     :definition definition
     :props props
     :state state
     :error error
     :value value}))

(defn component
  ([definition] (component {} definition))
  ([props|ref definition]
   (let [props-ref (if (gx.ref/is-ref? props|ref)
                     props|ref
                     (gx.ref/deferred-ref props|ref))]
     (->Component definition
                  props-ref
                  nil
                  nil
                  nil))))

(defn is-instance? [value]
  (satisfies? Instance value))
