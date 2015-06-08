(ns seql.sql
  (:require [seql.files :as migration-files]
            [clojure.java.jdbc :as sql]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.data :as data]
            [pandect.algo.sha256 :as sha256])
  (:import java.util.Date
           java.text.SimpleDateFormat)
  (:gen-class))

(defn- list-table-meta-data [db]
  (-> (sql/get-connection db)
      .getMetaData
      (.getTables nil nil nil nil)
      sql/metadata-result))

(defn- list-table-names [db]
  (map #(:table_name %) (list-table-meta-data db)))

(defn- migration-table-exists? [db]
  (not (empty? (filter #(= % "seql_migrations") (list-table-names db)))))

(defn- drop-migration-table [db]
  (if (migration-table-exists? db)
    (sql/db-do-commands db (sql/drop-table-ddl :seql_migrations))))

(defn create-migration-table [db]
  (if-not (migration-table-exists? db)
    (sql/db-do-commands db (sql/create-table-ddl :seql_migrations [:name "VARCHAR(255)" "NOT NULL"]
                                                 [:date_completed "VARCHAR(32)" "NOT NULL"]
                                                 [:checksum "VARCHAR(64)" "NOT NULL"])
                        "CREATE UNIQUE INDEX seql_migrations_name_idx ON seql_migrations (name)")))

(defn get-date-completed
  []
  (-> (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS")
      (.format (Date.))))

(defn build-migration-data
  [migration]
  (dissoc (assoc migration :date_completed (get-date-completed)) :content))

(defn- insert-migration-record
  [db migration]
  (sql/insert! db :seql_migrations (build-migration-data migration)))

(defn- delete-migration-record
  [db migration]
  (sql/db-transaction* db (fn [trans_db]
                            (sql/delete! trans_db :seql_migrations ["checksum=?" ((build-migration-data migration) :checksum)]))))

(defn list-migrations
  "Lists the migrations that have already been run against the database."
  [db]
  (sql/query db "SELECT name, checksum FROM seql_migrations"))

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
                 (group-by :name (set/union (set (migration-files/load-migrations "migrations/migrations.clj")) (set db-migrations)))))))

(defn check-migrations
  "Returns true if the migrations will successfully run, false if they wont."
  [db]
  (let [results (find-migrations-with-checksum-mismatch db)]
    (doseq [r results]
      (println (str (db :subname) " => checksum mismatch in " r)))
    (empty? results)))

(defn find-migrations-to-run
  [db]
  (let [db-migrations (list-migrations db)
        migration-files (migration-files/load-migrations "migrations/migrations.clj")]
    (let [diff-set (set/difference (set migration-files) (set db-migrations))]
      (reverse (filter #(contains? diff-set %) migration-files)))))

(defn assoc-migration-content
  [migration]
  (assoc migration :content (migration-files/load-migration-content (migration :name))))

(defn run-new-migrations
  [db]
  (let [migrations (map assoc-migration-content (find-migrations-to-run db))]
    (if (empty? migrations)
      (println (str (db :subname) " => Up to date"))
      (sql/db-transaction* db (fn [trans_db]
                                (doseq [m migrations]
                                  (print (str (db :subname) " => " (m :name) " (" (m :checksum) ")..."))
                                  (doseq [query (m :content )]
                                    (sql/db-do-commands trans_db query))
                                  (insert-migration-record trans_db m)
                                  (print " OK")
                                  (println "")))))))

(defn extract-invalid-check-results
  [results dbcoll]
  (let [invalid-results (filter #(= (second %) false) (zipmap dbcoll results))]
    (doseq [r invalid-results]
      (println (str ((first r) :subname) " => contains mismatched checksums.")))
    (println "Taking no action.")
    (identity invalid-results)))

(defn preprocess-dbcoll
  [dbcoll]
  (doseq [db dbcoll] (create-migration-table db)))

(defn run-migrations
  [dbcoll]
  (preprocess-dbcoll dbcoll)
  (let [db-valid-results (map check-migrations dbcoll)]
    (if (every? true? db-valid-results)
      (doseq [db dbcoll] (run-new-migrations db))
      (extract-invalid-check-results db-valid-results dbcoll))))

(defn run-on-dbcoll
  [dbcoll fun]
  (doseq [db dbcoll]
    (fun db)))

(defn do-sync-migrations
  [db]
  (sql/db-transaction* db (fn [trans_db]
    (doseq [m (find-migrations-with-checksum-mismatch trans_db)]
      (sql/delete! trans_db :seql_migrations ["name = ?" m]))
    (let [migrations (map assoc-migration-content (find-migrations-to-run trans_db))]
      (if (empty? migrations)
        (println (str (trans_db :subname) " => Up to date"))
        (doseq [m migrations]
      (print (str (trans_db :subname) " => " (m :name) " (" (m :checksum) ")..."))
      (insert-migration-record trans_db m)
      (print "OK")
      (println (str ""))))))))

(defn sync-migrations
  [dbcoll]
  (preprocess-dbcoll dbcoll)
  (run-on-dbcoll dbcoll do-sync-migrations))

