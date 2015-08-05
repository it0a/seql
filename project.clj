(defproject seql "0.1.7"
  :description "schema migrations for the masses"
  :url "https://github.com/it0a/seql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main seql.core
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [pandect "0.5.2"]]
  :plugins      [[lein-bin "0.3.4"]]
  :aot [seql.core]
  :omit-source true
  :bin {:name "seql"})
