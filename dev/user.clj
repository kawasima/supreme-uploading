(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [eftest.runner :as eftest]
            [meta-merge.core :refer [meta-merge]]
            [reloaded.repl :refer [system init start stop go reset]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [duct.component.figwheel :as figwheel]
            [supreme-uploading.config :as config]
            [supreme-uploading.system :as system]))

(def dev-config
  {:app {:middleware [wrap-stacktrace]}
   :figwheel
   {:css-dirs ["resources/supreme_uploading/public/css"]
    :builds   [{:source-paths ["src/cljs" "dev"]
                :build-options
                {:optimizations :none
                 :main "cljs.user"
                 :asset-path "js"
                 :output-to  "target/figwheel/supreme_uploading/public/js/main.js"
                 :output-dir "target/figwheel/supreme_uploading/public/js"
                 :source-map true
                 :source-map-path "js"}}]}})

(def config
  (meta-merge config/defaults
              config/environ
              dev-config))

(defn new-system []
  (into (system/new-system config)
        {:figwheel (figwheel/server (:figwheel config))}))

(ns-unmap *ns* 'test)

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(when (io/resource "local.clj")
  (load "local"))

(reloaded.repl/set-init! new-system)
