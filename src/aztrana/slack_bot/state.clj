(ns aztrana.slack-bot.state
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [clojure.core.async :refer [chan]]))

;; Settings
(def slack-api-token (env :slack-api-token))
(def bot-name "manny")

;; Db connection
(def db-uri
  (if-let [uri (env :mongolab-uri)]
    uri
    (timbre/error "Missing environment variable MONGOLAB_URI")))
(def db-conn (atom nil))
(def db-obj (atom nil))

;; Slack connection
(def running-slack? (atom nil))

;; Channel for high-level server actions
(def ch-server-actions (chan))
