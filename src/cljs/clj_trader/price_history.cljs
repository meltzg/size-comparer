(ns clj-trader.price-history
  (:require [ajax.core :as ajax]
            [clj-trader.date-selector :refer [date-selector]]
            [clj-trader.utils :as utils :refer [api-url]]
            ["@canvasjs/react-charts" :as CanvasJSReact]
            ["@mui/material" :refer [Button
                                     FormControl
                                     FormControlLabel
                                     InputLabel
                                     MenuItem
                                     Select
                                     Stack
                                     Switch
                                     TextField
                                     TableContainer
                                     Table
                                     TableRow
                                     TableHead
                                     TableCell
                                     TableBody]]
            [goog.string :as gstring]
            [goog.string.format]
            [rum.core :as rum]))

(def CanvasJSChart (.. CanvasJSReact -default -CanvasJSChart))

(def component-state (atom {:use-start-date false
                            :use-end-date   false
                            :period-type    :day
                            :periods        1
                            :frequency-type :minute
                            :frequency      1
                            :start-date     (utils/yesterday)
                            :end-date       (js/Date.)
                            :chart-data     []
                            :table-data     []}))

(def period-types
  [:day
   :month
   :year
   :ytd])

(def frequency-types
  [:minute
   :day
   :weekly
   :monthly])

(def valid-periods
  {:day   [1 2 3 4 5 10]
   :month [1 2 3 6]
   :year  [1 2 3 5 10 15 20]
   :ytd   [1]})

(def valid-frequency-type-for-period
  {:day   [:minute]
   :month [:day :weekly]
   :year  [:day :weekly :monthly]
   :ytd   [:day :weekly]})

(def valid-frequencies
  {:minute  [1 5 10 15 30]
   :day     [1]
   :weekly  [1]
   :monthly [1]})

(defn price-history->chart-data [{:keys [symbol price-candles]}]
  {:type               "candlestick"
   :showInLegend       true
   :name               symbol
   :yValueFormatString "$###0.00"
   :dataPoints         (mapv (fn [candle]
                               {:x (js/Date. (:datetime candle))
                                :y [(:open candle)
                                    (:high candle)
                                    (:low candle)
                                    (:close candle)]})
                             price-candles)})

(defn price-history->table-data [{:keys [symbol stats]}]
  (prn "STATS" stats)
  (assoc stats :symbol symbol))

(defn refresh-data []
  (ajax/GET (str api-url "priceHistory")
            {:format          :edn
             :response-format :json
             :keywords?       true
             :params          (-> {:period-type    (:period-type @component-state)
                                   :periods        (:periods @component-state)
                                   :frequency-type (:frequency-type @component-state)
                                   :frequency      (:frequency @component-state)}
                                  (conj (when (:use-start-date @component-state)
                                          [:start-date (.getTime (:start-date @component-state))]))
                                  (conj (when (:use-end-date @component-state)
                                          [:end-date (.getTime (:end-date @component-state))])))
             :handler         (fn [price-histories]
                                (swap! component-state
                                       assoc
                                       :chart-data
                                       (mapv price-history->chart-data price-histories)
                                       :table-data
                                       (mapv price-history->table-data price-histories)))}))

(defn- handle-legend-click [e]
  (if (or (nil? (.. e -dataSeries -visible))
          (true? (.. e -dataSeries -visible)))
    (set! (.. e -dataSeries -visible) false)
    (set! (.. e -dataSeries -visible) true))
  (.render (.-chart e)))

(rum/defc price-chart [chart-data]
  [:> CanvasJSChart {:options {:title            {:text "Price History"}
                               :zoomEnabled      true
                               :animationEnabled true
                               :exportEnabled    true
                               :axis             {:prefix "$"
                                                  :title  "Price (USD)"}
                               :legend           {:cursor    "pointer"
                                                  :itemclick handle-legend-click}
                               :data             (clj->js chart-data)}}])

(rum/defc stats-table [stats-data]
  [:> TableContainer
   [:> Table
    [:> TableHead
     [:> TableRow {:sx {:backgroundColor "lightgray"}}
      (concat [[:> TableCell "Symbol"]]
              (map (fn [column] [:> TableCell {:align "right" :key column}
                                 (name column)])
                   (->> stats-data first keys (remove #{:symbol}) sort)))]]
    [:> TableBody
     (map (fn [row] [:> TableRow {:key (:symbol row)}
                     (concat [[:> TableCell (:symbol row)]]
                             (map (fn [key] [:> TableCell {:align "right"} (gstring/format "%.2f" (key row))])
                                  (->> stats-data first keys (remove #{:symbol}) sort)))])
          stats-data)]]])

(rum/defc frequency-period-control < rum/reactive []
  [:> Stack {:direction  "row"
             :alignItems "center"
             :spacing    1
             :paddingTop 1}
   [:> FormControl {:sx {:m 1 :minWidth 180}}
    [:> InputLabel "Period Type"]
    [:> Select {:value    (name (:period-type (rum/react component-state)))
                :label    "Period Type"
                :onChange #(swap! component-state assoc :period-type (keyword (.. % -target -value)))}
     (map (fn [period-type] [:> MenuItem {:key period-type :value (name period-type)} (name period-type)]) period-types)]]
   [:> FormControl {:sx {:m 1 :minWidth 90}}
    [:> InputLabel "# Periods"]
    [:> Select {:value    (:periods (rum/react component-state))
                :label    "# Periods"
                :onChange #(swap! component-state assoc :periods (.. % -target -value))}
     (map (fn [periods] [:> MenuItem {:key periods :value periods} periods]) ((:period-type (rum/react component-state)) valid-periods))]]
   [:> FormControl {:sx {:m 1 :minWidth 180}}
    [:> InputLabel "Frequency Type"]
    [:> Select {:value    (name (:frequency-type (rum/react component-state)))
                :label    "Frequency Type"
                :onChange #(swap! component-state assoc :frequency-type (keyword (.. % -target -value)))}
     (map (fn [frequency-type] [:> MenuItem {:key frequency-type :value (name frequency-type)} (name frequency-type)]) ((:period-type (rum/react component-state)) valid-frequency-type-for-period))]]
   [:> FormControl {:sx {:m 1 :minWidth 90}}
    [:> InputLabel "Frequency"]
    [:> Select {:value    (:frequency (rum/react component-state))
                :label    "Frequency"
                :onChange #(swap! component-state assoc :frequency (.. % -target -value))}
     (map (fn [frequency] [:> MenuItem {:key frequency :value frequency} frequency]) ((:frequency-type (rum/react component-state)) valid-frequencies))]]])

(rum/defc start-end-control < rum/reactive []
  [:> Stack {:direction "column" :spacing 0.5}
   [:> Stack {:direction "row" :spacing 0.5}
    [:> FormControlLabel {:label   "Use Start Date"
                          :control (rum/adapt-class Switch {:onChange #(swap! component-state
                                                                              assoc
                                                                              :use-start-date
                                                                              (.. % -target -checked))
                                                            :checked  (:use-start-date (rum/react component-state))})}]
    (when (:use-start-date (rum/react component-state))
      (date-selector (:start-date (rum/react component-state))
                     #(swap! component-state assoc :start-date %)))]
   [:> Stack {:direction "row" :soacing 0.5}
    [:> FormControlLabel {:label   "Use End Date"
                          :control (rum/adapt-class Switch {:onChange #(swap! component-state
                                                                              assoc
                                                                              :use-end-date
                                                                              (.. % -target -checked))
                                                            :checked  (:use-end-date (rum/react component-state))})}]
    (when (:use-end-date (rum/react component-state))
      (date-selector (:end-date (rum/react component-state))
                     #(swap! component-state assoc :end-date %)))]])

(rum/defc chart-settings []
  [:> Stack {:direction "column" :spacing 0.5}
   (start-end-control)
   (frequency-period-control)])

(rum/defc price-history < rum/reactive []
  [:div.horizontal
   [:div
    [:> Stack {:direction "row" :spacing 1}
     (price-chart (:chart-data (rum/react component-state)))]
    [:> Stack {:direction "row" :spacing 1}
     (chart-settings)
     [:> Button {:variant "contained"
                 :onClick refresh-data}
      "Refresh"]]]
   (stats-table (:table-data (rum/react component-state)))])
