(ns dgellow.bottle.handler
  (:require [taoensso.timbre :as timbre]
            [clj-slack-client.team-state :as state]
            [clj-slack-client.rtm-transmit :as transmit]
            [dgellow.bottle.state :refer [bot-name]]
            [dgellow.bottle.analyze :refer [parse interpret]]))

(defn talk-to-bot? [text]
  (.contains text (state/name->id bot-name)))

(defmulti handle-slack-events (fn [event] ((juxt :type :subtype) event)))

(defmethod handle-slack-events ["message" nil]
  [{:keys [user text channel] :as event}]
  (when (talk-to-bot? text)
    (let [{:keys [action message] :as res}
          (interpret (parse user text))]
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
