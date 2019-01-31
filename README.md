# clj-clockwork

Clockwork in a browser extension for [Chrome][] and [Firefox][] devtools,
providing tools for debugging and profiling your application. This library is a
Clojure backend counterpart for the extension.

[Chrome]: https://github.com/itsgoingd/clockwork-chrome/
[Firefox]: https://github.com/itsgoingd/clockwork-firefox/

[![Clojars Project](https://img.shields.io/clojars/v/ua.kasta/clockwork.svg)](https://clojars.org/ua.kasta/clockwork)

## Usage

There are two parts to clockwork: one is a Ring middleware and another is a
couple of macros.


### Middleware

It's initialialized by wrapping your app in `clockwork.core/wrap`:

```
(def app (clockwork.core/wrap my-handler 
           {:authorized? (fn [req] (user/admin? req))}))
```

There are a couple of options for the middleware:

- `:authorized?` - this checks if a current user can access profiling
  information (by default all requests to localhost are allowed)
- `:profile-request?` - this checks if a request should be profiled: skipping
  static and media data seems logical (by default all authorized requests are
  profiled, except for requests for clockwork data)
- `:prefix` - prefix for all internal Clockwork requests (by default it's
  `/__clockwork/`)
- `:store` - store for Clockwork data (by default in-memory, for details see
  lower)
  
  
### Timing

There are two types of timing information: `trace` and `timing`. 

`trace` is a section of your code, where something happens: like request
parsing, or maybe template rendering, etc - it shows up on nested timeline. 

Signature: `(trace section-name & body)`

Example:

```
(clockwork/trace (str "app " (:uri req))
  (my-handler req))
```

`timing` is a call to some (external) resource, like database, cache lookup or
internal http request. It shows in a `DB` tab. 

Signature: `(timing type description & body)`

Example:

```
(defn q [query]
  (clockwork/timing "pg" (pr-str query) 
      (jdbc/query db-conn (query->args query))))
      
(defn cache-get [key]
  (clockwork/timing "cache get" key (.get memcache key)))
```
