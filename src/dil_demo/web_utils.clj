(ns dil-demo.web-utils
  (:require [clojure.java.io :as io]
            [hiccup2.core :as hiccup]))

(def statuses #{"Nieuw"
                "Ingepland"
                "Gepubliceerd"
                "In transit"
                "Gate in"
                "Gate out"})
(def locations #{"Intel, Schiphol"
                 "Nokia, Stockholm"
                 "Bol, Waalwijk"
                 "AH, Pijnacker"
                 "Jumbo, Tilburg"
                 "AH Winkel 23, Drachten"})
(def goods #{"Toiletpapier"
             "Bananen"
             "Smartphones"
             "Cola"
             "T-shirts"})
(def transporters #{"De Vries transport"
                    "Jansen logistiek"
                    "Dijkstra vracht"
                    "de Jong vervoer"})

(defn template [site title main]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:title title]
    [:link {:rel "stylesheet", :href (str "../assets/" site ".css")}]
    [:link {:rel "stylesheet", :href "../assets/base.css"}]]
   [:body
    [:nav [:h1 title]]
    [:main main]
    [:footer]
    (for [[id vals] {:statuses statuses, :locations locations, :goods goods, :transporters transporters}]
      [:datalist {:id id}
       (for [val vals]
         [:option {:value val}])])]])

(defn field [{:keys [name label type value disabled] :as opts}]
  [:div.field
   [:label {:for name} label]
   (if (= "textarea" type)
     [:textarea {:name name, :disabled disabled} value]
     [:input opts])])

(defn pick [coll]
  (first (shuffle coll)))

(defn format-date [date]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") date))

(defn days-from-now [i]
  (java.util.Date/from (.plusSeconds (java.time.Instant/now) (* 60 60 24 i))))

(defn render-body [site title h]
  (str "<!DOCTYPE HTML>" (hiccup/html (template site title h))))
