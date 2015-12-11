(set-env!
 :source-paths #{"src"}
 :dependencies '[[clj-slack-client "0.1.4-SNAPSHOT"]
                 [environ "1.0.1"]
                 [com.taoensso/timbre "4.1.4"]
                 [com.novemberain/monger "3.0.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/clojure "1.7.0"]])

(task-options! pom {:project 'dgellow/bottle
                    :version "0.1.0-SNAPSHOT"
                    :description "A chatops bot"}
               aot {:namespace #{'dgellow.bottle.server}}
               jar {:main 'dgellow.bottle.server})

(deftask deps [])

(deftask run-tests []
  (set-env! :source-paths #{"src" "test"})
  (comp (test)))

(deftask auto-test []
  (set-env! :source-paths #{"src" "test"})
  (comp (watch)
        (speak)
        (test)))

(deftask build
  "Build and package the project"
  []
  (comp (aot)
        (pom)
        (uber)
        (jar)))
