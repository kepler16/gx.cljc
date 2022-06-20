(ns database
  (:require [clojure.java.jdbc :as jdbc]))

;; start database instance function
(defn start!
  [{db-spec :props}]
  ;; assuming, that in real life a database connection pool
  ;; should be instantiated
  {:connection (jdbc/get-connection db-spec)})

;; stop database instance function
(defn stop!
  [{db-spec :value}]
  (.close (:connection db-spec))
  nil)

;; database component
(def sqlite
  {:gx/start {:gx/processor start!}
   :gx/stop {:gx/processor stop!}})

;; we populate our database with a sample data
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

;; db-populator has only :gx/start handler
;; and there is no teardown logic during stop
;; in case of :gx/stop only status will be changed to :stopped
;; and value will be left intact
(def db-populator
  {:gx/start {:gx/processor populate!}})