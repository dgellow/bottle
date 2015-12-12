# ![bottle](http://i.imgur.com/rjCWII1.png)

[](dependency)
```clojure
[dgellow/bottle "0.0.1-SNAPSHOT"] ;; latest release
```
[](/dependency)

## Usage

```
(ns your.bot.namespace.core
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer env]
            [dgellow.bottle.bot :refer [defaction make-bot]])
  (:import [dgellow.bottle.bot SlackAdapter]))

;; Actions
(defaction hello-action
  "Greets the user"
  #"hello"
  (fn [_ _] {:message "helol mate"}))

(defaction help-action
  "Display help"
  #"help"
  (fn [_ {:keys [bot]}]
    {:message
     (format "\n%s\n"
             (clojure.string/join
              "\n"
              (map #(format "- \"%s\": %s" (:pattern %)
                            (:doc (meta %))) (:actions bot))))}))

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
  (close-all! bot))
(defn start-bot! []
  (when @bot
    (stop-bot!))
  (timbre/info "Starting bot...")
  (run-all! bot))

(defn -main [& args]
  (start-bot!))
```
