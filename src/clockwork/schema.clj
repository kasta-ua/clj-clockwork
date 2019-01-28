(ns clockwork.schema
  "work-in-progress"
  (:require [clojure.spec.alpha :as s]))


(s/def :query/query string?)
(s/def :query/duration number?)
(s/def :query/connection string?)
(s/def :query/model string?)
(s/def ::db-query
  (s/keys :req-un [:query/query :query/duration :query/connection]
          :opt-un [:query/model]))

(s/def :log/message (s/or :message string?
                          :object  map?))
(s/def :log/level #{"debug" "info" "notice" "warning" "error"})
(s/def :log/time number?)
(s/def :log/context map?)
(s/def :log/file string?)
(s/def :log/line int?)
(s/def ::log
  (s/keys :req-un [:log/message :log/level :log/time]
          :opt-un [:log/context :log/file :log/line]))


(s/def :item/start number?)
(s/def :item/end number?)
(s/def :item/duration number?)
(s/def :item/description string?)
(s/def :item/data map?)
(s/def :timeline/item
  (s/keys :req-un [:item/start :item/end :item/duration :item/description]
          :opt-un [:item/data]))
(s/def ::timeline (s/map-of string? :timeline/item))


(s/def ::id string?)
(s/def ::headers (s/map-of string? vector?))
(s/def ::cookies (s/map-of string? string?))
(s/def ::get (s/map-of string? vector?))
(s/def ::post (s/map-of string? vector?))
(s/def ::session (s/map-of string? vector?))


(def schema
  {:id               "str"
   :version          1
   :url              "url-with-domain"
   :uri              "url-without-domain"
   :headers          {:header-name [:value]}
   :controller       "method-name"
   :getData          {:item :value-or-map}
   :postData         {:item :value}
   :sessionData      {:item :value}
   :cookies          {:item :value}
   :time             1.1 ;; time of request, seconds, possibly float
   :responseTime     1.1 ;; time of response, duration = responseTime - time
   :responseDuration 0.1 ;; ??
   :responseStatus   200
   :memoryUsage      123
   :databaseQueries  [{:query      "select * from table"
                       :duration   0.52
                       :connection :clockwork} ;; not used?
                      {:query      "param pam pam"
                       :duration   0.42
                       :connection :other
                       :model      "WaWaWa"}]
   :databaseDuration 123.4
   :cacheQueries     []
   :cacheReads       0
   :cacheHits        0
   :cacheWrites      0
   :cacheDeletes     0
   :cacheTime        nil
   :timelineData     {:timeline_name {:start       2.2
                                      :end         3.3
                                      :duration    1.1
                                      :description "Parsing query params"}}

   :log [{:message "text"
          :context {:a 1 :b 2}
          :level   "debug/info/notice/warning/error"
          :time    4.4
          :file    "mk.clockwork.core"
          :line    20}]

   :emailsData  nil
   :eventsData  nil
   :viewsData   nil
   :userData    nil
   :subrequests nil
   :xdebug      nil})
