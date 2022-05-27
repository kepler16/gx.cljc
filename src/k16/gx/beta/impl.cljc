(ns k16.gx.beta.impl
  #?(:cljs (:require [clojure.string :as string]
                     [k16.gx.beta.registry :as reg])))

(defn sccs
  "Returns a topologically sorted list of strongly connected components.
  Tarjan's algorithm."
  ([g] (sccs g []))
  ([g sccs-init]
   (let [strong-connect
         (fn strong-connect [acc v]
           (let [acc (-> acc
                         (assoc-in [:idxs v] (:idx acc))
                         (assoc-in [:low-links v] (:idx acc))
                         (update :idx inc)
                         (update :S conj v)
                         (assoc-in [:on-stack v] true))
                 acc (reduce
                      (fn [acc w]
                        (cond
                          (not (get-in acc [:idxs w]))
                          (let [acc (strong-connect acc w)]
                            (update-in acc
                                       [:low-links v]
                                       min
                                       (get-in acc [:low-links w])))
                          (get-in acc [:on-stack w])
                          (update-in acc
                                     [:low-links v]
                                     min
                                     (get-in acc [:idxs w]))
                          :else acc))
                      acc
                      (get g v))]
             (if (= (get-in acc [:idxs v]) (get-in acc [:low-links v]))
               (let [[S on-stack scc] (loop [S (:S acc)
                                             on-stack (:on-stack acc)
                                             scc #{}]
                                        (let [w (peek S)
                                              S (pop S)
                                              on-stack (dissoc on-stack w)
                                              scc (conj scc w)]
                                          (if (= v w)
                                            [S on-stack scc]
                                            (recur S on-stack scc))))]
                 (-> acc
                     (assoc :S S
                            :on-stack on-stack)
                     (update :sccs conj scc)))
               acc)))]
     (:sccs
      (reduce (fn [acc v]
                (if-not (contains? (:idxs acc) v) (strong-connect acc v) acc))
              {:S ()
               :idx 0
               :sccs sccs-init}
              (keys g))))))

(defn cycles
  [sccs g]
  (filter #(or (>= (count %) 2)
               (get-in g [(first %) (first %)]))
          sccs))

(defn dependency-errors
  [g sccs]
  (concat
   (mapcat
    (fn [[k v]]
      (seq
       (map (fn [does-not-exist]
              {:type :missing
               :from k
               :to does-not-exist})
            (remove #(contains? g %) v))))
    g)
   (map (fn [cycle] {:type :cycle :between cycle})
        (cycles sccs g))))

(defn human-render-dependency-error
  [dependency-error]
  (case (:type dependency-error)
    :missing
    (str (:from dependency-error) " depends on " (:to dependency-error)
         ", but " (:to dependency-error) " doesn't exist.")

    :cycle
    (str "There's a circular dependency between "
         (apply str (interpose
                     " -> "
                     (concat
                      (reverse (:between dependency-error))
                      [(first (reverse (:between dependency-error)))]))))

    (pr-str dependency-error)))

#?(:cljs (defn sym->js-resolve [sym]
           (let [path (when (symbol? sym)
                        (-> (str sym)
                            (string/replace #"/" ".")
                            (string/replace #"-" "_")
                            (string/split #"\.")))]
             (apply aget (concat [js/goog "global"] path)))))

(defn namespace-symbol
  "Returns symbol unchanged if it has a namespace, or with clojure.core as it's
  namespace otherwise."
  [sym]
  (if (namespace sym)
    #?(:clj sym :cljs (sym->js-resolve sym))
    #?(:clj (symbol "clojure.core" (name sym))
       :cljs ((ns-publics 'cljs.core) sym))))

(def mergable? (every-pred #(and (map? %) (not (record? %)))))

(defn merger
  [left right]
  (if (mergable? left right)
    (merge-with merger left right)
    (or right left)))

(defn deep-merge
  "Recursively merges maps."
  [& maps]
  (reduce merger maps))
