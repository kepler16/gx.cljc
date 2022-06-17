(ns database
  (:require [clojure.java.jdbc :as jdbc]))

(defn start!
  [{db-spec :props}]
  ;; assuming, that in real life we instantiate database connection pool
  {:connection (jdbc/get-connection db-spec)})

(defn stop!
  [{db-spec :value}]
  (.close (:connection db-spec))
  nil)

(def sqlite
  {:gx/start {:gx/processor #'start!}
   :gx/stop {:gx/processor #'stop!}})

(defn populate!
  [{db-spec :props}]
  (jdbc/execute!
   db-spec "create table users (id integer, name text, last_name text)")
  (jdbc/insert-multi!
   db-spec :users [{:id 1 :name "John" :last_name "Doe"}
                   {:id 2 :name "Peter" :last_name "Parker"}
                   {:id 3 :name "Richard" :last_name "Walker"}
                   {:id 4 :name "Sergey" :last_name "Matvienko"}])
  true)

(comment
  (let [conn (jdbc/get-connection {:connection-uri "jdbc:sqlite::memory:"})
        spec {:connection conn}]
    (jdbc/execute!
     spec
     ["create table users (id integer, name text, last_name text)"])))

(def db-populator
  {:gx/start {:gx/processor populate!}})