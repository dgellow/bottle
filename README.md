# A toolkit to create bots for your chat platform

![bottle](http://i.imgur.com/rjCWII1.png)

[](dependency)
```clojure
[dgellow/bottle "0.2.0-SNAPSHOT"] ;; latest release
```
[](/dependency)

## Usage

```clojure
(ns your.bot.namespace.core
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer env]
            [dgellow.bottle.bot :refer [defaction make-bot stop-all! start-all!]])
  (:import [dgellow.bottle.bot SlackAdapter]))

;; Actions
(defaction hello-action
  "Greets the user"
  #"hello"
  (fn [_ _] (make-envelope {:message "helol mate"})))

(defaction help-action
  "Display help"
  #"help"
  (fn [_ {:keys [bot]}]
    (make-envelope {:message
                    (format "\n%s\n"
                            (clojure.string/join
                             "\n"
                             (map #(format "- \"%s\": %s" (:pattern %)
                                           (:doc (meta %))) (:actions bot))))})))

;; Bot definition
(def bot-spec
  (let [slack-token (env :slack-api-token)]
    (assert slack-token "Missing environment variable SLACK_API_TOKEN")
    (make-bot {:name "john_draper"
               :actions [hello-action
                         help-action]
               :adapters [(SlackAdapter. slack-token)]})))

;; Entry points
(def bot
  (atom bot-spec))
(defn stop-bot! []
  (timbre/info "Stopping bot...")
  (stop-all! bot))
(defn start-bot! []
  (when @bot
    (stop-bot!))
  (timbre/info "Starting bot...")
  (start-all! bot))

(defn -main [& args]
  (start-bot!))
```

## References

See the presentation "[Chatops at GitHub](https://speakerdeck.com/jnewland/chatops-at-github)", also their bot engine [Hubot](https://hubot.github.com/).
