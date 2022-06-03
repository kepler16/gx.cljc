(ns k16.gx.beta.errors)

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
  [{:keys [node-key signal-key message]}]
  (str (or message "Error") ": "
       (tokenize "node = " node-key
                 "signal = " signal-key)))

(defmulti humanize :error-type)

(defmethod humanize :general
 [error]
 (humanize-error error))

(defmethod humanize :normalize-node
  [{:keys [internal-data] :as error}]
  (str (humanize-error error) ", "
       (tokenize
        "form = " (:form-def internal-data)
        "token = " (:token internal-data))))

(comment
  (humanize
   {:error-type :normalize-node,
    :node-key :d,
    :node-contents '(throw (ex-info "foo" (gx/ref :a))),
    :signal-key nil,
    :message "Special forms are not supported",
    :internal-data
    {:form-def '(throw (ex-info "foo" (gx/ref :a))), :token 'throw}}))

(defmethod humanize :deps-sort
  [{:keys [internal-data] :as error}]
  (str (humanize-error error)
       (when-let [errors (:errors internal-data)]
         (apply str (conj (interpose ", " errors) ", ")))))

(comment
  (humanize {:internal-data
             {:errors '("circular :a -> :b -> :a")},
             :message "Dependency errors",
             :error-type :deps-sort,
             :signal-key :gx/start}))

(defmethod humanize :node-signal
  [{:keys [internal-data] :as error}]
  (str (humanize-error error)
       (when-let [{:keys [ex-message args dep-node-keys]} internal-data]
         (str ", "
          (tokenize "error = " ex-message
                    "args = " args
                    "deps-nodes = " dep-node-keys)))))

(comment
  (map humanize
       (list {:internal-data {:dep-node-keys '(:c)},
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
              :signal-key :gx/start})))

(defmethod humanize :props-validation
  [{:keys [internal-data] :as error}]
  (str (humanize-error error)
       (when-let [{:keys [shema-error]} internal-data]
         (str ", " (tokenize "shema-error = " shema-error)))))

(comment
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
              :shema-error {:foo ["missing required key"]}}}))