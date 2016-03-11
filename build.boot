(set-env!
 :source-paths #{"src"}
 :dependencies '[[clj-slack-client "0.1.4-SNAPSHOT"]
                 [com.taoensso/timbre "4.1.4"]
                 [adzerk/bootlaces "0.1.13" :scope "test"]
                 [org.clojure/clojure "1.7.0"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0-SNAPSHOT")

(bootlaces! +version+)

(task-options! pom {:project 'dgellow/bottle
                    :version +version+
                    :description "A chatops toolkit"
                    :url "https://github.com/dgellow/bottle"
                    :scm {:url "https://github.com/dgellow/bottle"}
                    :license {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}}
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
