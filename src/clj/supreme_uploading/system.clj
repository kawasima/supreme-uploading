(ns supreme-uploading.system
  (:require [com.stuartsierra.component :as component]
            [duct.component.endpoint :refer [endpoint-component]]
            [duct.component.handler :refer [handler-component]]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [clj-jpa.middleware :refer [wrap-entity-manager wrap-transaction]]
            (supreme-uploading.endpoint [example :refer [example-endpoint]]
                                        [api :refer [api-endpoint]])
            (supreme-uploading.component [cdi :refer [cdi-component]])))

(def base-config
  {:app {:middleware [[wrap-transaction]
                      [wrap-entity-manager]
                      [wrap-not-found :not-found]
                      [wrap-defaults :defaults]]
         :not-found  "Resource Not Found"
         :defaults   (meta-merge api-defaults {})}
   :http {:port 3000}})

(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :app  (handler-component (:app config))
         :http (jetty-server (:http config))
         :cdi  (cdi-component)
         :example (endpoint-component example-endpoint)
         :api (endpoint-component api-endpoint))
        (component/system-using
         {:http [:app]
          :app  [:example :api]
          :example [:cdi]
          :api [:cdi]}))))
