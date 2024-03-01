(ns dil-demo.web-utils
  (:require [hiccup2.core :as hiccup]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]])
  (:import [java.util UUID Date]
           [java.text SimpleDateFormat]
           [java.time Instant]))

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

(def carriers #{"De Vries transport"
                    "Jansen logistiek"
                    "Dijkstra vracht"
                    "de Jong vervoer"})

(defn anti-forgery-input []
  [:input {:name "__anti-forgery-token", :value *anti-forgery-token*, :type "hidden"}])

(defn template [site title main & {:keys [flash]}]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:title title]
    [:link {:rel "stylesheet", :href (str "../assets/" site ".css")}]
    [:link {:rel "stylesheet", :href "../assets/base.css"}]]
   [:body
    [:nav [:h1 title]]
    [:main
     (for [[type message] flash]
       [:div.flash {:class (str "flash-" (name type))} message])
     main]
    [:footer]]])

(defn field [{:keys [name label type value list] :as opts}]
  (let [list-id (str "list-" (UUID/randomUUID))
        datalist [:datalist {:id list-id} (for [v list] [:option {:value v}])]
        opts (if list (assoc opts :list list-id) opts)]
    [:div.field
     [:label {:for name} label]
     (cond
       (= "textarea" type)
       [:textarea (dissoc opts :label :type) value]

       (= "select" type)
       [:select (dissoc opts :label :type :list :value)
        (for [option list]
          (if (vector? option)
            (let [[k v] option]
              [:option {:value k, :selected (= k value)} v])
            [:option {:value option, :selected (= option value)} option]))]

       :else
       [:input (-> opts
                   (dissoc :label)
                   (assoc :list list-id))])
     (when list
       datalist)]))

(defn delete-button [path]
  [:form.delete {:method "POST", :action path}
   (anti-forgery-input)
   [:input {:type "hidden", :name "_method", :value "DELETE"}]
   [:button {:type "submit", :onclick "return confirm('Zeker weten?')"} "Verwijderen"]])

(defn pick [coll]
  (first (shuffle coll)))

(defn format-date [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd") date))

(defn days-from-now [i]
  (Date/from (.plusSeconds (Instant/now) (* 60 60 24 i))))

(defn render-body [site title h & {:keys [flash]}]
  (str "<!DOCTYPE HTML>" (hiccup/html (template site title h :flash flash))))
