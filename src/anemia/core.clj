(ns anemia.core
  (:require [anemia.files :as migration-files])
  (:require [clojure.java.jdbc :as sql])
  (:require [clojure.set :as set])
  (:require [clojure.data :as data])
  (:require [pandect.algo.sha256 :as sha256])
  (:import java.util.Date
           java.text.SimpleDateFormat))

(def dbcoll [{:classname "com.mysql.jdbc.Driver"
              :subprotocol "mysql"
              :subname (str "//" "0.0.0.0" ":" "3306" "/" "schemex")
              :user "root"
              :password "sriq@"}
             {:classname "com.mysql.jdbc.Driver"
              :subprotocol "mysql"
              :subname (str "//" "0.0.0.0" ":" "3306" "/" "schemex2")
              :user "root"
              :password "sriq@"}])

(def loaded-migrations (migration-files/load-migrations "migrations/migrations.clj"))

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
    (sql/db-do-commands db (sql/drop-table-ddl :anemia_migrations))))

(defn create-migration-table [db]
  (if-not (migration-table-exists? db)
    (sql/db-do-commands db
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
  "Lists the migrations that have already been run against the database."
  [db]
  (sql/query db "SELECT name, checksum FROM anemia_migrations"))

(defn diff-migrations
  "Returns the checksums of the migration list against the migrations that have already been run against the database."
  [db]
  (list-migrations db))

(defn find-migrations-with-checksum-mismatch
  "Returns the filenames of migrations with a checksum mismatch."
  [db]
  (let [db-migrations (list-migrations db)]
    (map #(first %)
         (filter #(> (count (second %)) 1)
                 (group-by :name (set/union (set loaded-migrations) (set db-migrations)))))))

(defn check-migrations
  "Returns true if the migrations will successfully run, false if they wont."
  [db]
  (empty? (find-migrations-with-checksum-mismatch db)))

(defn find-migrations-to-run
  [db]
  (let [db-migrations (list-migrations db)]
    (set/difference (set loaded-migrations) (set db-migrations))))

(defn run-new-migrations
  [db]
  (find-migrations-to-run db))

(defn run-migrations
  [dbcoll]
  (let [db-valid-results (map check-migrations dbcoll)]
    (if (every? true? db-valid-results)
        (map run-new-migrations dbcoll)
        (extract-invalid-check-results db-valid-results))))

(defn extract-invalid-check-results
  [results]
  (filter #(= (second %) false) (zipmap dbcoll results)))

(run-migrations dbcoll)

(defn -main [& args]
  (println "unreleased"))
