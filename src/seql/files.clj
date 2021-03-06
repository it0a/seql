(ns seql.files
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:require [pandect.algo.sha256 :as sha256])
  (:import [java.io PushbackReader]
           [java.time LocalDate]))

(defn read-migration-file-list
  [filename]
  (with-open [r (io/reader filename)]
    (read (PushbackReader. r))))

(defn extract-migration-file-names
  [migration-file-list]
  (vec (flatten (map (fn [x] (map #(str (first x) "/" %) (rest x)))
       (map flatten migration-file-list)))))

(defn- within-lookback?
  [migration-file lookback-days]
  (if (> lookback-days -1)
    (>= (.lastModified migration-file) (* (.toEpochDay (.minusDays (LocalDate/now) lookback-days)) 86400000))
    true))

(defn- filter-migration-files-by-lookback-days
  [migration-files lookback-days]
  (filter #(within-lookback? (io/as-file (str "migrations/" %)) lookback-days) migration-files))

(defn validate-migration-files
  [migration-files]
  (zipmap migration-files (map #(.exists (io/as-file (str "migrations/" %))) migration-files)))

(defn read-migration-file
  [filename]
  (str/replace (slurp (str "migrations/" filename)) "\r\n" "\n"))

(defn compute-checksum
  [filename]
  (sha256/sha256 (read-migration-file filename)))

(defn print-invalid-files
  [invalid-files]
  (println "invalid migrations defined:")
  (map #(println (first %)) invalid-files))

(defn process-migration-files
  [migration-files]
  (mapv #(assoc {} :name % :checksum (compute-checksum %)) migration-files))

(defn read-migrations
  [migration-files lookback-days]
  (let [invalid-files (filter #(= (second %) false) (validate-migration-files migration-files))]
    (if (> (count invalid-files) 0)
      (print-invalid-files invalid-files)
      (process-migration-files (filter-migration-files-by-lookback-days migration-files lookback-days)))))

(defn load-migrations
  [migrations-file lookback-days]
  (read-migrations
    (extract-migration-file-names (read-migration-file-list migrations-file))
    lookback-days))

(defn load-migration-content
  [filename]
  (filter (complement str/blank?)
    (str/split
     (str/replace (read-migration-file filename) "\r\n" "\n") #";\s*\n")))

(defn load-databases-file
  [filename]
  (with-open [r (io/reader filename)]
    (read (PushbackReader. r))))

(defn flatten-db-spec
  "Flattens the :databases element into the base database specification."
  [db-spec]
  (let [base-spec (dissoc db-spec :databases)]
    (map #(conj base-spec %) (db-spec :databases))))

(defn convert-to-jdbc-spec
  [db-spec]
  (apply dissoc (assoc db-spec :subname
                       (str "//" (db-spec :host) ":" (db-spec :port) "/" (db-spec :schema))) [:host :port :schema]))

(defn load-database-group
  [db-group]
  (let [loaded-databases ((load-databases-file "migrations/databases.clj") db-group)]
    (if (empty? loaded-databases)
      (println (str "No databases belong to db-group '" db-group "'"))
      (map convert-to-jdbc-spec (flatten-db-spec loaded-databases)))))
