(ns supreme-uploading.components.grid
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

(defn search-by-conditions [entity-name conditions callback]
  (let [query (->> conditions
                   (map (fn [[k v]]
                        (if (= "equal" (:operator v))
                          (str "f." k "=" (:value v)))) conditions)
                   (clojure.string/join "&"))]
    (api/request (str "/api/" entity-name
                      (when query (str "?" query)))
                     :GET (pr-str conditions)
                     :handler (fn [res]
                                (let [data (->> res
                                                (map clojure.walk/stringify-keys)
                                                 vec
                                                 clj->js)]
                                  (callback data))))))

(defcomponent action-menu [entity-name owner {:keys [request-handler]}]
  (init-state [_]
    {:save-status nil})
  (render-state [_ {:keys [save-status]}]
    (html
     [:div.ui.menu
      [:div.item
       [:button.ui.primary.button
        {:type "button"
         :on-click (fn [e]
                     (api/request (str "/api/" entity-name)
                                  :POST
                                  (request-handler)
                                  
                                  :handler (fn [res]
                                             (om/set-state! owner :save-status :saved)
                                             (go
                                               (<! (timeout 3000))
                                               (om/set-state! owner :save-status nil))))
                     (om/set-state! owner :save-status :saving))}
        "保存"]
       (case save-status
         :saving "保存中です…"
         :saved  "保存しました"
         "")]])))

(defcomponent grid-new [app owner]
  (init-state [_]
    {})

  (render-state [_ _]
    (html
     [:div
      [:div.handson-container]
      (om/build action-menu (:entity app)
                {:opts
                 {:request-handler (fn []
                                     (let [handsontable (om/get-state owner :handsontable)]
                                       (pr-str {:headers (map :data (:columns app)) 
                                                :data (js->clj (for [i (range (.countRows handsontable))]
                                                                 (.getDataAtRow handsontable i)))})))} })]))
  
  (did-mount [_]
    (if-let [container (.querySelector (om/get-node owner) ".handson-container")]
      (let [handsontable (js/Handsontable.
                          container
                          (clj->js {:startCols (count (:columns app))
                                    :startRows 1
                                    :columnSorting true
                                    :manualColumnResize true
                                    :rowHeaders true
                                    :columns (:columns app)
                                    :beforeChange (fn [changes source]
                                                    (doseq [change changes]
                                                      (if-let [col (first (filter #(= (aget change 1) (:data %)) (:columns app)))]
                                                        (when (= (:type col) "checkbox")
                                                          (let [v (aget change 3)]
                                                            (aset change 3 (or (= v 1) (= v "1") (= v "yes") (= v "on"))))))))}))]
        (set! (.-grid js/window) handsontable)
        (om/set-state! owner :handsontable handsontable))
      
      (throw (js/Error. (om/get-node owner))))))

(defcomponent filter-label [label-name owner {:keys [click-fn]}]
  (init-state [_]
    {:over? false})
  (render-state [_ {:keys [over?]}]
    (html
     [:label
      [:i.icon (merge {:on-mouse-over (fn [e] (om/set-state! owner :over? true))
                       :on-mouse-out  (fn [e] (om/set-state! owner :over? false))
                       :on-click click-fn}
                      (if over?
                        {:class "trash"}
                        {:class "filter"}))]
      label-name])))

(defcomponent grid-query [app owner]
  (init-state [_]
    {:selected-conditions []
     :conditions {}
     :selected-columns (:columns @app)
     :menu-status {:open-filter? false
                   :open-options? false}
     :editable? false 
     :save-status nil})
  (render-state [_ {:keys [selected-conditions selected-columns menu-status save-status handsontable editable?]}]
    (html
     [:div
      [:div.ui.accordion
       [:div.title (merge
                    {:on-click (fn [e]
                                 (om/set-state! owner [:menu-status :open-filter?]
                                                (not (om/get-state owner [:menu-status :open-filter?]))))}
                    (when (:open-filter? menu-status) {:class "active"}))
        [:i.dropdown.icon] "フィルタ"]
       [:div.content (when (:open-filter? menu-status) {:class "active"})
        [:div.ui.grid.form
        [:div.twelve.wide.column
         [:div.ui.list
          (for [condition selected-conditions]
            [:div.item
             [:div.inline.field
              (om/build filter-label (:label condition)
                        {:opts
                         {:click-fn (fn [e]
                                      (om/update-state! owner [:selected-conditions]
                                                        (fn [sc]
                                                          (->> sc
                                                               (remove #(= % condition)))))
                                      (om/update-state! owner [:conditions]
                                                        #(dissoc % (:name condition))))}})
              (case (:type condition)
                "ref"
                [:select {:name (:name condition)
                        :value (om/get-state owner [:conditions (:name condition) :value])
                        :on-change (fn [e]
                                     (om/set-state! owner [:conditions (:name condition)]
                                                    {:operator "equal"
                                                     :value (.. e -target -value)}))}
                 (for [ref (:ref condition)]
                   [:option {:value (:value ref)}
                    (:label ref)])]

                "text"
                [:div.ui.equal.width.grid
                 [:div.column
                  [:input {:type "text" :name (:name condition)
                         :value (om/get-state owner [:conditions (:name condition) :value])
                         :on-change (fn [e]
                                      (om/update-state! owner [:conditions (:name condition)]
                                                        #(assoc %
                                                                :value (.. e -target -value))))}]]
                 [:div.column
                  [:select {:value (om/get-state owner [:conditions (:name condition) :operator])
                            :on-change (fn [e]
                                         (om/update-state! owner [:conditions (:name condition)]
                                                           #(assoc % :value (.. e -target -value))))}
                  [:option {:value "contains"} "を含む"]
                  [:option {:value "not-contains"} "を含まない"]]]                 ]
                
                
                ;; Default typ is `string`
                [:input {:type "text" :name (:name condition)
                       :value (om/get-state owner [:conditions (:name condition) :value])
                       :on-change (fn [e]
                                    (om/set-state! owner [:conditions (:name condition)]
                                                   {:operator "equal"
                                                    :value (.. e -target -value)}))}])]])]]
        
        [:div.four.wide.column
         [:select {:on-change (fn [e]
                                (om/update-state! owner :selected-conditions
                                                  (fn [conds]
                                                    (if-let [c (first (filter #(= (:name %) (.. e -target -value)) (:conditions app)))]
                                                      (conj conds c))))
                                (set! (.. e -target -value) ""))}
          [:option {:value ""} "選択してください"]
          (for [condition (->> (:conditions app)
                               (remove (fn [c] (some #(= c %) selected-conditions))))]
            [:option {:value (:name condition)} (:label condition)])]]]]
       [:div.title (merge
                    {:on-click (fn [e]
                                 (om/set-state! owner [:menu-status :open-options?]
                                                (not (om/get-state owner [:menu-status :open-options?]))))}
                    (when (:open-options? menu-status) {:class "active"}))
        [:i.dropdown.icon] "オプション"]
       [:div.content (when (:open-options? menu-status) {:class "active"})
        [:div.ui.grid
         [:div.three.wide.column "column"]
         [:div.four.wide.column
          [:select {:name "available_columns" :multiple "multiple"}
           (for [column (->> (:columns app)
                             (remove (fn [c] (some #(= c %) selected-columns))))]
             [:option {:value (:data column)} (:title column)])]]
         [:div.one.wide.column
          [:button.ui.button
           {:type "button"
            :on-click (fn [e]
                        (if-let [sel (some-> (.querySelector (om/get-node owner) "select[name=available_columns]")
                                             (.-value))]
                          (om/update-state! owner :selected-columns
                                            (fn [cols]
                                              (if-let [c (first (filter #(= (:data %) sel) (:columns app)))]
                                                (conj cols c))))))}
           [:i.arrow.right.icon]]
          [:button.ui.button
           {:type "button"
            :on-click (fn [e]
                        (if-let [sel (some-> (.querySelector (om/get-node owner) "select[name=selected_columns]")
                                             (.-value))]
                          (om/update-state! owner :selected-columns
                                            (fn [cols]
                                              (->> cols
                                                   (remove #(= (:data %) sel))
                                                   vec)))))}
           [:i.arrow.left.icon]]]
         [:div.four.wide.column
          [:select {:name "selected_columns" :multiple "multiple"}
           (for [column selected-columns]
             [:option {:value (:data column)} (:title column)])]]
         [:div.one.wide.column
          [:button.ui.button
           {:type "button"
            :on-click (fn [e]
                        (if-let [sel (some-> (.querySelector (om/get-node owner) "select[name=selected_columns]")
                                             (.-value))]
                          (om/update-state! owner :selected-columns
                                            (fn [cols]
                                              (if-let [c (first (filter #(= (:data %) sel) (:columns app)))]
                                                (conj cols c))))))}
           [:i.arrow.up.icon]]
          [:button.ui.button
           {:type "button"
            :on-click (fn [e]
                        (if-let [sel (some-> (.querySelector (om/get-node owner) "select[name=selected_columns]")
                                             (.-value))]
                          (om/update-state! owner :selected-columns
                                            (fn [cols]
                                              (if-let [c (first (filter #(= (:data %) sel) (:columns app)))]
                                                (conj cols c))))))}
           [:i.arrow.down.icon]]]]]]

      [:div.ui.buttons
       [:button.ui.basic.button
        {:on-click (fn [e]
                     (search-by-conditions (:entity app)
                                           (om/get-state owner :conditions)
                                           (fn [data]
                                             (if (empty? data)
                                               (let [handsontable (om/get-state owner :handsontable)]
                                                 (.loadData handsontable (clj->js [{:code "該当するデータがありません"}]))
                                                 (.updateSettings handsontable (clj->js {:mergeCells [{:row 0 :col 0 :rowspan 1 :colspan (.countCols handsontable)}]})))
                                               (.loadData (om/get-state owner :handsontable) data))
                                             )))}
        [:i.green.check.icon] "適用"]]
      [:div.handson-container]
      (when editable?
        (om/build action-menu (:entity app)
                  {:opts {:request-hander (fn [handsontable]
                                            (pr-str {:headers (map :data (om/get-state owner :selected-columns)) 
                                                     :data (js->clj (for [i (range (.countRows handsontable))]
                                                                      (.getDataAtRow handsontable i)))}))}}))]))
  
  (did-mount [_]
    (api/request (str "/api/" (:entity app))
                     :GET nil
                     :handler (fn [res]
                                (let [data (->> res
                                                (map clojure.walk/stringify-keys)
                                                 vec
                                                 clj->js)
                                      container (.querySelector (om/get-node owner) ".handson-container")]
                                  (if (empty? data)
                                    (println "")
                                    (let [handsontable (js/Handsontable.
                                                        container
                                                        (clj->js {:startCols (count (om/get-state owner :selected-columns))
                                                                  :startRows 1
                                                                  :columnSorting true
                                                                  :manualColumnResize true
                                                                  :rowHeaders true
                                                                  :columns (om/get-state owner :selected-columns)
                                                                  :readOnly true
                                                                  :afterChange (fn [changes source]
                                                                                 (doseq [change changes]))}))
                                          headers (map :data (om/get-state owner :selected-columns))]
                                      (set! (.-grid js/window) handsontable)
                                      (om/set-state! owner :handsontable handsontable)
                                      (.loadData handsontable data)))))))
  (did-update [_ _ _]
    (if-let [handsontable (om/get-state owner :handsontable)]
      (.updateSettings handsontable (clj->js {:columns (om/get-state owner :selected-columns)})))))


(defcomponent grid-view [app owner]
  (init-state [_]
    {:mode :query})
  
  (render-state [_ {:keys [mode]}]
    (html
     [:div
      [:div.ui.pointing.secondary.menu
       [:a.item (if (= mode :query)
                  {:class "active"}
                  {:on-click (fn [e]
                               (om/set-state! owner :mode :query))}) "検索"]
       [:a.item (if (= mode :new)
                  {:class "active"}
                  {:on-click (fn [e]
                               (om/set-state! owner :mode :new))}) "新規登録"]]
      [:ui.segment
       (case mode
         :query (om/build grid-query app)
         :new   (om/build grid-new   app))]])))
