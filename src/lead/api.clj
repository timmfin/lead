(ns lead.api
  (:use ring.middleware.params
        lead.parser
        lead.functions
        ring.middleware.json
        compojure.core
        clojure.tools.logging)
  (:require [compojure.route :as route]
            [lead.functions :as fns]
            [lead.connector :as conn]
            [clojure.string :as string]))

(def ^:dynamic *routes*)
(defn create-routes [] (atom []))
(defn add-routes
  [& routes]
  (swap! *routes* concat routes))

(defroutes handler
  (GET "/find" [query]
       (let [results (conn/query @conn/*connector* query)]
         {:status 200
          :body results}))
  (GET "/render" [target start end]
    (let [result (run (parse target) {:start (Integer/parseInt start) :end (Integer/parseInt end)})]
      {:status 200
       :body result}))
  (GET "/parse" [target]
    {:status 200
     :body (parse target)})
  (GET "/functions" []
    {:status 200 :body (keys @fns/*fn-registry*)}))

(def not-found (route/not-found "Not Found"))
(defn wrap-exception [f]
  (fn [request]
    (try (f request)
      (catch Exception e
        (warn e "Excption handling request")
        {:status 500
         :body {:exception (.getMessage e) :details (ex-data e)}}))))

(defn create-handler
  []
  (->
    (routes handler (apply routes @*routes*) not-found)
    wrap-exception
    wrap-json-response
    wrap-params))

(defn wrap-uri-prefix [handler prefix]
  (fn [request]
    (let [response (handler (assoc request
                              :uri (string/replace-first (:uri request)
                                                         (re-pattern (str "^" prefix "/?"))
                                                         "/")))]
      (if (<= 300 (:status response) 308)
        (assoc response
          :headers (assoc (:headers response)
                     "Location" (string/replace-first (get-in response [:headers "Location"])
                                                      #"^/"
                                                      (str prefix "/"))))
        response))))