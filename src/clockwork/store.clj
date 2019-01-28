(ns clockwork.store)


(defprotocol Storage
  "Storage is how you create custom storage for your profiling results. In
  production, you'll want to use a persistent data store that is shared between
  multiple threads, with your database being a good example. Key-Value stores
  such as redis, foundationdb etc are also especially suited to miniprofiler
  storage, as they're fast and the interface required is very small."
  (save [this profile]
    "save takes a profile (which is effectively an arbitrary map), and stores it
     in storage. Note that the map isn't serialized, you'll have to do that
     logic yourself.  The key the map should be stored under can be found in it
     as `:id`")
  (fetch [this id]
    "fetch takes the Id asked for and returns the deserialized result from the
     specified session."))


(defrecord InMemoryStore [store]
  Storage
  (save [_ data]
    (swap! store assoc (:id data) data))
  (fetch [_ id]
    (get @store id)))


(defn in-memory-store []
  (->InMemoryStore (atom {})))
