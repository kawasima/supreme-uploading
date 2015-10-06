(ns supreme-uploading.components.excel
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
            [cljs.reader :refer [read-string]])
  (:import [goog.net EventType]
           [goog.events KeyCodes]))

(def ch (chan 10000))

(defn to-rownum [r]
  (js/parseInt (second (re-matches #"[A-Z]+([0-9]+)" r))))

(defn process-wb [workbook owner]
  (doseq [sheet-name (.-SheetNames workbook)]
    (let [sheet (aget (.-Sheets workbook) sheet-name)
          [from to] (.split (aget sheet "!ref") ":" 2)
          records (->> (.. js/XLSX -utils (sheet_to_row_object_array sheet nil))
                       rest)]
      (om/set-state! owner :total (- (to-rownum to) (to-rownum from) 1))
      (go
        (doseq [rec records]
          (let [row (js->clj rec)]
            (let [[result error-rec] (b/validate row "postal-cd" [[v/matches #"^\d+$"]])]
              (if result
                (om/update-state! owner :errors #(conj % error-rec))
                (>! ch row)))))
        (>! ch :finished)))))

(defn vectorize [headers m]
  (->> headers
       (map #(get m %))
       vec))

(defcomponent excel-uploader [app owner]
  (init-state [_]
    {:errors []
     :total 1
     :rownum 0})
  
  (will-mount [_]
    (go-loop [buf []]
      (let [rec (<! ch)
            headers (map :data (:columns app))]
        (if (= rec :finished)
          (api/request "/api/Postal" :POST
                       (pr-str {:data buf
                                :headers headers})
                       :handler #(om/update-state! owner :rownum
                                                   (fn [n] (+ n 1000))))
          (if (>= (count buf) 999)
            (do
              (api/request "/api/Postal" :POST
                           (pr-str {:data (conj buf (vectorize headers rec))
                                    :headers headers})
                           :handler (fn [res]
                                      (om/update-state! owner :rownum
                                                          (fn [n] (+ n 1000)))) )
              (recur []))
            (recur (conj buf (vectorize headers rec))))))))
  
  (render-state [_ {:keys [rownum total errors]}]
    (html
     [:div.form

      [:input#file {:type "file" :accept ".xlsx"}]
      [:button.ui.primary.button
       {:type "button"
        :on-click (fn [e]
                    (om/set-state! owner :total 1)
                    (om/set-state! owner :rownum 0)
                    (let [file (aget (.. js/document (getElementById "file") -files) 0)
                          reader (js/FileReader.)]
                      (om/set-state! owner :errors [])
                      (set! (.-onload reader)
                            (fn [e]
                              (process-wb
                               (.read js/XLSX
                                      (.. e -target -result)
                                      (clj->js {:type "binary"}))
                               owner)))
                      (.readAsBinaryString reader file)))}
       "Upload"]
      [:div.ui.divider]
      (let [pct (int (* (/ rownum total) 100))]
        [:div.ui.teal.progress.active
         [:div.bar {:style {:width (str pct "%")}}]
         [:div.label (str pct "% registered!")]])
      (if (not-empty errors)
        [:div
         [:table.ui.table
          [:thead
           (for [column (:columns app)]
             [:th (:title column)])]
          [:tbody
           (map-indexed
            (fn [idx rec]
              [:tr
               (for [column (:columns app)]
                 [:td
                  (if-let [msgs (get-in rec [:bouncer.core/errors (:data column)])]
                    [:div.ui.input.error
                     [:input#postal-cd
                      {:type "text"
                       :value (get rec (:data column))
                       :on-change (fn [e]
                                    (om/update-state! owner
                                                      :errors
                                                      #(assoc-in % [idx (:data column)]
                                                                 (.. e -target -value))))}]]
                    (get rec (:data column)))])])
            errors)]]
         [:p "上記エラーを修正して"
          [:button.ui.primary.button
           {:on-click (fn [e]
                        (let [headers (map :data (:columns app))]
                          (api/request "/api/Postal" :POST
                                       (pr-str {:data (->> (om/get-state owner :errors)
                                                           (map #(dissoc % :bouncer.core/errors))
                                                           (map #(vectorize headers %)))
                                                :headers headers})
                                       :handler #(om/set-state! owner :errors []))))}
           "登録する"]]])])))
