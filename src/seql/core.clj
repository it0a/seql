(ns seql.core
  (:require
   [seql.sql :as sql]
   [seql.files :as files]
   [clojure.pprint :as pprint]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-s" "--sync" "Remove db entries whose checksum doesn't match it's file migration checksum, then run all available migrations"]
   ["-r" "--refresh" "Update db migration entries' checksum to file migrations' checksum; migrations not executed"]
   ["-g" "--generate" "Generate sample scripts"]
   [nil "--lookback-days DAYS" "Limit migration results for last number of days (-1 for all)"
     :default -1
     :parse-fn #(Integer/parseInt %)
     :validate [#(>= % -1) "Must be greater than or equal to -1"]]
   ["-h" "--help"]])

(defn error-msg
    [errors]
  (println "The following errors occurred while parsing your command:")
  (println "")
  (println (string/join \newline errors)))

(defn display-help
  [options-summary]
  (println "Usage: seql [db-groups...]")
  (println "Runs migrations listed in migrations/migrations.clj")
  (println "against specified db-groups from migrations/databases.clj")
  (println "")
  (println "Options:")
  (println options-summary))

(defn display-sample-migration-file
  []
  (pprint/pprint [["0.0.1" ["1.sql" "2.sql"]] ["0.0.2" ["1.sql"]]]))

(defn display-sample-databases-file
  []
  (pprint/pprint {"default" {:classname "com.mysql.jdbc.Driver"
                             :subprotocol "mysql"
                             :user "root"
                             :password "pass"
                             :host "127.0.0.1"
                             :port "3306"
                             :databases [{:schema "dbname1"}
                                         {:schema "dbname2"}
                                         {:schema "dbname3"}]}
                  "another" {:classname "com.mysql.jdbc.Driver"
                             :subprotocol "mysql"
                             :user "root"
                             :password "pass"
                             :host "127.0.0.1"
                             :port "3306"
                             :databases [{:schema "dbname4"}
                                         {:schema "dbname5"
                                          :port "3309"
                                          :host "0.0.0.0"
                                          :user "anotheruser"}]}}))

(defn -main [& args]
  (let [opt-map (parse-opts args cli-options)]
    (cond
      ((opt-map :options) :help) (display-help (opt-map :summary))
      (not-empty (opt-map :errors)) (error-msg (opt-map :errors))

      ((opt-map :options) :generate)
      (if (empty? (opt-map :arguments))
        (println "Nothing specified to generate. (Ex: migration-file, databases-file)")
        (doseq [gen-target (opt-map :arguments)]
          (cond
            (= gen-target "migration-file") (display-sample-migration-file)
            (= gen-target "databases-file") (display-sample-databases-file))))

      :else
      (let [lookback-days (get (get opt-map :options) :lookback-days)
            migration-files-cksum (files/load-migrations "migrations/migrations.clj" lookback-days)]
        (cond
          ((opt-map :options) :sync)
          (if (empty? (opt-map :arguments))
            (println "no database groups to sync")
            (doseq [db-group (opt-map :arguments)]
              (println (str "synchronizing migrations on db-group '" db-group "'..."))
              (sql/sync-migrations
                (files/load-database-group db-group) migration-files-cksum)))

          ((opt-map :options) :refresh)
          (if (empty? (opt-map :arguments))
            (println "no database groups to refresh checksums on")
            (doseq [db-group (opt-map :arguments)]
              (println (str "Refreshing existing migrations on db-group '" db-group "'..."))
              (sql/sync-old-migrations
                (files/load-database-group db-group) migration-files-cksum)))

          :else
          (if (empty? (opt-map :arguments))
            (println "no database groups")
              (doseq [db-group (opt-map :arguments)]
                (println (str "running migrations on db-group '" db-group "'..."))
                (sql/run-migrations
                  (files/load-database-group db-group) migration-files-cksum))))))))
