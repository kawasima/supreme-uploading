(ns supreme-uploading.components.grid
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [>! <! chan timeout]]
            [clojure.walk]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [goog.events :as events]
            [goog.string :as gstring]
            [goog.ui.Component]
            [goog.fs]
            [supreme-uploading.api :as api]
            [cljs.reader :refer [read-string]]))

(defn search-by-conditions [entity-name headers conditions callback]
  (let [query (->> conditions
                   (map (fn [[k v]]
                          (let [base-query (str "f." k "=" (js/encodeURIComponent (:value v)))]
                            (if (not= "equal" (:operator v))
                              (str base-query "&fo." k "=" (:operator v))
                              base-query)))
                        conditions)
                   (clojure.string/join "&"))]
    (api/request (str "/api/" entity-name
                      "?cols=" (clojure.string/join "," headers)
                      (when query (str "&" query)))
                     :GET (pr-str conditions)
                     :handler (fn [res]
                                (callback res))
                     :accept "text/tab-separated-values")))

(defn search-for-download [entity-name headers conditions callback]
  (let [query (->> conditions
                   (map (fn [[k v]]
                          (let [base-query (str "f." k "=" (js/encodeURIComponent (:value v)))]
                            (if (not= "equal" (:operator v))
                              (str base-query "&fo." k "=" (:operator v))
                              base-query)))
                        conditions)
                   (clojure.string/join "&"))]
    (api/request (str "/api/" entity-name
                      "?cols=" (clojure.string/join "," headers)
                      (when query (str "&" query)))
                     :GET (pr-str conditions)
                     :handler (fn [res]
                                (callback res))
                     :accept "text/csv")))

(defcomponent action-menu [entity-name owner {:keys [request-handler]}]
  (init-state [_]
    {:save-status nil})
  (render-state [_ {:keys [save-status errors-count]}]
    (html
     [:div.ui.menu
      [:div.item
       [:button.ui.primary.button
        (merge
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
         (when (> errors-count 0) {:class "disabled"}))
        "保存"]
       (case save-status
         :saving "保存中です…"
         :saved  "保存しました"
         (if (> errors-count 0)
           (str "エラーが" errors-count "件あります。")
           ""))]])))

(defcomponent grid-new [app owner]
  (init-state [_]
    {:errors {}})

  (render-state [_ {:keys [errors]}]
    (html
     [:div
      [:div.handson-container]
      (om/build action-menu (:entity app)
                {:state {:errors-count (count errors)}
                 :opts
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
                                                            (aset change 3 (or (= v 1) (= v "1") (= v "yes") (= v "on"))))))))
                                    :afterValidate (fn [valid? value row prop source]
                                                     (if valid?
                                                       (when (om/get-state owner [:errors row prop])
                                                         (if (<= (count (om/get-state owner [:errors row]) 1))
                                                           (om/update-state! owner [:errors]  (fn [errors] (dissoc errors row)))
                                                           (om/update-state! owner [:errors row]
                                                                           (fn [props]
                                                                             (dissoc props prop)))))
                                                       (om/update-state! owner [:errors row]
                                                                         (fn [props] (assoc props prop true))))
                                                     valid?)}))]
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

(defn selected-values [sel]
  (some->> (array-seq (.querySelectorAll sel "option") 0)
           (filter #(.-selected %))
           (map #(.-value %))))


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
                                                           #(assoc % :operator (.. e -target -value))))}
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
         [:div.three.wide.column "表示項目"]
         [:div.four.wide.column
          [:select {:name "available_columns" :multiple "multiple"}
           (for [column (->> (:columns app)
                             (remove (fn [c] (some #(= c %) selected-columns))))]
             [:option {:value (:data column)} (:title column)])]]
         [:div.one.wide.column
          [:div {:on-click (fn [e]
                             (if-let [sel (selected-values (.querySelector (om/get-node owner) "select[name=available_columns]"))]
                               (om/update-state! owner :selected-columns
                                                 (fn [cols]
                                                   (if-let [c (filter #(some #{(:data %)}  sel) (:columns app))]
                                                     (concat cols c))))))}
           [:i.arrow.right.icon]]
          [:div {:on-click (fn [e]
                        (if-let [sel (selected-values (.querySelector (om/get-node owner) "select[name=selected_columns]"))]
                          (om/update-state! owner :selected-columns
                                            (fn [cols]
                                              (->> cols
                                                   (remove #(some #{(:data %)} sel))
                                                   vec)))))}
           [:i.arrow.left.icon]]]
         [:div.four.wide.column
          [:select {:name "selected_columns" :multiple "multiple"}
           (for [column selected-columns]
             [:option {:value (:data column)} (:title column)])]]
         [:div.one.wide.column
          [:div {:on-click (fn [e]
                             (let [select-box (.querySelector (om/get-node owner) "select[name=selected_columns]")]
                               (when-let [sel (selected-values select-box)]
                                 (om/update-state! owner :selected-columns
                                                   (fn [cols]
                                                     (let [prependee (take-while #(not (some #{(:data %)} sel)) cols)
                                                           selcols   (filter #(some #{(:data %)} sel) cols)
                                                           appendee  (->> cols
                                                                          (drop-while #(not (some #{(:data %)} sel)))
                                                                          (remove #(some #{%} selcols)))]
                                                       (into []
                                                             (concat (drop-last prependee) selcols (take-last 1 prependee) appendee))))))))}
           [:i.arrow.up.icon]]

          [:div {:on-click (fn [e]
                             (let [select-box (.querySelector (om/get-node owner) "select[name=selected_columns]")]
                               (when-let [sel (selected-values select-box)]
                                 (om/update-state! owner :selected-columns
                                                   (fn [cols]
                                                     (let [prependee (take-while #(not (some #{(:data %)} sel)) cols)
                                                           selcols   (filter #(some #{(:data %)} sel) cols)
                                                           appendee  (->> cols
                                                                          (drop-while #(not (some #{(:data %)} sel)))
                                                                          (remove #(some #{%} selcols)))]
                                                       (into []
                                                             (concat prependee (take 1 appendee) selcols (drop 1 appendee)))))))))}
           [:i.arrow.down.icon]]]]]]

      [:div.ui.buttons
       [:button.ui.basic.button
        {:on-click (fn [e]
                     (let [headers (map :data (om/get-state owner :selected-columns))]
                       (search-by-conditions (:entity app)
                                             headers
                                             (om/get-state owner :conditions)
                                             (fn [data]
                                               (let [handsontable (om/get-state owner :handsontable)]
                                                 (.updateSettings handsontable (js-obj "readOnly" true))
                                                 (if (empty? data)
                                                   (do (.loadData handsontable (clj->js [{:code "該当するデータがありません"}]))
                                                       (.updateSettings handsontable (clj->js {:mergeCells [{:row 0 :col 0 :rowspan 1 :colspan (.countCols handsontable)}]})))
                                                   (.loadData (om/get-state owner :handsontable)
                                                              (clj->js (map #(apply js-obj (interleave headers %))
                                                                            (drop 1 data)))))
                                                 (.updateSettings handsontable (js-obj "readOnly" true)))))))}
        [:i.green.check.icon] "適用"]
       [:button.ui.basic.button
        {:on-click (fn [e]
                     (.preventDefault e)
                     (let [headers (map :data (om/get-state owner :selected-columns))]
                       (search-for-download (:entity app)
                                             headers
                                             (om/get-state owner :conditions)
                                             (fn [csv]
                                               (let [blob (goog.fs/getBlobWithProperties (array csv) "text/csv")
                                                     click-event (. js/document createEvent "HTMLEvents")
                                                     anchor (. js/document createElement "a")]
                                                 (.initEvent click-event "click")
                                                 (doto anchor
                                                   (.setAttribute "href" (goog.fs/createObjectUrl blob))
                                                   (.setAttribute "download" "postal.csv"))
                                                 (.dispatchEvent anchor click-event))))))}
        [:i.cloud.download.icon] "ダウンロード"]]
      [:div.handson-container]
      (when editable?
        (om/build action-menu (:entity app)
                  {:opts {:request-hander (fn [handsontable]
                                            (pr-str {:headers (map :data (om/get-state owner :selected-columns)) 
                                                     :data (js->clj (for [i (range (.countRows handsontable))]
                                                                      (.getDataAtRow handsontable i)))}))}}))]))
  
  (did-mount [_]
    (let [headers (->> (om/get-state owner :selected-columns)
                       (map :data)
                       (map name))]
      (api/request (str "/api/" (:entity app) "?cols=" (clojure.string/join "," headers))
                   :GET nil
                   :accept "text/tab-separated-values"
                   :handler (fn [res]
                              (if (empty? res)
                                (println "")
                                (let [handsontable (js/Handsontable.
                                                    (.querySelector (om/get-node owner) ".handson-container")
                                                    (clj->js {:startCols (count headers)
                                                              :startRows 1
                                                              :stretchH  "all"
                                                              :columnSorting true
                                                              :manualColumnResize true
                                                              :rowHeaders true
                                                              :columns  headers
                                                              :beforeChange (fn [changes source]
                                                                              (doseq [change changes]
                                                                                (if-let [col (first (filter #(= (aget change 1) (:data %)) (:columns app)))]
                                                                                  (when (= (:type col) "checkbox")
                                                                                    (let [v (aget change 3)]
                                                                                      (aset change 3 (or (= v 1) (= v "1") (= v "yes") (= v "on") (= v "true"))))))))
                                                              :afterChange (fn [changes source]
                                                                             (doseq [change changes]))}))
                                      headers (map :data (om/get-state owner :selected-columns))]
                                  (set! (.-grid js/window) handsontable)
                                  (om/set-state! owner :handsontable handsontable)
                                  (.loadData handsontable (clj->js (map #(apply js-obj (interleave headers %))
                                                                        (drop 1 res))) )
                                  (.updateSettings handsontable (clj->js {:readOnly true}))))))))
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
