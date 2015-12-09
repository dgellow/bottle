(ns aztrana.slack-bot.server
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [clj-slack-client.core :as slack])
  (:gen-class))

(def slack-api-token (env :slack-api-token))


(defmulti handle-slack-events (fn [event] ((juxt :type :subtype) event)))

(defmethod handle-slack-events :default
  [event]
  (timbre/debug "Unhandled event:")
  (timbre/debug event))

(defn stop-app! []
  (timbre/info "Stopping slack bot...")
  (slack/disconnect))

(defn start-app! []
  (if slack-api-token
    (do
      (timbre/info "Starting slack bot...")
      (slack/connect slack-api-token))
    (timbre/error "Missing environment variable SLACK_API_TOKEN")))

(defn -main []
  (start-app!))
