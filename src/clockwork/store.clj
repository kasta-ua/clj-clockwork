(ns clockwork.store)


(defprotocol Storage
  "Storage is how you create custom storage for your profiling results. In
  production, you'll want to use a persistent data store that is shared between
  multiple threads, with your database being a good example. Key-Value stores
  such as redis, foundationdb etc are also especially suited for storage, as
  they're fast and the interface required is very small."
  (save [this id data]
    "save takes `id` and `data` to store (which is a string with an actual
    serialized data inside) and puts it into a storage.")
  (fetch [this id]
    "fetch takes the `id` asked for and returns the resulting string from the
    storage."))


(defrecord InMemoryStore [store]
  Storage
  (save [_ id data]
    (swap! store assoc id data))
  (fetch [_ id]
    (get @store id)))


(defn in-memory-store []
  (->InMemoryStore (atom {})))
