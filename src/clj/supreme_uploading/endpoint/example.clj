(ns supreme-uploading.endpoint.example
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response content-type]]
            [garden.core :refer [css]]
            [garden.units :refer [px em]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]]
            [clojure.java.io :as io]))
(def styles
  [[:.ui.fixed.menu {:z-index 1000}]
   [:.main.content {:min-height "100%"
                    :max-width (px 960)
                    :margin {:left "auto" :right "auto"}
                    :padding {:top (px 80)
                              :left (em 2)
                              :right (em 2)}
                    :background {:color "#fff"}
                    :border {:left "1px solid #ddd"
                             :right "1px solid #ddd"}}]
   [:.handson-container {:width (px 850)
                         :height (px 400)
                         :overflow "auto"}]
   [:i.arrow.icon {:user-select "none"
                   :cursor "pointer"}]
   [:select {:width "100%"}]])

(defn index [req]
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1"}]
    (include-css "https://cdn.jsdelivr.net/semantic-ui/2.1.4/semantic.min.css"
                 "https://cdn.jsdelivr.net/handsontable/0.20.0/handsontable.full.min.css"
                 "/css/main.css")
    (include-js  "https://cdn.jsdelivr.net/handsontable/0.18.0/handsontable.full.min.js"
                 "https://cdn.jsdelivr.net/jszip/2.5.0/jszip.min.js"
                 "https://cdn.jsdelivr.net/js-xlsx/0.8.0/xlsx.core.min.js")]
   [:body
    [:div#application]
    (include-js "/js/main.js")]))

(defn example-endpoint [config]
  (routes
   (GET "/" {:as req} (index req))
   (GET "/react/react.js" [] (-> (resource-response "cljsjs/development/react.inc.js")
                                 (content-type "text/javascript")))
   (GET "/react/react.min.js" [] (resource-response "cljsjs/production/react.min.inc.js"))
   (GET "/css/main.css" [] (-> {:body (css {:pretty-pring? false} styles)}
                               (content-type "text/css")))
   (route/resources "/")))
