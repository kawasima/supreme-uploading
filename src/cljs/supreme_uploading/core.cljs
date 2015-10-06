(ns supreme-uploading.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [>! <! chan timeout]]
            [clojure.browser.net :as net]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [goog.events :as events]
            [goog.ui.Component]
            [supreme-uploading.api :as api]
            [supreme-uploading.components.grid :refer [grid-view]]
            [supreme-uploading.components.excel :refer [excel-uploader]])
  (:import [goog.net EventType]
           [goog.events KeyCodes]))

(enable-console-print!)

(def app-state
  (atom {:entity "Postal"
         :conditions [{:name "postal-cd" :label "郵便番号" :type "sting"}
                      {:name "city-name" :label "市区町村名" :type "text"}
                      {:name "prefecture-name" :label "都道府県", :type "ref"
                       :ref [{:value "01" :label "北海道"}
                             {:value "02" :label "青森"}
                             {:value "03" :label "岩手"}
                             {:value "04" :label "秋田"}
                             {:value "05" :label "宮城"}
                             {:value "06" :label "山形"}
                             {:value "07" :label "福島"}]}]
         :columns [{:title "全国地方公共団体コード" :data "code", :validator #"\d+"}
                   {:title "旧郵便番号" :data "old-postal-cd", :validator #"^[\d ]{3,5}$"}
                   {:title "郵便番号" :data "postal-cd", :validator #"^\d{7}$"}
                   {:title "都道府県カナ名" :data "prefecture-kana-name", :validator #"^[ｦ-ﾟ0-9]+$"}
                   {:title "市区町村カナ名" :data "city-kana-name", :validator #"^[ｦ-ﾟ0-9]+$"}
                   {:title "町域カナ名" :data "town-kana-name", :validator #"^[ｦ-ﾟ0-9]+$"}
                   {:title "都道府県名" :data "prefecture-name"}
                   {:title "市区町村名" :data "city-name"}
                   {:title "町域名" :data "town-name"}
                   {:title "A" :data "option1", :type "checkbox"}
                   {:title "B" :data "option2", :type "checkbox"}
                   {:title "C" :data "option3", :type "checkbox"}
                   {:title "D" :data "option4", :type "checkbox"}
                   {:title "E" :data "option5", :type "checkbox"}
                   {:title "F" :data "option6"}]}))

(defcomponent main-view [app owner]
  (init-state [_]
    {:uploading-type "grid"})
  (render-state [_ {:keys [uploading-type]}]
    (html
     [:div
      [:div.ui.inverted.fixed.teal.menu
       [:div.title.item [:b "Upload example"]]
       [:div.item
        [:div.ui.form
         [:select {:name "" :on-change (fn [e]
                                         (om/set-state! owner :uploading-type (.. e -target -value)))}
          [:option {:value "grid"} "グリッド"]
          [:option {:value "excel"} "Excel (クライアントサイドRead)"]]]]]
      [:div.main.grid.content
       [:div.ui.items
        [:div.ui.item
         (case uploading-type
           "grid"  (om/build grid-view app)
           "excel" (om/build excel-uploader app))]]]])))

(om/root main-view app-state
         {:target (.getElementById js/document "application")})


