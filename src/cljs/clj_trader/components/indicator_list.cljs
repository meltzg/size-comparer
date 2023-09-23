(ns clj-trader.components.indicator-list
  (:require [clj-trader.components.inputs :refer [form-selector]]
            [rum.core :as rum]
            ["@mui/icons-material/Add$default" :as AddIcon]
            ["@mui/icons-material/Delete$default" :as DeleteIcon]
            ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
            ["@mui/material" :refer [Accordion
                                     AccordionActions
                                     AccordionDetails
                                     AccordionSummary
                                     Button
                                     Fab
                                     FormControl
                                     IconButton
                                     InputLabel
                                     Menu
                                     MenuItem
                                     Select
                                     Stack
                                     TextField
                                     Typography]]))

(def component-state (atom {}))

(defn handle-indicator-menu [event]
  (swap! component-state assoc :anchor-element (.-currentTarget event)))

(defn handle-indicator-close []
  (swap! component-state dissoc :anchor-element))

(defmulti option-control #(-> %3 :type keyword))

(defmethod option-control :choice [on-change opt-key {:keys [display-name opts value] :as config}]
  (form-selector {:value     value
                  :label     display-name
                  :on-change #(on-change opt-key (assoc config :value (.. % -target -value)))
                  :items     opts}))

(defmethod option-control :int [on-change opt-key {:keys [display-name min max value] :as config}]
  [:> TextField {:label    display-name
                 :type     "number"
                 :value    value
                 :onChange #(on-change opt-key (assoc config :value (js/parseInt (.. % -target -value))))}])

(rum/defc indicator-config [indicator-key indicator-info on-change on-delete]
  (let [on-option-change (fn [opt-key opt-config]
                           (on-change indicator-key (assoc-in indicator-info [:opts opt-key] opt-config)))]
    [:> Accordion
     [:> AccordionSummary {:expandIcon (rum/adapt-class ExpandMoreIcon {})}
      [:> Typography (:name indicator-info)]]
     [:> AccordionDetails {}
      (map #(apply (partial option-control on-option-change) %) (:opts indicator-info))]
     [:> AccordionActions
      [:> Button {:variant "contained"} "Save"]
      [:> Button {:variant "contained"} "Reset"]
      [:> IconButton {:color   "error"
                      :onClick #(on-delete indicator-key)}
       [:> DeleteIcon {}]]]]))

(rum/defc indicator-list < rum/reactive [indicator-configs indicator-config-info on-add on-delete]
  [:> Stack {:direction "column"}
   (map (fn [[indicator-key indicator]]
          (indicator-config indicator-key indicator on-add on-delete)) indicator-configs)
   [:> Fab {:color         "primary"
            :size          "small"
            :id            "add-indicator"
            :aria-controls (when (:anchor-element (rum/react component-state)) "indicator-menu")
            :aria-haspopup "true"
            :aria-expanded (when (:anchor-element (rum/react component-state)) "true")
            :onClick       handle-indicator-menu}
    [:> AddIcon {}]]
   [:> Menu {:id            "indicator-menu"
             :anchorEl      (:anchor-element (rum/react component-state))
             :open          (some? (:anchor-element (rum/react component-state)))
             :onClose       handle-indicator-close
             :MenuListProps {:aria-labelledby "add-indicator"}}
    (map (fn [[indicator-key indicator-options]]
           [:> MenuItem {:onClick (fn []
                                    (handle-indicator-close)
                                    (on-add indicator-key indicator-options))}
            (:name indicator-options)]) indicator-config-info)]])
