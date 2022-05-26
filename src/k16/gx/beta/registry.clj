(ns k16.gx.beta.registry)

(declare entity-registry*)

(defn qualify-sym [nsp s]
  (when nsp
    (if (simple-symbol? s)
      (or (some-> nsp :uses s name (symbol (name s)))
          (symbol (name (-> nsp :name)) (name s)))
      (symbol (or (some-> nsp :requires (get (symbol (namespace s))) name)
                  (namespace s))
              (name s)))))

;; TODO explore if normalisation can be used to simplify. Can we simply call
;; (normalize-node-def body)... or something like that. Is there anything we
;; can use from this file and feed into normalisation step? is it possible
;; to (def graph (normalize-graph  <some big graph>))  and have that work in
;; cljs?
(defmacro def-component
  "Define new component and register to entity-registry
   (cljs only, expands to plain def on jvm)"
  [cname & body]
  (if (:ns &env)
    (let [qualified-symbol# (qualify-sym (:ns &env) cname)]
      `(list
        (def ~cname ~@body)
        (swap! entity-registry* assoc '~qualified-symbol# ~cname)))
    `(def ~cname ~@body)))

(defmacro def-props-fn
  "Define new function and register in entity-registry
   (cljs only, expands to plain defn on jvm)"
  [cname & body]
  (if (:ns &env)
    (let [qualified-symbol# (qualify-sym (:ns &env) cname)]
      `(list
        (defn ~cname ~@body)
        (swap! entity-registry* assoc '~qualified-symbol# ~cname)))
    `(defn ~cname ~@body)))