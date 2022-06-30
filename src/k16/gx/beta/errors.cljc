(ns k16.gx.beta.errors)

(defrecord ErrorContext [error-type node-key node-contents signal-key])

(def ^:dynamic *err-ctx*
  "Error context is used for creating/throwing exceptions with contextual data"
  (map->ErrorContext {:error-type :general}))

(defn gx-err-data
  ([internal-data]
   (gx-err-data nil internal-data))
  ([message internal-data]
   (->> *err-ctx*
        (filter (fn [[_ v]] v))
        (into (if message {:message message} {}))
        (merge {:internal-data internal-data}))))

(defn throw-gx-err
  ([message]
   (throw-gx-err message nil))
  ([message internal-data]
   (throw (ex-info message (gx-err-data message internal-data)))))

(defn ex->gx-err-data
  [ex]
  (->> (ex-data ex)
       (merge *err-ctx*)
       (filter (fn [[_ v]] v))
       (into {:message (ex-message ex)})))

(defn- stringify
  [token]
  (cond
    (string? token) token
    (nil? token) nil
    :else (pr-str token)))

(defn- tokenize
  [& token-pairs]
  (assert (even? (count token-pairs))
          "tokenize accepts only even number of forms")
  (->> token-pairs
       (map stringify)
       (partition 2)
       (filter (comp seq second))
       (map (fn [[a b]] [a (str "'" b "'")]))
       (interpose ", ")
       (flatten)
       (apply str)))

(defn humanize-error
  [{:keys [node-key signal-key message]} & rest-of-error]
  (let [rest-of-error (filter seq rest-of-error)]
    (apply str (concat [(or message "Error") ": "
                        (tokenize "node = " node-key
                                  "signal = " signal-key)]
                       (when (seq rest-of-error)
                         (conj (interpose "\n\t• " rest-of-error)
                               "\n\t• "))))))

(defmulti humanize :error-type)

(defn humanize-all
  [errors]
  (->> errors
       (map humanize)
       (interpose "\n")
       (apply str)))

(defmethod humanize :general
  [error]
  (humanize-error error))

(defmethod humanize :normalize-node
  [{:keys [internal-data] :as error}]
  (humanize-error error (tokenize
                         "form = " (:form-def internal-data)
                         "token = " (:token internal-data))))

(comment
  (println
   (humanize
    {:error-type :normalize-node,
     :node-key :d,
     :node-contents '(throw (ex-info "foo" (gx/ref :a))),
     :signal-key nil,
     :message "Special forms are not supported",
     :internal-data
     {:form-def '(throw (ex-info "foo" (gx/ref :a))), :token 'throw}})))

(defmethod humanize :deps-sort
  [{:keys [internal-data] :as error}]
  (apply humanize-error error (:errors internal-data)))

(apply + 1 [1 2])

(comment
  (println
   (humanize-all
    [{:internal-data {:errors ["circular :a -> :b -> :a" "circular :c -> :c"]},
      :message "Dependency errors",
      :error-type :deps-sort,
      :signal-key :gx/start}
     {:internal-data
      {:errors '("circular :a -> :b -> :a")},
      :message "Dependency errors",
      :error-type :deps-sort,
      :signal-key :gx/start}])))

(defmethod humanize :node-signal
  [{:keys [internal-data] :as error}]
  (humanize-error
   error (when-let [{:keys [ex-message dep-node-keys]} internal-data]
           (tokenize "error = " ex-message
                     "deps-nodes = " dep-node-keys))))

(comment
  (println
   (humanize-all
    [{:internal-data {:dep-node-keys '(:c)},
      :message "Failure in dependencies",
      :error-type :node-signal,
      :node-key :d,
      :node-contents '(gx/ref :c),
      :signal-key :gx/start}
     {:internal-data {:dep-node-keys '(:b)},
      :message "Failure in dependencies",
      :error-type :node-signal,
      :node-key :c,
      :node-contents '(gx/ref :b),
      :signal-key :gx/start}
     {:internal-data
      {:ex-message "Divide by zero",
       :args {:props {:a 1}, :value nil}},
      :message "Signal processor error",
      :error-type :node-signal,
      :node-key :b,
      :node-contents '(/ (gx/ref :a) 0),
      :signal-key :gx/start}])))

(defmethod humanize :props-validation
  [{:keys [internal-data] :as error}]
  (humanize-error
   error (when-let [{:keys [schema-error]} internal-data]
           (tokenize "schema-error = " schema-error))))

(comment
  (println
   (humanize {:error-type :props-validation,
              :message "Props validation error",
              :node-key :comp,
              :node-contents
              #:gx{:component 'k16.gx.beta.core-test/props-validation-component,
                   :start #:gx{:props-fn 'k16.gx.beta.core-test/my-props-fn}},
              :signal-key :gx/start,
              :internal-data
              {:props-value {:name "John",
                             :last-name "Doe",
                             :full-name "John Doe"},
               :props-schema [:map [:foo string?]],
               :schema-error {:foo ["missing required key"]}}})))

(defmethod humanize :normalize-node-component
  [{:keys [internal-data] :as error}]
  (humanize-error
   error (tokenize "schema-error = " (:schema-error internal-data)
                   "node-contents = " (:node-contents error))))

(comment
  (println
   (humanize {:message "Component schema error",
              :error-type :normalize-node-component,
              :node-key :c,
              :node-contents
              #:gx{:component 'k16.gx.beta.core-test/invalid-component-2},
              :internal-data
              {:component #:gx{:start #:gx{:processor "non callable val"}}
               :component-schema [:map-of keyword?]
               :schema-error
               #{[:gx/start
                  #:gx{:processor ["should be an fn" "should be a keyword"]}]}}}))

  (println
   (humanize {:message "Component could not be resolved",
              :error-type :normalize-node-component,
              :node-key :z,
              :node-contents #:gx{:component 'non.existend/component},
              :internal-data {:component 'non.existend/component}})))
