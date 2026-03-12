(ns k16.gx.thread
  (:import
   java.util.concurrent.Executors
   java.util.concurrent.ExecutorService))

(def ^:dynamic *executor*
  (Executors/newVirtualThreadPerTaskExecutor))

(defmacro vthread [& body]
  `(let [^Callable fn# (bound-fn [] ~@body)]
     (ExecutorService/.submit *executor* fn#)))

(defn deref-maybe-fut [maybe-fut]
  (if (future? maybe-fut)
    @maybe-fut
    maybe-fut))
