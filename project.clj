(defproject com.ryanberdeen/lead "0.1.0-SNAPSHOT"
  :description "Maybe an alternative to Graphite"
  :url "https://github.com/also/lead"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [org.clojure/clojurescript "0.0-2655"]
    [clj-http "0.7.2"]
    [ring/ring-core "1.2.1"]
    [ring/ring-jetty-adapter "1.2.1"]
    [ring/ring-json "0.2.0"]
    [compojure "1.1.5"]
    [com.lucasbradstreet/instaparse-cljs "1.3.4.2"]
    [org.clojure/tools.logging "0.3.1"]
    [com.cemerick/clojurescript.test "0.2.1"]
    [joda-time/joda-time "2.3"]
    [com.google.guava/guava "15.0"]
    [prismatic/schema "0.2.6"]
    [org.apache.commons/commons-math3 "3.2"]]
  :aot [lead.matcher]
  :plugins [[com.keminglabs/cljx "0.5.0"]
            [lein-cljsbuild "1.0.4"]
            [codox "0.8.10"]]
  :auto-clean false
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths ["src/test/clojure"]
  :javac-options ["-target" "1.6" "-source" "1.6"]
  :prep-tasks []
  :cljx {:builds [{:source-paths ["src/main/cljx"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src/main/cljx"]
                   :output-path "target/generated/cljs"
                   :rules :cljs}
                  {:source-paths ["src/test/cljx"]
                   :output-path "target/test-classes"
                   :rules :clj}
                  {:source-paths ["src/test/cljx"]
                   :output-path "target/generated/test-cljs"
                   :rules :cljs}]}
  :cljsbuild {:builds [{:source-paths ["target/generated/cljs" "target/classes"]
                        :compiler {:optimizations :none
                                   :pretty-print true
                                   :output-dir "target/js"
                                   :output-to "target/js/index.js"}}
                       {:source-paths ["target/generated/cljs" "target/classes" "target/generated/test-cljs" "target/test-classes"]
                        :compiler {:optimizations :none
                                   :pretty-print true
                                   :output-dir "target/test-js"
                                   :output-to "target/test-js/index.js"}}]
              :test-commands {"node" ["./node_modules/coffee-script/bin/coffee" "test/coffee/run_clojure_tests.coffee"]}}
  :aliases {"cleantest" ["do" "clean," "cljx" "once," "javac," "compile," "test"]}
  ;; need this to work with leiningen 2.3.1 used on travis-ci
  ;; cljx should probably support %s or another way to reference project configuration
  :profiles {:test {:target-path "target"
                    :test-paths ["target/test-classes"]}}
  :codox {:defaults {:doc/format :markdown}
          :sources ["src/main/clojure" "target/classes"]
          :src-dir-uri "https://github.com/also/lead/blob/master/"
          :src-linenum-anchor-prefix "L"
          :src-uri-mapping {#"target/classes" #(str "src/main/cljx/" % "x")}})
