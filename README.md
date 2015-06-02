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

**migrations/migrations.clj**:
```clojure
{"0.0.1" ["1.sql"
          "2.sql"]
 "0.0.2" ["1.sql"]}
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

```
seql [db-groups...]
```

With the above databases.clj, we can run all migrations specified in migrations.clj against the 'default' and 'another' groups:

```
./seql default another
running migrations on db-group 'default'...
//0.0.0.0:3306/example => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example2 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example2 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example2 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example3 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example3 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example3 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example4 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example4 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example4 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example5 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example5 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example5 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
running migrations on db-group 'another'...
//0.0.0.0:3306/example6 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example6 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example6 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example7 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example7 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example7 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example8 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example8 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example8 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example9 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example9 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example9 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
//0.0.0.0:3306/example10 => 0.0.1/1.sql (1aa7f420ae87538528dc1566d53afae8a14cce9b2313a676adec4f9f5d8aff3d)... OK
//0.0.0.0:3306/example10 => 0.0.1/2.sql (53dc5e0a801c1ce6197f434d7ea67f4eaba38ae7b9aca19b74e5b256cbb9cc05)... OK
//0.0.0.0:3306/example10 => 0.0.2/1.sql (3448126addae59ba2d03ea16763364b40bc025d4ed441ea1cd33fec3735b6836)... OK
```

## License

Copyright © 2015 it0a

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
