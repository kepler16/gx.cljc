(ns k16.gx.beta.registry)

(defonce component-registry* (atom {}))

(defn qualify-sym [nsp s]
  (when nsp
    (if (simple-symbol? s)
      (or (some-> nsp :uses s name (symbol (name s)))
          (symbol (name (-> nsp :name)) (name s)))
      (symbol (or (some-> nsp :requires (get (symbol (namespace s))) name)
                  (namespace s))
              (name s)))))

(defmacro defcomponent
  "Define new component and register (cljs only, expands to plain def on jvm)"
  [cname body-map]
  (if (:ns &env)
    (let [qualified-symbol# (qualify-sym (:ns &env) cname)]
      `(list
        (def ~cname ~body-map)
        (swap! component-registry* assoc '~qualified-symbol# ~cname)
        ~body-map))
    `(def ~cname ~body-map)))
