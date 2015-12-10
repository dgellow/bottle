(ns aztrana.slack-bot.server
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [clojure.string :refer [join lower-case]]
            [monger.core :as mongo]
            [monger.collection :as mongcol]
            [clj-slack-client.core :as slack]
            [clj-slack-client.team-state :as state]
            [clj-slack-client.rtm-transmit :as transmit]
            [org.httpkit.server :as http])
  (:import [com.mongodb MongoOptions ServerAddress])
  (:gen-class))

(def slack-api-token (env :slack-api-token))
(def bot-name "manny")

(def db-uri (env :mongolab-uri))
(def db-conn (atom nil))
(def db-obj (atom nil))

(declare stop-app!)


(def bot-actions
  {"i am [in|at] LOCATION" "Set your current location"
   "[move|moving] to LOCATION" "Ditto"
   "location" "List people location"
   "leave us" "Shut me down. But that's not what you want, right? RIGHT?"
   "help" "Well... you should already know what it does"})


(defn talk-to-bot? [text]
  (.contains text (state/name->id bot-name)))

(defn parse [user text]
  (let [politness (or (.contains text "pls")
                     (.contains text "please"))]
    (merge
     {:polite politness
      :user user}
     (cond
       ;; sakoboy
       (.contains text "uh???") {:verb :sakoboy}
       ;; stop bot
       (.contains text "leave us") {:verb :leave}
       ;; help
       (.contains text "help") {:verb :help}
       ;; set location
       (re-find #"i am (?:in|at) (.+)\.?" text)
       (let [[_ verb-object] (re-find #"i am (?:in|at) (.+)\.?" text)]
         {:verb :set-location
          :verb-object verb-object})
       ;; move to location
       (re-find #"mov(e|ing) to (.+)\.?" text)
       (let [[_ verb-object] (re-find #"mov(e|ing) to (.+)\.?" text)]
         {:verb :set-location
          :verb-object verb-object})
       ;; ask location
       (.contains text "location") {:verb :ask-locations}
       :else nil))))

(defmulti interpret :verb)

(defmethod interpret :sakoboy
  [_]
  {:message "https://files.slack.com/files-pri/T038PG7EE-F0G9KRXE1/manny_in_real_life.jpg"})

(defmethod interpret :help
  [{:keys [polite]}]
  (let [actions-str
        (join "\n"
              (map #(str (format "`%s`" (first %))
                         "\t\t"
                         (second %))
                bot-actions))]
    {:message
     (str (when polite "Always here for you dude.\n")
          "You can ask me the following actions:\n"
          actions-str)}))

(defmethod interpret :set-location
  [{:keys [verb-object user]}]
  (timbre/debug verb-object)
  {:action (fn [] (mongcol/upsert
                  @db-obj "users"
                  {:_id user}
                  {:location verb-object
                   :name (state/id->name user)}))
   :message (format "%s Noted." user)})

(defmethod interpret :ask-locations
  [{:keys [verb-object]}]
  (let [user-docs (mongcol/find-maps @db-obj "users")
        locations
        (let [user-docs (mongcol/find-seq @db-obj "users")]
          (map (fn [doc]
                 (let [name (get doc "name")
                       location (get doc "location")]
                   (when (and name location)
                     (format "%s is in %s" name location))))
            user-docs))
        locations-str
        (join ",\n" (filter identity locations))]
    {:message (str "Team members location:\n" locations-str)}))

(defmethod interpret :leave
  [{:keys [polite]}]
  {:action #(stop-app!)
   :message (if polite
              "Yeah sure. Bye."
              "Ok ok, I'm leaving but I don't like your tone.")})

(defmethod interpret :default
  [{:keys [user]}]
  {:message
   (if (= "sako" (state/id->name))
     "uh?"
     "Sorry mate, my actions are limited. Ask me to `help` you if you want to see what I can do for you.")})

(defmulti handle-slack-events (fn [event] ((juxt :type :subtype) event)))

(defmethod handle-slack-events ["message" nil]
  [{:keys [user text channel] :as event}]
  (when (talk-to-bot? text)
    (let [{:keys [action message] :as res}
          (interpret (parse user (lower-case text)))]
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

(def running-slack? (atom nil))
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
                                      {:port (parse-port (env :port))})))

(defn stop-app! []
  (stop-web!)
  (stop-db!)
  (stop-slack!))
(defn start-app! []
  (start-web!)
  (start-db!)
  (start-slack!))

(defn -main []
  (start-app!))
