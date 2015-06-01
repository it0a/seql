(ns anemia.files
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:require [pandect.algo.sha256 :as sha256])
  (:import [java.io PushbackReader]))

(defn read-migration-file-list
  [filename]
  (with-open [r (io/reader filename)]
    (read (PushbackReader. r))))

(defn extract-migration-file-names [migration-file-list]
  (vec (flatten (map (fn [key] (map #(str key "/" %) (migration-file-list key)))
    (keys migration-file-list)))))

(defn validate-migration-files
  [migration-files]
  (zipmap migration-files (map #(.exists (io/as-file (str "migrations/" %))) migration-files)))

(defn read-migration-file
  [filename]
  (io/file (str "migrations/" filename)))

(defn compute-checksum
  [filename]
  (sha256/sha256-file (read-migration-file filename)))

(defn print-invalid-files
  [invalid-files]
  (println "Invalid migrations defined:")
  (map #(println (first %)) invalid-files))

(defn process-migration-files
  [migration-files]
  (map #(assoc nil :name (first %) :checksum (second %)) (zipmap migration-files (map compute-checksum migration-files))))

(defn read-migrations
  [migration-files]
  (let [invalid-files (filter #(= (second %) false) (validate-migration-files migration-files))]
   (if (> (count invalid-files) 0)
     (print-invalid-files invalid-files)
     (process-migration-files migration-files))))

(defn load-migrations
  [migrations-file]
  (read-migrations (extract-migration-file-names (read-migration-file-list migrations-file))))

(defn load-migration-content
  [filename]
  (slurp (str "migrations/" filename)))

;(map load-migration-content (map #(str "migrations/" %) (keys (read-migrations (extract-migration-file-names
; (read-migration-file-list "migrations/migrations.clj"))))))
