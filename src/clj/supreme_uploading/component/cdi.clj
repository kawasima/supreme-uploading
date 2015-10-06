(ns supreme-uploading.component.cdi
  (:require [com.stuartsierra.component :as component])
  (:import [org.jboss.weld.environment.se Weld]))

(defrecord CDI []
  component/Lifecycle
  (start [component]
    (if (:weld component)
      component
      (let [weld (Weld.)]
        (.initialize weld)
        (assoc component :weld weld))))
  (stop [component]
    (if-let [weld (:weld component)]
      (do (.shutdown weld)
          (dissoc component :weld))
      component)))

(defn cdi-component []
  (->CDI))
