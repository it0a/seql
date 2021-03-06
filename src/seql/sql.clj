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

(defn- migration-exists? [db migration]
  (not (empty?
         (sql/query db ["SELECT * FROM seql_migrations WHERE name = ?" (migration :name)]))))

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


(defn- update-migration-record
  [db migration]
  (let [migration-data (build-migration-data migration)]
    (if (migration-exists? db migration-data)
      (do
        (print (str "Synchronizing " (db :subname) " => " (migration :name) " (" (migration :checksum) ")..."))
        (sql/execute! db ["
            UPDATE seql_migrations
            SET checksum = ?
            WHERE name = ?
        " (migration-data :checksum)
          (migration-data :name)])
        (print "OK")
        (println ""))
      (println (str "Skipping sync on migration " (migration-data :name) " as it hasn't yet executed as a migration on this database")))))


(defn- delete-migration-record
  [db migration]
  (sql/db-transaction* db (fn [trans_db]
                            (sql/delete! trans_db :seql_migrations ["checksum=?" ((build-migration-data migration) :checksum)]))))

(defn list-migrations
  "Lists the migrations that have already been run against the database."
  [db migration-files-cksum]
  (let [migration-query "SELECT name, checksum FROM seql_migrations"
        migration-count (count migration-files-cksum)
        use-parameters? (and (> migration-count 0) (< migration-count 1990))]
    (if use-parameters?
        (let [where-clause (str " WHERE name IN (?" (apply str (repeat (- migration-count 1) ",?")))
              migration-query-full (str migration-query where-clause ")")]
          (sql/query db migration-query migration-query-full (map :name migration-files-cksum)))
        (sql/query db migration-query))))

(defn diff-migrations
  "Returns the checksums of the migration list against the migrations that have already been run against the database."
  [db]
  (list-migrations db []))

(defn find-migrations-with-checksum-mismatch
  "Returns the filenames of migrations with a checksum mismatch."
  [db migration-files-cksum ]
  (let [db-migrations (list-migrations db migration-files-cksum )]
    (map #(first %)
         (filter #(> (count (second %)) 1)
                 (group-by :name (set/union (set migration-files-cksum) (set db-migrations)))))))

(defn check-migrations
  "Returns true if the migrations will successfully run, false if they wont."
  [db migration-files-cksum]
  (let [results (find-migrations-with-checksum-mismatch db migration-files-cksum)]
    (doseq [r results]
      (println (str (db :subname) " => checksum mismatch in " r)))
    (empty? results)))

(defn find-migrations-to-run
  [db migration-files-cksum ]
  (let [db-migrations (list-migrations db migration-files-cksum )]
    (let [diff-set (set/difference (set migration-files-cksum) (set db-migrations))]
      (filterv #(contains? diff-set %) migration-files-cksum))))

(defn assoc-migration-content
  [migration]
  (assoc migration :content (migration-files/load-migration-content (migration :name))))

(defn run-new-migrations
  [db migration-files-cksum ]
  (let [migrations (find-migrations-to-run db migration-files-cksum )]
    (if (empty? migrations)
      (println (str (db :subname) " => Up to date"))
      (sql/db-transaction* db (fn [trans_db]
                                (doseq [m migrations]
                                  (print (str (db :subname) " => " (m :name) " (" (m :checksum) ")..."))
                                  (if (.endsWith (m :name) "clj")
                                    ; Pass the current db into the function returned by load-file
                                    ((load-file (str "migrations/" (m :name))) trans_db)
                                    (try
                                      (doseq [query ((assoc-migration-content m) :content )]
                                        (sql/db-do-commands trans_db query))
                                      (catch Exception e
                                        (print " FAIL")
                                        (println "")
                                        (println (str "Migration " (m :name) " failed to execute!"))
                                        (println "")
                                        (throw e))))
                                  (insert-migration-record trans_db m)
                                  (print " OK")
                                  (println "")))))))

(defn extract-invalid-check-results
  [results dbcoll]
  (let [invalid-results (filter #(= (second %) false) (zipmap dbcoll results))]
    (doseq [r invalid-results]
      (println (str ((first r) :subname) " => contains mismatched checksums.")))
    (println "Taking no action.")
    (System/exit 1)))

(defn preprocess-dbcoll
  [dbcoll]
  (doseq [db dbcoll] (create-migration-table db)))

(defn run-migrations
  [dbcoll migration-files-cksum]
  (preprocess-dbcoll dbcoll)
  (let [db-valid-results (map #(check-migrations % migration-files-cksum) dbcoll )]
    (if (every? true? db-valid-results)

      (let [now-ms (System/currentTimeMillis)]
        (doseq [db dbcoll]
          (run-new-migrations db migration-files-cksum)
          (println (str (db :subname) " => Completed in " (- (System/currentTimeMillis) now-ms) "ms" ))))

      (extract-invalid-check-results db-valid-results dbcoll))))

(defn run-on-dbcoll
  [dbcoll fun]
  (doseq [db dbcoll]
    (let [now-ms (System/currentTimeMillis)]
      (fun db)
      (println (str (db :subname) " => Completed in " (- (System/currentTimeMillis) now-ms) "ms" ))
      )))

(defn do-sync-migrations
  [db migration-files-cksum]
  (sql/db-transaction* db (fn [trans_db]
    (doseq [m (find-migrations-with-checksum-mismatch trans_db migration-files-cksum)]
      (sql/delete! trans_db :seql_migrations ["name = ?" m]))
    (let [migrations (map assoc-migration-content (find-migrations-to-run trans_db migration-files-cksum))]
      (if (empty? migrations)
        (println (str (trans_db :subname) " => Up to date"))
        (doseq [m migrations]
      (print (str (trans_db :subname) " => " (m :name) " (" (m :checksum) ")..."))
      (insert-migration-record trans_db m)
      (print "OK")
      (println (str ""))))))))


(defn do-sync-old-migrations
  [db migration-files-cksum]
  (sql/db-transaction* db (fn [trans_db]
    (let [migrations (map assoc-migration-content (find-migrations-to-run trans_db migration-files-cksum))]
      (if (empty? migrations)
        (println (str (trans_db :subname) " => Up to date"))
        (doseq [m migrations]
          (update-migration-record trans_db m)
          (print "OK")
          (println (str ""))))))))

(defn sync-migrations
  [dbcoll migration-files-cksum]
  (preprocess-dbcoll dbcoll)
  (run-on-dbcoll dbcoll (fn [db] (do-sync-migrations db migration-files-cksum))))

(defn sync-old-migrations
  [dbcoll migration-files-cksum]
  (preprocess-dbcoll dbcoll)
  (run-on-dbcoll dbcoll (fn [db] (do-sync-old-migrations db migration-files-cksum))))
