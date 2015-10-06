(defproject supreme-uploading "0.1.0-SNAPSHOT"
  :description "Supreme uploading in 2015"
  :url "https://github.com/kawasima/supreme-uploading"
  :min-lein-version "2.0.0"
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [com.stuartsierra/component "0.3.0"]
                 [compojure "1.4.0"]
                 [duct "0.4.2"]
                 [environ "1.0.1"]
                 [meta-merge "0.1.1"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring-jetty-component "0.3.0"]
                 [liberator "0.13"]
                 [org.projectlombok/lombok "1.16.6" :scope "provided"]

                 ;; jpa
                 [clj-jpa "0.1.0"]
                 [org.jboss.weld.se/weld-se "2.3.0.Final"]
                 [org.eclipse.persistence/org.eclipse.persistence.jpa "2.5.2"]
                 [com.h2database/h2 "1.4.188"]
                 
                 ;; cljs
                 [hiccup "1.0.5"]
                 [garden "1.2.5"]
                 [sablono "0.3.4"]
                 [prismatic/om-tools "0.3.11"]
                 [bouncer "0.3.2"]
                 [org.omcljs/om "0.9.0"]

                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/java.jdbc "0.3.6"]]
  
  :plugins [[lein-environ "1.0.1"]
            [lein-gen "0.2.2"]
            [lein-jdk-tools "0.1.1"]
            [lein-cljsbuild "1.1.0"]]
  :pom-plugins [[org.apache.maven.plugins/maven-compiler-plugin "3.3"
                 {:configuration ([:source "1.7"] [:target "1.7"])}]]
  
  :generators [[duct/generators "0.4.2"]]
  :duct {:ns-prefix supreme-uploading}
  :main ^:skip-aot supreme-uploading.main
  :target-path "target/%s/"
  :resource-paths ["resources" "target/cljsbuild"]
  :prep-tasks [["cljsbuild" "once"] ["compile"]]
  :cljsbuild
  {:builds
   {:main {:jar true
           :source-paths ["src/cljs"]
           :compiler {:output-to "target/cljsbuild/public/js/main.js"
                      :optimizations :simple}}}}
  :aliases {"gen"   ["generate"]
            "setup" ["do" ["generate" "locals"]]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:resource-paths ^:replace ["resources" "target/figwheel/supreme_uploading"]
          :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:source-paths ["dev"]
                   :repl-options {:init-ns user}
                   :dependencies [[reloaded.repl "0.2.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [eftest "0.1.0"]
                                  [kerodon "0.7.0"]
                                  [duct/figwheel-component "0.2.0"]
                                  [figwheel "0.4.0"]]
                   :env {:port 3000}}
   :project/test  {}})
