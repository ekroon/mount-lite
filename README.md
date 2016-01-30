# Mount lite

I like [Mount](https://github.com/tolitius/mount), a lot. But

* I don't need ClojureScript support (and its CLJC mode, as the [rmap library](https://github.com/aroemers/rmap) already provides lazily loading systems/states),
* I think it is cleaner when using meta data directly in the state vars (which is how Mount used to work internally),
* I don't need suspending (or [other](https://github.com/tolitius/mount/issues/16)
  [features](https://github.com/tolitius/mount/blob/dc5c89b3e9a47601242fbc79846460812f81407d/src/mount/core.cljc#L301)) -
  I'd like a library like this to be minimal, and
* I wanted a more composable and data-driven API (see [this mount issue](https://github.com/tolitius/mount/issues/19)
  and [this presentation](https://www.youtube.com/watch?v=3oQTSP4FngY)).

Mount Lite is Clojure only, offers no suspending, has a flexible API, substitutions are supported
(and somewhat cleaner in my opinion, but Mount may [get there](https://github.com/tolitius/mount/issues/45) as well) and states stop
automatically whenever they are redefined (just like Mount, but with Mount Lite this can be disabled per state,
something Mount may [have](https://github.com/tolitius/mount/issues/36) in the future as well). That's it.

You like it? Feel free to use it. Don't like it? The original Mount is great!

> Master branch: [![Circle CI](https://circleci.com/gh/aroemers/mount-lite/tree/master.svg?style=svg)](https://circleci.com/gh/aroemers/mount-lite/tree/master)

## Usage

Put this in your dependencies `[functionalbytes/mount-lite "0.9.1"]` and make sure Clojars is one of your repositories.
Also make sure you use Clojure 1.7+, as the library uses transducers.
Read on for a description of the library functions, or go straight to the [API docs](http://aroemers.github.io/mount-lite/index.html).

### Global states, starting and stopping

First, require the `mount.lite` namespace:

```clj
(ns your.app
  (:require [mount.lite :refer (defstate state only except substitute) :as mount]
            [your.app.config :as config] ;; Also has a defstate defined.
            [some.db.lib :as db]))
```

The simplest of a global state definition is one with a name and a `:start` expression. In this example we also supply a
`:stop` expression.

```clj
(defstate db
  :start (db/start (get-in config/config [:db :url]))
  :stop (do (println "Stopping DB...") (db/stop db)))
;=> #'your.app/db
```

> Consider the following in your design when using Mount:
>
> * Only use defstate in your application namespaces, not in library namespaces.
> * Only use defstate when either the state needs some stop logic before it can be reloaded,
>   or whenever the state depends on another defstate. In other cases, just use a def.

To start all global states, just use `start`. A sequence of started state vars is returned. The order in which the
states are started is determined by their load order by the Clojure compiler. Using `stop` stops all the states in
reverse order.

```clj
(mount/start)
;=> (#'your.app.config/config #'your.app/db)

db
;=> object[some.db.Object 0x12345678]
```

Whenever you redefine a global state var, by default the current state is stopped automatically. The following example
also shows that one can use metadata, document strings, attribute maps and a `:on-reload` key (explained below).

```clj
(defstate ^:private db
  "My database state"
  {:attribute 'map}
  :start (db/start (get-in config/config [:db :url]))
  :stop (do (println "Stopping db...") (db/stop db))
  :on-reload :lifecycle)
;>> Stopping DB...
;=> #'your.app/db

db
;=> object[mount.lite.Unstarted 0x12345678 "State #'your.app/db is not started."]
```

There may be cases where you don't want a redefined state stopping automatically. To alter this behaviour, a `:on-reload` option can be given to a state. The following values are supported:

* `:stop` - This is the default, as described above.
* `:lifecycle` -  This will just redefine the lifecycle functions on a reload, and keep the state running as is (including its original `:stop` expression and `:on-reload` configuration).
* `:cascade` - This will stop all the status "up to" the state being redefined. In other words, all states that might depend on the state in question, are stopped as well.


### Substitute states

Whenever you want to mock a global state when testing, you can define anonymous `state` maps, and pass this to the
`start` function using the `substitute` function (or with plain data, as described in the next section).

```clj
(mount/start (substitute #'db (state :start (do (println "Starting fake DB") (atom {}))
                                     :stop (println "Stopping fake DB"))))
;>> Starting fake DB
;=> (#'your.app/db)

db
;=> object[clojure.lang.Atom 0x2010a30b {:status :ready, :val {}}]

(mount/stop)
;>> Stopping fake DB
;=> (#'your.app/db #'your.app.config/config)
```

After a substituted state is stopped, it is brought back to its original definition. Thus, starting the state var again,
without a substitute configured for it, will start the original definition.

Note that substitution states don't need to be inline and the `state` macro is also only for convenience.
For example, the following is also possible:

```clj
(mount/start (substitute #'db {:start (constantly (atom {}))}))
```

### Only, except and other start/stop options

The `start` and `stop` functions can take one or more option maps (as we have done already actually, with the
substitutions above). The combination of these option maps make up a single options map, influencing what global states
should be started or stopped, and, as we have seen already, which states should be substituted (in case of `start`).

These option maps support four keys, and are applied in the following order:

* `:only` - A collection of the state vars that should be started or stopped (if not already having that status).

* `:except` - A collection of the state vars that should not be started or stopped.

* `:up-to` - A defstate var until which the states are started or stopped. In case multiple option maps are supplied,
  only the last `:up-to` option is used.

* `:substitute` - A map of state vars to substitute states, only applicable for `start`.

The functions `only`, `except`, `up-to` and `substitute` create or update such option maps, as a convenience. These functions can
be threaded, if that's your style, but you don't need to, as both `start` and `stop` take multiples of these option
maps. For example, these groups of expressions mean the same:

```clj
(mount/start {:only [#'db]})
(mount/start (only #'db))

(mount/start {:except [#'db] :substitute {#'your.app.config/config my-fake-config}})
(mount/start (-> (except #'db) (substitute #'your.app.config/config my-fake-config)))

(mount/start {:only [#'db]} {:only [#'your.app.config/config]})
(mount/start (only #'db) (only #'your.app.config/config))
(mount/start (-> (only #'db) (only #'your.app.config/config)))
(mount/start (only #'db #'your.app.config/config))
```

And this shows how `up-to` works:

```clj
(defstate a :start nil)
(defstate b :start nil)
(defstate c :start nil)

(start (up-to #'b))
;=> (#'user/a #'user/b)

(start)
;=> (#'user/c)

(stop (up-to #'b))
;=> (#'user/c #'user/b)
```

While the functions offer a convenient, readable and composable API, all of it is data driven. Your (test) configuration
can be stored anywhere, such as your `user.clj` file or in an EDN data resource.

Whatever your style or situation, enjoy!

## License

Copyright © 2016 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
