(ns aztrana.slack-bot.server
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [clojure.string :refer [join]]
            [clj-slack-client.core :as slack]
            [clj-slack-client.team-state :as state]
            [clj-slack-client.rtm-transmit :as transmit])
  (:gen-class))

(def slack-api-token (env :slack-api-token))
(def bot-name "manny")

(declare stop-app!)


(def bot-actions
  {"help" "Well... That's what you asked for, no?"
   "leave us" "Shut me down"})


(defn talk-to-bot? [text]
  (.contains text (state/name->id bot-name)))

(defn parse [text]
  (let [politness (or (.contains text "pls")
                     (.contains text "please"))]
    (merge
     {:polite politness}
     (cond
       (.contains text "leave us") {:verb :leave}
       (.contains text "help") {:verb :help}
       :else nil))))

(defmulti interpret :verb)

(defmethod interpret :help
  [{:keys [polite]}]
  (let [actions-str
        (join "\n"
              (map #(str (first %)
                         "\t\t"
                         (second %))
                bot-actions))]
    {:message
     (str (when polite "Always here for you dude.\n")
          "You can ask me the following actions:\n"
          actions-str)}))

(defmethod interpret :leave
  [{:keys [polite]}]
  {:action #(stop-app!)
   :message (if polite
              "Yeah sure. Bye."
              "Ok ok, I'm leaving but I don't like your tone.")})

(defmethod interpret :default
  [tokens]
  (println tokens)
  "uh?")

(defmulti handle-slack-events (fn [event] ((juxt :type :subtype) event)))

(defmethod handle-slack-events ["message" nil]
  [{:keys [user text channel] :as event}]
  (when (talk-to-bot? text)
    (let [{:keys [action message] :as res} (interpret (parse text))]
      (when message
        (transmit/say-message channel message))
      (when action
        (action)))))

(defmethod handle-slack-events ["user_typing" nil]
  [_] nil)

(defmethod handle-slack-events ["pong" nil]
  [_] nil)

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
      (slack/connect slack-api-token handle-slack-events {:log false}))
    (timbre/error "Missing environment variable SLACK_API_TOKEN")))

(defn -main []
  (start-app!))
