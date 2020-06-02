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

seql currently runs under the following hierarchy:
```
seql
migrations/migrations.clj
migrations/databases.clj
```

**migrations/migrations.clj**:
```clojure
[["0.0.1" ["1.sql"
           "2.sql"]]
 ["0.0.2" ["1.sql"]]]
```
This defines three files that will run in the following order:
```
migrations/0.0.1/1.sql
migrations/0.0.1/2.sql
migrations/0.0.2/1.sql
```

**migrations/databases.clj**:
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

Note that `:schema "example6"` will connect with `:user "anotheruser"` and `:password "anotherpassword"`. All other `:schema` will connect with `:user "root"` and `:password "rootpass"`.


## Usage

### Running Migrations

```
seql [db-groups...]
```

Using the above databases.clj:
```
seql default another
```
Will run migrations on all databases in the 'default' and 'another' groups.

### Only Run Recent Migrations
This assists in limiting time it takes to analyze large migration suites.

To limit running migrations for those created / edited in the last 60 days:

```
seql --lookback-days 60 [db-groups...]
```

**NOTES:**

* Use only when it is certain all of the oldest migrations have been executed.
* All migrations listed must still exist, lookback isn't applied to this check.
* `--lookback-days DAYS` can also be used with these options:
    * `--sync`
    * `--refresh`

### Refresh Migrations

To Update db migration entries' checksum to file migrations' checksum:

```
seql --refresh [db-groups...]
```

**NOTE:** Migrations not executed

### Synchronizing Migrations

To synchronize all migrations as having been run:

```
seql --sync [db-groups...]
```

**NOTE:** Doing this will also sync migrations with checksum mismatches that have already been run.

TODO: --sync --file "filename"

### Removing Migrations

TODO: --remove --file "filename" [db-groups]

### Arbitrary Code Execution

In v0.2.0, it is now possible to execute arbitrary code as part of your migration process.

Any migration files ending in ".clj" will be interpreted at migration time.

Example code migration to convert plaintext passwords to bcrypt hashes:

**migrations/migrations.clj**:
```clojure
    [["0.0.1" ["example.clj"]]]
```

**migrations/0.0.1/example.clj**:
```clojure
    (require '[clojure.java.jdbc :as sql])
    (require '[crypto.password.bcrypt :as bcrypt])

    (fn [db]
      (doseq [user (sql/query db ["SELECT id, username, password FROM users"]
                              :identifiers identity)]
        (sql/update! db :users
                     {:password (bcrypt/encrypt (user :password))}
                     ["id = ?" (user :id)])))
```

As the closure will be passed the database transaction at evaluation time,
queries inside .clj files will be wrapped in a transaction.


## Wiki

See the [project wiki](https://github.com/it0a/seql/wiki) for more details.

## License

Copyright © 2015 it0a

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
