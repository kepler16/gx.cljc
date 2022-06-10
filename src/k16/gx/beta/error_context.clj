(ns k16.gx.beta.error-context)

(defmacro with-err-ctx
  [ctx & body]
  `(binding [~'k16.gx.beta.errors/*err-ctx*
             (merge ~'k16.gx.beta.errors/*err-ctx* ~ctx)]
     ~@body))
