(ns app
  (:require [clojure.java.jdbc :as j]))

(defn get-users-handler
  [req]
  {:status 200
   :content-type "text/plain"
   :body (pr-str
          (j/query (:db-spec req) ["select * from users"]))})
