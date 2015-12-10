(ns aztrana.slack-bot.analyze
  (:require [taoensso.timbre :as timbre]
            [clj-slack-client.team-state :as state]
            [monger.collection :as mongcol]
            [clojure.core.async :refer [>!!]]
            [clojure.string :refer [join lower-case upper-case replace]]
            [aztrana.slack-bot.state :refer [db-obj ch-server-actions]]))

(def bot-actions
  {"[i'm|i am] [in|at] LOCATION" "Set your current location"
   "[move|moving] to LOCATION" "Ditto"

   "[team location|locations]" "List all known locations"

   "[where is SOMEONE|SOMEONE location|location of SOMEONE]"
   "List current location of SOMEONE"

   "eval CLOJURE_EXPRESSION" "(eval (read-string CLOJURE_EXPRESSION))"

   "[restart|take a nap]"
   "Shut me down, but I'll be back soon."

   "leave us"
   "Shut me down. Definitely."

   "help" "Ask for help"})

;; Parsing
(defn parse
  "Search text message for patterns and extract information.
  Returns a map {:verb :kw
                 :verb-object \"str\"
                 :user \"USERID\"
                 :polite boolean}"
  [user raw-text]
  (let [text (lower-case raw-text)
        politness (or (.contains text "pls")
                     (.contains text "please"))]
    (timbre/debug text)
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
       (re-find #"(?:i am|i?m) (?:in|at) (.+)\.?" text)
       (let [[_ verb-object]
             (re-find #"(?:i am|i?m) (?:in|at) (.+)\.?" text)]
         {:verb :set-location
          :verb-object verb-object})
       ;; move to location
       (re-find #"mov(?:e|ing) to (.+)\.?" text)
       (let [[_ verb-object] (re-find #"mov(?:e|ing) to (.+)\.?" text)]
         {:verb :set-location
          :verb-object verb-object})
       ;; ask team locations
       (or (.contains text "locations")
          (.contains text "team location")) {:verb :ask-locations}
       ;; ask location
       (re-find #"where is (?:(\w+)|<@(.+)>)" text)
       (let [[_ user-name user-id]
             (re-find #"where is (?:(\w+)|<@(.+)>)" text)]
         {:verb :ask-location
          :verb-object (or user-name user-id)})
       (re-find #"location of (?:(\w+)|<@(.+)>)" text)
       (let [[_ verb-object] (re-find #"location of (?:(\w+)|<@(.+)>)" text)]
         {:verb :ask-location
          :verb-object verb-object})
       (re-find #"(?:(\w+)|<@(.+)>) location" text)
       (let [[_ verb-object] (re-find #"(?:(\w+)|<@(.+)>) location" text)]
         {:verb :ask-location
          :verb-object verb-object})
       (re-find #"eval (.*)" raw-text)
       (let [[_ verb-object] (re-find #"eval (.*)" raw-text)]
         (timbre/debug raw-text)
         (timbre/debug verb-object)
         {:verb :eval
          :verb-object verb-object})
       ;; thank you
       (or (.contains text "thank")
          (.contains text "great")
          (.contains text "nice")
          (.contains text "good job")) {:verb :thanks}
       ;; hello
       (or (.contains text "hello")
          (.contains text "hi")
          (.contains text "hey")) {:verb :greet}
       :else nil))))


;; Interpretating
(defmulti interpret
  "Takes a map with at least {:verb :kw :verb-action \"str\"}.
  Do something based on :verb.
  Returns another map {:action (fn []) :message \"str\"}"
  :verb)

(defmethod interpret :greet
  [{:keys [user]}]
  {:message (format "%s Hi buddy" user)})

(defmethod interpret :thanks
  [{:keys [user]}]
  {:message (format "%s You're welcome" user)})

(defmethod interpret :sakoboy
  [_]
  {:message "https://files.slack.com/files-pri/T038PG7EE-F0G9KRXE1/manny_in_real_life.jpg"})

(defmethod interpret :eval
  [{:keys [polite verb-object]}]
  {:message (try (-> verb-object
                    (replace "&amp;" "&")
                    (replace "‘" "'")
                    (replace "”" "\"")
                    (replace "“" "\"")
                    ((fn [s]
                      (timbre/debug s)
                      s))
                    read-string
                    eval)
                 (catch Exception e
                   (format "_Exception_ \n```\n%s\n```" (.getMessage e))))})

(defmethod interpret :help
  [{:keys [polite]}]
  (let [actions-str
        (join "\n"
              (map #(str (format "\"%s\"" (first %))
                         "\t"
                         (second %))
                bot-actions))]
    {:message
     (str (when polite "Always here for you dude.\n")
          "You can ask me the following actions:\n"
          "```\n"
          actions-str
          "```")}))

(defmethod interpret :set-location
  [{:keys [verb-object user]}]
  (timbre/debug verb-object)
  {:action (fn [] (mongcol/upsert
                  @db-obj "users"
                  {:_id user}
                  {:location verb-object
                   :name (state/id->name user)}))
   :message (format "%s `%s`, noted. " user verb-object)})

(defmethod interpret :ask-location
  [{:keys [verb-object] :as tokens}]
  (timbre/debug tokens)
  {:message
   (if verb-object
     (let [user-id (or (state/name->id verb-object)
                      verb-object)
           {:keys [location name] :as doc}
           (mongcol/find-map-by-id @db-obj "users" (upper-case user-id))]
       (cond
         (and name location) (format "%s is in %s" name location)
         user-id (format "I cannot find a user `%s` on my records" verb-object)
         :else (format "I have no information for the user `%s`" verb-object)))
     "I'm not sure who you want the location for. See `help` for more details on how to ask me questions")})

(defmethod interpret :ask-locations
  [_]
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
  {:action #(>!! ch-server-actions :stop-app)
   :message (if polite
              "Yeah sure. Bye."
              "Ok ok, I'm leaving but I don't like your tone.")})

(defmethod interpret :default
  [{:keys [user]}]
  {:message "Sorry mate, my actions are limited. Ask me to `help` you if you want to see what I can do for you."})
