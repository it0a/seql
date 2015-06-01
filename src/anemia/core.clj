(ns anemia.core
  (:require [anemia.files :as migration-files])
  (:require [clojure.java.jdbc :as sql])
  (:require [clojure.set :as set])
  (:require [pandect.algo.sha256 :as sha256])
  (:import java.util.Date
           java.text.SimpleDateFormat))

(def loaded-migrations (migration-files/load-migrations "migrations/migrations.clj"))

(let [db-host "0.0.0.0"
      db-port 3306
      db-name "schemex"]

  (def dbcoll [{:classname "com.mysql.jdbc.Driver"
                :subprotocol "mysql"
                :subname (str "//" db-host ":" db-port "/" db-name)
                :user "root"
                :password "sriq@"}
               {:classname "com.mysql.jdbc.Driver"
                :subprotocol "mysql"
                :subname (str "//" db-host ":" db-port "/" "schemex2")
                :user "root"
                :password "sriq@"}]))

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
     (sql/create-table-ddl :anemia_migrations [:name "VARCHAR(255)" "NOT NULL"]
                                              [:date_completed "VARCHAR(32)" "NOT NULL"]
                                              [:checksum "VARCHAR(64)" "NOT NULL"])
     "CREATE UNIQUE INDEX anemia_migrations_name_idx ON anemia_migrations (name)")))

(defn get-date-completed
  []
  (-> (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS")
      (.format (Date.))))

(defn build-migration-data
  [migration]
  (identity {:name "Example"
    :date_completed (get-date-completed)
    :checksum (sha256/sha256 "Example")}))

(defn- insert-migration-record
  [db migration]
  (sql/db-transaction* db (fn [trans_db]
                            (sql/insert! trans_db :anemia_migrations (build-migration-data migration)))))

(defn- delete-migration-record
  [db migration]
  (sql/db-transaction* db (fn [trans_db]
                            (sql/delete! trans_db :anemia_migrations ["checksum=?" ((build-migration-data migration) :checksum)]))))

(defn list-migrations
  [db]
  (sql/query db "SELECT name, date_completed, checksum FROM anemia_migrations"))

(defn diff-migrations
  [db]
  (set/difference (set (map second loaded-migrations))
                  (set (map #(% :checksum) (list-migrations db)))))

;(map diff-migrations dbcoll)
;(map #(create-migration-table %) dbcoll)
;(map #(drop-migration-table %) dbcoll)
;(map #(insert-migration-record % 1) dbcoll)
;(map #(delete-migration-record % 1) dbcoll)

(defn -main [& args]
  (println "unreleased"))
