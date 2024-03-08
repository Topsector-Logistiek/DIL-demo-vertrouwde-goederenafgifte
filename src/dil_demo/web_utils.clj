(ns dil-demo.web-utils
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [hiccup2.core :as hiccup]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]])
  (:import (java.text SimpleDateFormat)
           (java.util UUID)))

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

(def carriers {"NL0000000000" "De Vries Transport"})

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

(defn format-date [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd") date))

(defn render-body [site title h & {:keys [flash]}]
  (str "<!DOCTYPE HTML>" (hiccup/html (template site title h :flash flash))))

(defn camelize
  "Convert key `s` from lispy to jsony style."
  [s]
  (let [words (string/split s #"-")]
    (string/join (into [(first words)] (map string/capitalize (rest words))))))

(defn to-json
  "Transform `val` from lispy value to jsony string."
  [val & {:keys [depth pad] :or {depth 0, pad "  "} :as opts}]
  (let [padding (-> depth (repeat pad) (string/join))]
    (str
     padding
     (cond
       (map? val)
       (if (empty? val)
         "{}"
         (str "{\n"
              (string/join ",\n"
                           (for [[k v] val]
                             (str
                              padding pad
                              (to-json (camelize (name k)) (assoc opts :depth 0))
                              ": "
                              (string/trim (to-json v (assoc opts :depth (inc depth)))))))
              "\n"
              padding
              "}"))

       (coll? val)
       (if (empty? val)
         "[]"
         (str "[\n"
              (string/join ",\n"
                           (for [v val]
                             (to-json v (assoc opts :depth (inc depth)))))
              "\n"
              padding
              "]"))

       :else
       (json/write-str val)))))
