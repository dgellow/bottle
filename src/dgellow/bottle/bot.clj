(ns dgellow.bottle.bot
  (:require [taoensso.timbre :as timbre]
            [clj-slack-client.core :as slack]
            [clj-slack-client.team-state :as team-state]
            [clj-slack-client.rtm-transmit :as transmit]))

(defrecord Envelope [from to room message])

(defn make-envelope [map]
  (merge (map->Envelope map)
         {:timestamp (str (java.util.Date.))}))

(defprotocol ChatAdapts
  (send-to [x envelope msgs])
  (reply-to [x envelope msgs])
  (set-topic [x envelope msgs])
  (start! [x bot])
  (stop! [x])
  (all-users [x bot])
  (user-by-id [x id])
  (user-by-name [x name])
  (adapter-kind [x])
  (talk-to-bot? [x bot message]))

(defrecord IrcAdapter [server channel]
  ChatAdapts
  (adapter-kind [_] :irc)
  (start! [_ bot] nil)
  (stop! [_] nil))

(defmulti handle-slack-events (fn [event] ((juxt :type :subtype) event)))

(defmethod handle-slack-events ["user_typing" nil]
  [_] nil)

(defmethod handle-slack-events ["pong" nil]
  [_] nil)

(defmethod handle-slack-events :default
  [event]
  (timbre/debug "Unhandled event:")
  (timbre/debug event))

(defn interpret [bot adapter channel user-id user-message]
  (timbre/debug "-- Interpretor --")
  (let [actions (:actions bot)
        action (->> (:actions bot)
                  (filter (fn [{:keys [pattern]}] (re-find pattern user-message)))
                  first)
        f-action (:behaviour action)]
    (timbre/debug actions)
    (timbre/debug action)
    (when f-action
      (f-action user-message
                {:bot bot
                 :adapter adapter
                 :channel channel
                 :user-id user-id}))))

(defrecord SlackAdapter [token]
  ChatAdapts
  (adapter-kind [_] :slack)
  ;; (all-users [_ bot]
  ;;   (let [conn (get-in  [:adapters :slack :conn])]
  ;;     (:users )))
  ;; (user-by-id [_ id]
  ;;   (let [conn (get-in @(:state bot) [:adapters :slack :conn])
  ;;         users (:users (team-state/get-team-state))]
  ;;     (first (filter #(= id (:id %)) users))))
  ;; (send-to [_ envelope msgs]
  ;;   (format "@%s: %s" (:from envelope)
  ;;           (clojure.string/join "\n" msgs)))
  (reply-to [_ envelope message]
    (transmit/say-message (:to envelope) message))
  (talk-to-bot? [this bot message]
    (.contains message (team-state/name->id (:name bot))))
  (stop! [_]
    (slack/disconnect))
  (start! [this bot]
    (let [{:keys [actions]} bot]
      (defmethod handle-slack-events ["message" nil]
        [{:keys [user text channel] :as event}]
        (when (talk-to-bot? this bot text)
          (let [{:keys [message envelope] :as res}
                (interpret bot this channel user text)]
            (timbre/debug "-- Handler \"message\" --")
            (timbre/debug res)
            (when message
              (reply-to this (assoc envelope :to (:to envelope channel)) message)))))
      (slack/connect token handle-slack-events))))

(defprotocol BotLike
  (start-all! [x])
  (stop-all! [x]))

(defrecord Bot [name actions adapters]
  BotLike
  (start-all! [this]
    (doseq [[_ adapter] adapters]
      (start! adapter this)))
  (stop-all! [_]
    (doseq [[_ adapter] adapters]
      (stop! adapter))))

(defn make-bot [{:keys [name actions adapters]}]
  (merge (Bot. name actions
               (reduce (fn [acc adapter]
                         (assoc acc (adapter-kind adapter) adapter))
                       (hash-map)
                       adapters))
         {:state (reduce (fn [acc adapter] nil) {} adapters)}))

(defrecord ChatAction [pattern behaviour])

(defn make-action
  ([pattern f]
   (ChatAction. pattern f))
  ([pattern f ?doc] (with-meta (make-action pattern f) {:doc ?doc})))

(defmacro defaction
  ([name pattern f]
   `(def ~name
      (make-action ~pattern ~f)))
  ([name ?doc pattern f]
   `(def ~name
      (make-action ~pattern ~f ~?doc))))
