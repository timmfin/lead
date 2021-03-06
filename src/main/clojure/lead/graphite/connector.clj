(ns lead.graphite.connector
  (:import (com.google.common.hash Hashing)
           (com.google.common.base Charsets)
           (java.util Collections))
  (:require [clj-http.client :as http]
            [lead.connector :refer [Connector connector-list] :as connector]
            [lead.matcher :refer [segment-matcher pattern?]]))

(defn ^:no-doc graphite-json->serieses [targets]
    (map (fn [target]
           (assoc (let [timestamps (map second (:datapoints target))]
                    (if (seq timestamps)
                      (assoc {:start (first timestamps) :end (last timestamps)}
                              :step (if (> (count timestamps) 1)
                                     (- (second timestamps) (first timestamps))
                                     1))))
                  :name (:target target)
                  :values (map first (:datapoints target))))
         targets))

(defn- transform-find-response
  [pattern response]
  (let [last-segment-matches (-> pattern (.split "\\.") last segment-matcher)]
    ; graphite tacks on "*" to the end of every query:
    ; https://github.com/graphite-project/graphite-web/blob/0.9.12/webapp/graphite/metrics/views.py#L157-L158
    (filter (fn [result] (-> result :name (.split "\\.") last last-segment-matches))
            (map (fn [result]
                   {:name    (let [name (:path result)]
                               (if (.endsWith name ".")
                                 (.substring name 0 (- (.length name) 1))
                                 name))
                    :is-leaf (= "1" (:is_leaf result))})
                 (-> response :metrics)))))

(defrecord GraphiteConnector [url]
  Connector
  (query [this pattern]
    (let [url (str url "/metrics/find")
          response (http/get url {:as :json
                                  :query-params {"query" pattern
                                                 "format" "completer"}})]
      (transform-find-response pattern (:body response))))

  (load [this target {:keys [start end]}]
    (let [url (str url "/render/")
          response (http/get url {:as :json
                                  :query-params {"target" target
                                                 "from" start
                                                 "until" end
                                                 "format" "json"}})
          targets (:body response)]
      (graphite-json->serieses targets))))

(alter-meta! #'->GraphiteConnector assoc :no-doc true)
(alter-meta! #'map->GraphiteConnector assoc :no-doc true)

; https://github.com/graphite-project/carbon/blob/0.9.12/lib/carbon/hashing.py
; https://github.com/graphite-project/carbon/blob/0.9.12/lib/carbon/routers.py

(defn connector [url] (->GraphiteConnector url))

(def ^:private md5 (Hashing/md5))

(defn- compute-ring-position [key]
  (Integer/parseInt (.substring (str (.hashString md5 key Charsets/UTF_8)) 0 4) 16))

(defn- create-builder
  [replicas] {:replicas replicas, :entries ()})

(defn- key-string [[host instance] i]
  (format "('%s', '%s'):%d" host instance i))

(defn- add-node [builder node]
  (assoc builder :entries
                 (reduce (fn [entries i]
                           (let [key (key-string node i)
                                 position (compute-ring-position key)]
                             (conj entries [position node])))
                         (:entries builder)
                         (range (:replicas builder)))))

(defn- build [builder]
  (-> builder :entries sort vec))

(defn ^:no-doc create-ring
  ([nodes] (create-ring 100 nodes))
  ([replicas nodes]
   (build (reduce add-node (create-builder replicas) nodes))))

(defn ^:no-doc get-node
  [key entries]
  (let [position (compute-ring-position key)
        entry [position nil]
        result-position (mod (- (- (Collections/binarySearch entries entry)) 1) (count entries))]
    (second (nth entries result-position))))

(defrecord ConsistentHashedGraphiteConnector [wrapped node ring]
  Connector
  (query [this pattern]
    (if (or (pattern? pattern) (= node (get-node pattern ring)))
      (connector/query wrapped pattern)
      ()))
  (connector/load [this target opts]
    (if (or (pattern? target) (= node (get-node target ring)))
      (connector/load wrapped target opts)
      ())))

(alter-meta! #'->ConsistentHashedGraphiteConnector assoc :no-doc true)
(alter-meta! #'map->ConsistentHashedGraphiteConnector assoc :no-doc true)

(defn consistent-hashing-cluster [replicas & host-specs]
  (let [ring (create-ring replicas (map :node host-specs))
        connectors (map #(->ConsistentHashedGraphiteConnector
                          (connector (:url %))
                          (:node %)
                          ring)
                        host-specs)]
    (apply connector-list connectors)))
