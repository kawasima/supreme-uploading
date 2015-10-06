(ns supreme-uploading.api
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [>! <! chan]]
            [clojure.browser.net :as net]
            [goog.events :as events]
            [goog.string :as gstring]
            [goog.ui.Component]
            [goog.net.ErrorCode]
            [goog.net.EventType]
            [cljs.reader :refer [read-string]])
  (:import [goog.events KeyCodes]
           [goog.net EventType]))

(defn request
  ([path method body & {:keys [handler error-handler format]}]
   (let [xhrio (net/xhr-connection)]
     (when handler
       (events/listen xhrio EventType.SUCCESS
                      (fn [e]
                        (let [res (read-string (.getResponseText xhrio))]
                          (handler res)))))
     (.send xhrio path (.toLowerCase (name method))
            body
            (case format
              :xml (clj->js {:content-type "application/xml"})
              (clj->js {:content-type "application/edn"}))))))

