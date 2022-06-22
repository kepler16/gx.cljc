(ns k16.gx.beta.context)

(defrecord ErrorContext [error-type node-key node-contents signal-key])

(def ^:dynamic *err-ctx*
  "Error context is used for creating/throwing exceptions with contextual data"
  (map->ErrorContext {:error-type :general}))

(def ^:dynamic *ctx* {})

(defmacro merge-err-ctx
  "Creates error context by merging value to `k16.gx.beta.error/*err-ctx*`"
  [ctx & body]
  `(binding [~'k16.gx.beta.context/*err-ctx*
             (merge ~'k16.gx.beta.context/*err-ctx* ~ctx)]
     ~@body))

(defmacro with-ctx
  "Takes map with two keys :ctx, :err. Creates general context from :ctx,
   merger :err to error context"
  [ctx & body]
  `(binding [~'k16.gx.beta.context/*ctx* (:ctx ~ctx)]
     (if-let [err# (:err ~ctx)]
       (merge-err-ctx err#
         ~@body)
       ~@body)))
