(ns dgellow.bottle.server
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [monger.core :as mongo]
            [clj-slack-client.core :as slack]
            [org.httpkit.server :as http]
            [clojure.core.async :refer [go-loop <!]]
            [dgellow.bottle.state
             :refer [db-conn db-obj db-uri running-slack?
                     slack-api-token ch-server-actions]]
            [dgellow.bottle.handler :refer [handle-slack-events]])
  (:import [com.mongodb MongoOptions ServerAddress])
  (:gen-class))

;; Db connection
(defn stop-db! []
  (timbre/info "Disconnecting from db...")
  (mongo/disconnect @db-conn)
  (reset! db-conn nil)
  (reset! db-obj nil))
(defn start-db! []
  (when @db-obj (stop-db!))
  (timbre/info "Connecting to db...")
  (let [{:keys [conn db]} (mongo/connect-via-uri db-uri)]
    (reset! db-conn conn)
    (reset! db-obj db)))


;; Slack connection
(defn stop-slack! []
  (timbre/info "Stopping slack bot...")
  (slack/disconnect))
(defn start-slack! []
  (when @running-slack? (stop-slack!))
  (if slack-api-token
    (do
      (timbre/info "Starting slack bot...")
      (reset! running-slack? true)
      (slack/connect slack-api-token handle-slack-events {:log false}))
    (timbre/error "Missing environment variable SLACK_API_TOKEN")))


;; Web server
(defn web-handler [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello!"})

(defonce web-server (atom nil))

(defn parse-port [port]
  (when port
    (cond
      (string? port) (Integer/parseInt port)
      (number? port) port
      :else (throw (Exception. (str "invalid port value: " port))))))

(defn stop-web! []
  (when-not (nil? @web-server)
    (@web-server :timeout 100)
    (reset! web-server nil)))
(defn start-web! []
  (reset! web-server (http/run-server #'web-handler
                                      {:port
                                       (or (parse-port (env :port))
                                          3000) })))


;; Entry point
(defn stop-app! []
  (stop-web!)
  (stop-db!)
  (stop-slack!))
(defn start-app! []
  (start-web!)
  (start-db!)
  (start-slack!))

(defn listen-to-server-actions []
  (go-loop [action (<! ch-server-actions)]
    (when action
      (case action
        :stop-app (stop-app!)
        :restart-app (do (stop-app!)
                         (start-app!))))

    (recur (<! ch-server-actions))))

(defn -main []
  (listen-to-server-actions)
  (start-app!))
