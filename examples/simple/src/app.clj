(ns app)

(defn get-users-handler
  [req]
  {:status 200
   :content-type "text/plain"
   :body (pr-str [{:user "John"}
                  {:user "Peter"}
                  {:user "Donald"}])})
