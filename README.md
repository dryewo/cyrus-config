# cyrus-config
[![Build Status](https://travis-ci.org/dryewo/cyrus-config.svg?branch=master)](https://travis-ci.org/dryewo/cyrus-config)
[![codecov](https://codecov.io/gh/dryewo/cyrus-config/branch/master/graph/badge.svg)](https://codecov.io/gh/dryewo/cyrus-config)
[![Clojars Project](https://img.shields.io/clojars/v/cyrus/config.svg)](https://clojars.org/cyrus/config)

Almost statically typed REPL-friendly configuration library.

```clj
[cyrus/config "0.3.1"]
```

Many other configuration libraries just give you a map from keyword to string:

```clj
{:http-port   "8090"
 :db-user     "master"
 :db-password "foo123"}
```

This map is collected from various sources, but there is no support for you to actually check if these values
are present, have correct format, etc.

* What if a value is missing?
* What if it's not convertible to number or boolean?
* What if you want to pass a list in a variable?
* Can you remember all the configuration parameters that your application supports?
* How can you log all the configuration values when the application starts?
* How can you reload the configuration when you `(refresh)` your project in REPL?
* How can you unit-test application behavior with different configuration values?
 
This is what **cyrus-config** helps you with. Following [12 Factor App] manifesto, it only reads configuration 
from environment (not files, not command line arguments, not network services),
and then transforms, validates and summarizes it.

## Usage

*http.clj:*
```clj
(ns my.http
  (:require [cyrus-config.core :as cfg]
            [clojure.spec.alpha :as s]))

;; Introduce a configuration constant that will contain validated and transformed value from the environment
;; By default uses variable name transformed from the defined name: "HTTP_PORT"

(s/def ::port int?)
(cfg/def HTTP_PORT "Port to listen on" {:spec     ::port
                                        :default  8080})

;; Available immediately, without additional loading commands, but can contain a special value indicating an error.

(defn start-server []
  (server/start-server {:port http-port}))
```

*core.clj:*
```clj
(ns my.core
  (:require [cyrus-config.core :as cfg]
            [my.http :as http]))

(defn -main [& args]
  ;; Will throw if some configuration variables are invalid
  (cfg/validate!)
  (println "Config loaded:\n" (cfg/show))
  (http/start-server))
```

When started, it will print something like:

```
Config loaded:
#'my.http/HTTP_PORT: 8080 from HTTP_PORT in :enironment // Port to listen on
```

This library is also a part of [cyrus] Leiningen template:

```
$ lein new cyrus org.example/my-project +all
```

### Reference

#### Defining

`(cfg/def FOO_BAR <optional-docstring> <optional-parameter-map>)` — defines a configuration constant (which is an ordinary var,
like normal `def` does), assigns its value according to parameters.
Additionally, metadata is set that contains all parameters, raw value, source (`:environment`, `:override`, `:default`) and error.
This metadata is used in `(cfg/show)`.

```clj
;; Assuming that HTTP_PORT environment variable contains "8080"
(cfg/def HTTP_PORT {:spec int? :required true})
HTTP_PORT
=> 8080
(meta #'HTTP_PORT)
=> {::cfg/user-spec      {:spec :my.http/port}
    ::cfg/effective-spec {:required true
                          :default  nil
                          :secret   false
                          :var-name "HTTP_PORT"
                          :spec     :my.http/port}
    ::cfg/source         :environment
    ::cfg/raw-value      "8080"
    ::cfg/error          nil
    ...}

;; Like with normal def, only the name is mandatory
(cfd/def DB_USERNAME)

;; Docstring behaves the same way as for normal def
(cfg/def DB_PASSWORD "DB password" {:secret true})

;; Parameter map is optional
(cfg/def DB_URL "DB URL")

;; Variable name can be explicitly specified
(cfg/def DESC {:var-name "DESCRIPTION"}) 
```

Parameters (all are optional):

* `:var-name` — string or keyword, environment variable name to get the value from. Automatically converted to ENV_CASE string.
  Defaults to `"FOO_BAR"` (according to the constant's name).
* `:required` — boolean, if the environment variable is not set, an error will be thrown during `(cfg/validate!)`. The constant
will silently get a special value that indicates an error, for example:
    ```clj
    (cfg/def REQUIRED_1 {:required true})
    REQUIRED_1
    => #=(cyrus_config.core.ConfigNotLoaded. {:code    :cyrus-config.core/required-not-present
                                              :message "Required not present"})

    ```
* `:default` — default value to use if the variable is not set. Cannot be used together with `:required true`. Defaults to `nil`.
* `:spec` — Clojure Spec to conform the value to. Defaults to string. Specs must be provided as fully-qualified registered keywords. See Conforming/Coercing section below. 
* `:schema` — Prismatic Schema to coerce the value to (same as `:spec`, but for Prismatic). Complex schemas first parse the value as YAML.
* `:secret` — boolean, if true, the value will not be displayed in the overview returned by `(cfg/show)`:
    ```
    #'my.db/DB_PASSWORD: <SECRET> from DB_PASSWORD in :environment // Database password
    ```

You can also use existing configuration constants' values when defining configuration constants:

```clj
(s/def ::server-polling-interval int?)
(cfg/def SERVER_URL)

;; This constant will only be required if SERVER_URL is set
(cfg/def SERVER_POLLING_INTERVAL {:required (some? SERVER_URL) :spec ::server-polling-interval})

;; This will get default value from SERVER_POLLING_INTERVAL, when it's set (it also has a different type)
(cfg/def SERVER_POLLING_DELAY {:default SERVER_POLLING_INTERVAL})
```

#### Validation

`(cfg/validate!)` — checks if there were any errors during config loading, throws an `ex-info` with their description.
If everything is ok, does nothing.
The output looks like this:

```
               my.core.main                
                        ...                
              my.core/-main  core.clj:   21
              my.core/-main  core.clj:   32
cyrus-config.core/validate!  core.clj:  146
       clojure.core/ex-info  core.clj: 4739
clojure.lang.ExceptionInfo: Errors found when loading config:

                            #'my.http/HTTP_PORT: <ERROR> because HTTP_PORT contains "abcd" in :environment - clojure.lang.ExceptionInfo: Failed to coerce value {:spec :my.http/port, :value "abcd"} // Port to listen on
```

#### Summary

`(cfg/show)` — returns a formatted string containing information about all defined and loaded configuration constants.
The return value looks like this:

```
#'my.nrepl/NREPL_BIND: "0.0.0.0" from NREPL_BIND in :default "0.0.0.0" // NREPL network interface
#'my.nrepl/NREPL_PORT: 55000 from NREPL_PORT in :environment // NREPL port
#'my.db/DB_PASSWORD: <SECRET> because DB_PASSWORD is not set // Password
#'my.db/DB_USERNAME: "postgres" from DB_USERNAME in :default "postgres" // Username
#'my.db/JDBC_URL: "jdbc:postgresql://localhost:5432/postgres" from DB_JDBC_URL in :default "jdbc:postgresql://localhost:5432/postgres" // Coordinates of the DB
#'my.authenticator/TOKENINFO_URL: nil because TOKENINFO_URL is not set // URL to check access tokens against. If not set, tokens won't be checked.
#'my.http/HTTP_PORT: 8090 from HTTP_PORT in :environment // Port for HTTP server to listen on
```

It's recommended to print it from `-main`:

```clj
(defn -main [& args]
  ;; By the time -main starts, all the config is already loaded. Here we only find out about errors. 
  (cfg/validate!)
  (println "Config loaded:\n" (cfg/show))
  ...)

```

#### REPL support

`(reload-with-override! <env-map>)` — reloads all configuration constants from a merged source:

    (merge (System/getenv) <env-map>)


For REPL-driven development it's recommended to have it in a wrapper for `(refresh)`:

*dev/user.clj:*
```clj
(defn load-dev-env []
  (edn/read-string (slurp "./dev-env.edn")))

(defn refresh []
  (cfg/reload-with-override! (load-dev-env))
  (cfg/validate!)
  (clojure.tools.namespace.repl/refresh))
```

This will ensure that every time the code is reloaded, the overrides file `dev-env.edn` is also re-read.

### Conforming/Coercion

The library supports two ways of conforming (a.k.a. coercing) environment values (which are always string) to
various types: integer, keyword, double, etc. The ways of defining target types are Clojure Spec and Prismatic Schema.
They are mutually exclusive, i.e. only one of `:spec` and `:schema` keys are possible at the same time for each configuration constant.

#### Clojure Spec

> [spec] is a Clojure library to describe the structure of data and functions.

The Clojure Spec coercion implementation is provided by [spec-coerce](https://github.com/wilkerlucio/spec-coerce). It is enabled by setting `:spec` key in the parameters:

```clj
(s/def ::port int?)
(cfg/def HTTP_PORT {:spec ::port})
```

Implicit coercion is in place for basic types: `int?`, `double?`, `boolean?`, `keyword?`, `string?` (the default one does nothing). But note that you must provide the spec in the form of a registered, fully-qualified keyword as defined by `s/def`. It's expected that you will be defining these on your own (as in the example of `::port` above), but if you'd rather not, there are some stand-in specs for the basic types located under `cyrus-config.coerce`:
- :cyrus-config.coerce/int
- :cyrus-config.coerce/double
- :cyrus-config.coerce/keyword
- :cyrus-config.coerce/string
- :cyrus-config.coerce/boolean

##### Custom coercions

`:cyrus-config.coerce/nonblank-string` spec is included, it conforms blank string to `nil`:

    EXTERNAL_SERVICE_URL=""

```clj
(require '[cyrus-config.coerce :as cfgc])

(cfg/def EXTERNAL_SERVICE_URL {:spec ::cfgc/nonblank-string})

(when-not EXTERNAL_SERVICE_URL
  (log/warn "EXTERNAL_SERVICE_URL is not set, will not try to access!"))
```

Without `::cfgc/nonblank-string` you would need to check for blankness of the string every time you use it:

```clj
(when (str/blank? EXTERNAL_SERVICE_URL)
  (log/warn "EXTERNAL_SERVICE_URL is not set, will not try to access!"))
```

Additionally, you can put a complex value in EDN format into the variable:

    IP_WHITELIST='["1.2.3.4" "4.3.2.1"]'

and then define how to coerce it using spec-coerce's `def` form:

```clj
(require '[spec-coerce.core :as sc]
         '[clojure.spec.alpha :as s]
         '[clojure.edn :as edn])
(s/def ::ip-whitelist (s/coll-of string?))
(sc/def ::ip-whitelist edn/read-string)
(cfg/def IP_WHITELIST {:spec ::ip-whitelist})
IP_WHITELIST
=> ["1.2.3.4" "4.3.2.1"]
;; ^ not a string, a Clojure data structure
```

Alternatively, you can use JSON format:

    IP_WHITELIST='["1.2.3.4", "4.3.2.1"]'

```clj
(require '[cheshire.core :as json])
(sc/def ::ip-whitelist json/parse-string)
```

Or any other custom conversion:

    IP_WHITELIST='1.2.3.4, 4.3.2.1'

```clj
(defn parse-csv [csv]
  (if (sequential? csv)
    csv
    (->> (str/split (str csv) #",")
         (map str/trim))))

(sc/def ::ip-whitelist parse-csv)
```

`if (sequential? csv)` condition is important, it allows to provide `:default` not only as string, but also as target type:

```clj
(cfg/def IP_WHITELIST {:spec ::ip-whitelist :default ["one" "two"]})
```

Note also that spec-coerce honors the "first" or "left-most" spec condition of `s/and` specs for coercion. So, when using one of the basic predicates (like `keyword?` or `int?`) for coercing, you can specify the general coercion spec first and then the more granular constraints afterward to avoid the need for an `sc/def` line:

```clj
(s/def ::my-number (s/and int? (s/int-in 0 2)))
;; Is the same as:
(s/def ::my-number (s/int-in 0 2))
(sc/def ::my-number sc/parse-long)
```

See [spec-coerce's usage](https://github.com/wilkerlucio/spec-coerce#usage) for more details and options.

NOTE: Taking this approach may hamper your ability to generate sample data from these specs. `::my-number` will sometimes fail to generate after 100 tries, because the `int?` case is very broad and the `s/int-in` case is very narrow. It seems spec's generator logic also reads left-to-right in this sense. 

A better solution to the specific `::my-number` example above would be to use a homogeneous set:

```clj
(s/def ::my-number #{0 1})
```

spec-coerce will infer from this set that it needs to parse as an integer. 

#### Prismatic Schema

> [Prismatic Schema]: A Clojure(Script) library for declarative data description and validation.

It is enabled by setting `:schema` key in the parameters:

```clj
(cfg/def HTTP_PORT {:schema s/Int})
```

Also, it's necessary to include `[squeeze "0.3.2]` library in project dependencies to use `:schema` key.

Besides supporting all atomic types (`s/Int`, `s/Num`, `s/Keyword`, etc.), complex types can be coerced:

```
IP_WHITELIST=$(cat <<EOF
- 1.2.3.4
- 4.3.2.1
EOF
)
FOO='foo:\n  bar: 1\n  baz: 42'
```

And then define the config constants as following:

```clj
(cfg/def IP_WHITELIST {:spec [s/Str]})
IP_WHITELIST
=> ["1.2.3.4" "4.3.2.1"]

(cfg/def FOO {:schema {:foo {:bar                  s/Str 
                             :baz                  s/Int
                             (s/optional-key :opt) s/Keyword}}})
FOO
=> {:foo {:bar "1" 
          :baz 42}}

```

Additionally, when using REPL-driven development, you can provide unstringified values in the overrides:

```clj
(cfg/reload-with-override! {"IP_WHITELIST" ["1.2.3.4" "4.3.2.1"]
                            "FOO"          {:foo {:bar "1" 
                                                  :baz 42}}})
```

## Rationale

Let's assume we are following [12 Factor App] manifesto and read the configuration only from environment variables.

Imagine you have a HTTP server that expects a port:

```clj
(defn start-server []
  (server/start-server {:port (System/getenv "HTTP_PORT")}))
```

But the HTTP server library expects an integer, not a string:

```clj
(defn start-server []
  (server/start-server {:port (Integer/parseInt (System/getenv "HTTP_PORT"))}))
```

We need a default value in case when `HTTP_PORT` is not set:

```clj
(defn start-server []
  (server/start-server {:port (or (some-> (System/getenv "HTTP_PORT") (Integer/parseInt)) 8080)}))
```

Environment variables cannot be changed when the process is running. In order to make our application testable
 (for example, to try starting the server on different ports), we need an abstraction layer.
For example, we can read the entire environment to a map and then redefine it for tests:

```clj
(def ^:dynamic environment (System/getenv))

(defn start-server []
  (server/start-server {:port (Integer/parseInt (get environment "HTTP_PORT"))}))

(deftest test-start-server
  (alter-var-root #'environment (constantly {"HTTP_PORT" "7777"})))
  (start-server)
  ...
  (stop-server)
```

This can be further improved by keeping the original environment intact and changing only the overriding:

```clj
(def environment (System/getenv))
(def ^:dynamic env-override {})

(defn reload-overrides! [overrides]
  (alter-var-root #'env-override (constantly overrides)))

(defn get-config [var-name]
  (get (merge environment env-override) var-name))

(defn start-server []
  (server/start-server {:port (Integer/parseInt (get-config "HTTP_PORT")}))
```

If you do REPL-driven development, overrides can be read from a file and applied without restarting the REPL:

```clj
(reload-overrides! (clojure.edn/read-string (slurp "dev-env.edn")))
```

It is a lot of hassle already, while we just wanted to read one simple value and have some basic REPL support.
This solution still has drawbacks:

* If the variable is used in many places, its name has to be repeated, which adds risk of typos ("HTPP_PORT").
* Errors are found late, only when first accessing the value.
* Errors are only given one at a time: if the application fails to start because of one invalid variable, it does not check
  other values which are needed later.
* We might want to have a print a summary of configuration values when the application starts.
    * Some variables should not be printed (passwords, keys, etc.)

This list can be continued, and the requirements are common to many projects, hence this library.

## License

Copyright © 2017 Dmitrii Balakhonskii

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[12 Factor App]: https://12factor.net/
[spec]: https://github.com/clojure/spec.alpha
[Prismatic Schema]: https://github.com/plumatic/schema
[cyrus]: https://github.com/dryewo/cyrus
