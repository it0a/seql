(ns seql.core
  (:require
   [seql.sql :as sql]
   [seql.files :as files]
   [clojure.pprint :as pprint]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-g" "--generate" "Generate"]
   ["-h" "--help"]])

(defn display-help
  []
  (println "Usage: seql [db-groups...]")
  (println "Runs all migrations listed in migrations/migrations.clj")
  (println "against specified db-groups from migrations/databases.clj"))

(defn display-sample-migration-file
  []
  (pprint/pprint {"0.0.1" ["1.sql" "2.sql"] "0.0.2" ["1.sql"]}))

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
      ((opt-map :options) :help) (display-help)
      ((opt-map :options) :generate)
        (if (empty? (opt-map :arguments))
            (println "Nothing specified to generate. (Ex: migration-file, databases-file)")
            (doseq [gen-target (opt-map :arguments)]
              (cond
                (= gen-target "migration-file") (display-sample-migration-file)
                (= gen-target "databases-file") (display-sample-databases-file))))
      :else (if (empty? (opt-map :arguments))
        (println "no database groups")
        (doseq [db-group (opt-map :arguments)]
          (println (str "running migrations on database group '" db-group "'..."))
          (sql/run-migrations (files/load-database-group db-group)))))))
