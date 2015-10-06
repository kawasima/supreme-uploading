(ns supreme-uploading.endpoint.api
  (:require [liberator.core :refer [defresource]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [compojure.core :refer [routes context ANY]]
            [clj-jpa.entity-manager :as em]
            [clj-jpa.query :as q]))

(def entity-path "example.model")
(defn body-as-string [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn parse-edn [ctx]
  (when (#{:put :post} (get-in ctx [:request :request-method]))
    (try
      (if-let [body (body-as-string ctx)]
        (let [data (edn/read-string body)]
          [false {::data data}])
        {:message "No body"})
      (catch Exception e
        {:message (format "IOException: %s" (.getMessage e))}))))

(defresource list-resource [entity-name]
  :available-media-types ["application/edn"]
  :allowed-methods [:get :post]
  :malformed? #(parse-edn %)
  :post! #(let [entity-class (Class/forName (str entity-path "." entity-name))
                headers (get-in % [::data :headers])]
            (doseq [record (get-in % [::data :data])]
              (em/merge entity-class
                        (apply assoc {}
                               (interleave (map keyword headers) record)))))
  :handle-ok (fn [ctx]
               (let [entity-class (Class/forName (str entity-path "." entity-name))
                     builder (.getCriteriaBuilder em/*em*)
                     criteria-query (.createQuery builder entity-class)
                     root (.from criteria-query entity-class)]
                 (.where criteria-query
                         (apply q/pred-and builder root
                                (->> (get-in ctx [:request :query-params])
                                     (filter (fn [[k v]] (.startsWith k "f.")))
                                     (map (fn [[k v]] (q/pred-= builder
                                                                root
                                                                (q/expr root (keyword (.substring k 2)))
                                                                v))))))
                 (let [query (.createQuery em/*em* criteria-query)]
                   (q/result-list query em/*em*)))))

(defn api-endpoint [config]
  (routes
   (context "/api" []
     (ANY "/:entity-name" [entity-name]
       (list-resource entity-name)))))
