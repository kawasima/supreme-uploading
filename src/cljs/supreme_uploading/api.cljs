(ns supreme-uploading.api
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [>! <! chan]]
            [clojure.string :refer [split-lines split]]
            [clojure.browser.net :as net]
            [goog.events :as events]
            [goog.string :as gstring]
            [goog.ui.Component]
            [goog.net.ErrorCode]
            [goog.net.EventType]
            [cljs.reader :refer [read-string]])
  (:import [goog.events KeyCodes]
           [goog.net EventType]))

(defn response-handler [accept]
  (case accept
    "text/tab-separated-values"
    (fn [tsv]
      (->> (split-lines tsv)
           (map #(split % #"\t"))))

    "text/csv"
    (fn [csv] csv)
    (fn [res] (read-string res))))

(defn request
  ([path method body & {:keys [handler error-handler format accept]}]
   (let [xhrio (net/xhr-connection)]
     (when handler
       (events/listen xhrio EventType.SUCCESS
                      (fn [e]
                        (let [res ((response-handler accept) (.getResponseText xhrio))]
                          (handler res)))))
     (.send xhrio path (.toLowerCase (name method))
            body
            (clj->js
             (merge (case format
                      :xml {:content-type "application/xml"}
                      {:content-type "application/edn"})
                    (when accept
                      {:accept accept})))))))

