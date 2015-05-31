(ns anemia.core
  (:require [clojure.java.jdbc :as sql]))

(let [db-host "0.0.0.0"
      db-port 3306
      db-name "schemex"]

  (def db {:classname "com.mysql.jdbc.Driver"
           :subprotocol "mysql"
           :subname (str "//" db-host ":" db-port "/" db-name)
           :user "root"
           :password "sriq@"}))

(defn- list-table-meta-data [db]
  (-> (sql/get-connection db)
      .getMetaData
      (.getTables nil nil nil nil)
      sql/metadata-result))

(defn- list-table-names [db]
  (map #(:table_name %) (list-table-meta-data db)))

(defn- migration-table-exists? [db]
  (not (empty? (filter #(= % "anemia_migrations") (list-table-names db)))))

(defn- drop-migration-table [db]
  (if (migration-table-exists? db)
    (sql/db-do-commands
     db
     (sql/drop-table-ddl :anemia_migrations))))

(defn create-migration-table [db]
  (if-not (migration-table-exists? db)
    (sql/db-do-commands
     db
     (sql/create-table-ddl :anemia_migrations [:name "VARCHAR(20)" "NOT NULL"])
     "CREATE UNIQUE INDEX anemia_migrations_name_idx ON anemia_migrations (name)")))

(defn -main [& args]
  (println "unreleased"))


