(ns clockwork.core
  "Dev notes:
  https://github.com/itsgoingd/clockwork/wiki/Development-notes

  Clockwork sample:
  https://gist.github.com/itsgoingd/89efad5ee42c0c594d89cd6b9942a3e3"
  (:import [java.util UUID]
           [org.slf4j LoggerFactory]
           [ch.qos.logback.classic Logger]
           [ch.qos.logback.core Appender])
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [ring.util.codec :as codec]

            [clockwork.store :as store]))


(def ^:dynamic *current-profiler* nil)
(defn initial-profiler-data []
  {:time     (System/currentTimeMillis)
   :timeline []
   :db       []
   :logs     []})


;;; Utils

(defn ns->ms
  "Difference between two nanosecond times in ms"
  [ns0 ns1]
  (/ (float (- ns1 ns0)) 1000000))


(defn ms->s
  "Convert ms to (float) seconds"
  [ms]
  (/ (double ms) 1000))


(defn uuid []
  (str (UUID/randomUUID)))

;;; Timing part

(defmacro trace
  "trace lets you wrap sections of your code so they show up as sections on a
  timeline."
  [section-name & body]
  `(if-not *current-profiler*
     (do ~@body)
     (let [t0# (System/currentTimeMillis)]
       (try
         (do ~@body)
         (finally
           (let [t1#       (System/currentTimeMillis)
                 duration# (- t1# t0#)
                 section#  {:description ~section-name
                            ;; start of this timeline is time in ms plus
                            ;; difference between current ns and ns at the start
                            ;; of that request
                            :start       (ms->s t0#)
                            :end         (ms->s t1#)
                            :duration    duration#}]
             (swap! *current-profiler* update :timeline
               conj section#)))))))


(defmacro timing
  "timing lets you mark various external requests such as queries to db"
  [type description & body]
  `(if-not *current-profiler*
     (do ~@body)
     (let [t0# (System/nanoTime)]
       (try
         (do ~@body)
         (finally
           (let [t1#    (System/nanoTime)
                 query# {:query    ~description
                         :model    ~type
                         :duration (ns->ms t0# t1#)
                         :trace    (-> (.getStackTrace (Thread/currentThread))
                                       ;; rest is because last call will be "getStackTrace"
                                       rest)}]
             (swap! *current-profiler* update :db
               conj query#)))))))


;;; Constructing

(defn parse-body-params [{:keys [body] :as req}]
  (when-not (nil? body)
    (.reset body)
    (case (get-in req [:headers "content-type"])
      "application/json"
      (-> body json/read-value)

      "application/x-www-form-urlencoded"
      (-> body slurp codec/form-decode)

      nil)))


(defn parse-frame [frame]
  (let [fname (.getFileName frame)]
    (if (and fname (re-find #"\.cljc?$" fname))
      {:file (-> (.getClassName frame)
                 (str/replace "_" "-")
                 (str/replace #"\$fn-.*" ""))
       :line (.getLineNumber frame)}
      {:file (str (.getClassName frame) "::" (.getMethodName frame))
       :line (.getLineNumber frame)})))


(defn parse-trace [data interesting?]
  (let [trace   (:trace data)
        drop?   (when interesting? (comp (complement interesting?) :file))
        parsed  (cond->> (map parse-frame trace)
                  drop? (drop-while drop?)
                  true  (into []))
        current (first parsed)]
    (if current
      (merge data current {:trace parsed})
      data)))


(defn e->log [e]
  {:message (.getFormattedMessage e)
   :time    (ms->s (.getTimeStamp e))
   :context (.getMDCPropertyMap e)
   :level   (str (.getLevel e))
   :trace   (vec (.getCallerData e))})



(defn -construct [{:keys [uri query-string headers] :as req} res data
                  {:keys [log-trace-p db-trace-p]}]
  (let [now      (System/currentTimeMillis)
        full-uri (str uri (some->> query-string (str "?")))]
    {:id               (uuid)
     :version          1
     :url              (str
                         (get headers "x-forwarded-proto" "http://")
                         (get headers "host")
                         full-uri)
     :uri              full-uri
     :headers          (into {}
                         (for [[k v] headers]
                           [k (if (sequential? v) v [v])]))
     :controller       (-> res meta :controller)
     :getData          (some-> query-string codec/form-decode)
     :postData         (parse-body-params req)
     :cookies          (into {}
                         (for [[k v] (:cookies req)]
                           [k (:value v)]))
     :time             (ms->s (:time data))
     :responseTime     (ms->s now)
     :responseDuration (- now (:time data))
     :responseStatus   (:status res)
     :memoryUsage      nil ;; FIXME: how do we even count that
     :databaseQueries  (mapv #(parse-trace % db-trace-p) (:db data))
     :databaseDuration (apply + (map :duration (:db data)))
     :cacheTime        0 ;; FIXME: how is this different from DB
     :timelineData     (reverse (:timeline data))
     :log              (mapv #(parse-trace % log-trace-p) (:logs data))}))


;;; Server part

(def ID-RE #"^([^/]+)/?$")
(def log-appender
  (reify Appender
    (doAppend [this e]
      (when *current-profiler*
        (swap! *current-profiler* update :logs conj
          (e->log e))))
    (getName [this] "clockwork appender")
    (setName [this new-name] :pass)))


(defn respond [store {:keys [stripped] :as req}]
  (let [[_ id] (re-find ID-RE stripped)
        data   (store/fetch store id)]
    (if data
      {:status 200
       :headers {"Access-Control-Allow-Origin" (get-in req [:headers "origin"] "")}
       :body   (json/write-value-as-string data)}
      {:status 404})))


(defn profile-request [app req opts]
  (binding [*current-profiler* (atom (initial-profiler-data))]
    (let [res  (app req)
          data (-construct req res @*current-profiler* opts)]
      (store/save (:store opts) data)
      (cond-> res
        (map? res)
        ;;; look at x-clockwork-subrequest
        ;;; also, what is x-clockwork-header-...?
        (update :headers assoc
          :X-Clockwork-Version "3.1.2"
          :X-Clockwork-Path    (:prefix opts)
          :X-Clockwork-Id      (:id data))))))


(def default-opts
  {:prefix           "/__clockwork/"
   :store            (store/in-memory-store)
   :authorized?      (fn [req] (= "localhost" (get-in req [:headers "host"])))
   :profile-request? (fn [req] true)})


(defn wrap
  "Ring middleware for clockwork.

   app - ring app
   opts:
     - :authorized? - check if current user is allowed to access profile information
     - :profile-request? - check if this request should be profiled
     - :store - object, satisfying clockwork.store/Storage protocol (default: in-memory)
     - :prefix - prefix to serve clockwork's responses under (default: /__clockwork/)"
  [app opts]
  (let [{:keys [prefix store authorized? profile-request?] :as opts}
        (merge default-opts opts)

        root (LoggerFactory/getLogger Logger/ROOT_LOGGER_NAME)]

    (assert (satisfies? store/Storage store) "Please pass valid :store")

    (.addAppender root log-appender)

    (fn [{:keys [uri] :as req}]
      (cond
        (not (authorized? req))
        (app req)

        (.startsWith uri prefix)
        (respond store (assoc req :stripped (.substring uri (count prefix))))

        (profile-request? req)
        (profile-request app req opts)

        :else
        (app req)))))
