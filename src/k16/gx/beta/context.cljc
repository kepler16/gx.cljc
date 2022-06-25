(ns k16.gx.beta.context)

(def ^:dynamic *runtime*
  "GX's execution runtime, contains:
   - :err is used for creating/throwing exceptions with contextual data
   - :context contains gx context with configurations"
  {:err {:error-type :general}
   :context {}})

(defmacro merge-err-ctx
  "Creates error context by merging value to :err in `k16.gx.beta.error/*runtime*`"
  [err & body]
  `(binding [~`*runtime* (update ~`*runtime* :err merge ~err)]
     ~@body))

(defmacro with-ctx
  "Takes map with two keys :context and :err. Creates execution context"
  [ctx & body]
  `(binding [~`*runtime* (merge ~`*runtime* ~ctx)]
     ~@body))

(defn err []
  (get *runtime* :err))

(defn context []
  (get *runtime* :context))

(comment
  (with-ctx {:context {:foo 1}}
    *runtime*)
  (merge-err-ctx {:node-key :foo}
    (err))
  )
