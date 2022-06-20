(ns middlewares)

(defn database-middleware
  [handler database]
  (fn [request]
    (handler (assoc request :db-spec database))))
