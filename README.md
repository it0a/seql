# seql

migrations for the masses

## Build

With leiningen:
```bash
git clone https://github.com/it0a/seql && \
cd seql && \
lein bin
```
Will output a binary at target/seql (Add this to your PATH)

## Configuration

seql currently runs under the following heirarchy:
```
seql
migrations/migrations.clj
migrations/databases.clj
```

*migrations/migrations.clj*:
```clojure
{"0.0.1" ["1.sql"
          "2.sql"]
 "0.0.2" ["1.sql"]}
```

*migrations/databases.clj*:
```clojure
{"default" {:classname "com.mysql.jdbc.Driver"
            :subprotocol "mysql"
            :user "root"
            :password "rootpass"
            :host "0.0.0.0"
            :port "3306"
            :databases [{:schema "example"}
                        {:schema "example2"}
                        {:schema "example3"}
                        {:schema "example4"}
                        {:schema "example5"}]}
 "another" {:classname "com.mysql.jdbc.Driver"
            :subprotocol "mysql"
            :user "root"
            :password "rootpass"
            :host "0.0.0.0"
            :port "3306"
            :databases [{:schema   "example6"
                         :user     "anotheruser"
                         :password "anotherpassword"}
                        {:schema "example7"}
                        {:schema "example8"}
                        {:schema "example9"}
                        {:schema "example10"}]}}
```

Note that any properties specified in a `:databases` cell take precedence over the base properties.

In other words, `:schema "example6"` will connect with `:user "anotheruser"` and `:password "anotherpassword"`. whereas all other `:schema` will connect with `:user "root"` and `:password "rootpass"`.


## Usage

```
seql [db-groups...]
```

With the above databases.clj, we can run all migrations specified in migrations.clj against the 'default' and 'another' groups:

```
seql default another
```

## License

Copyright © 2015 it0a

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
