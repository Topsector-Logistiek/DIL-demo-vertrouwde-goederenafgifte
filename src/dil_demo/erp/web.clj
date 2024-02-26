(ns dil-demo.erp.web
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

(defn template [title main]
  (let [title (str "ERP — " title)]
    [:html
     [:head
      [:title title]
      [:link {:rel "stylesheet", :href "../assets/erp.css"}]
      [:link {:rel "stylesheet", :href "../assets/base.css"}]]
     [:body
      [:nav [:h1 title]]
      [:main main]
      [:footer]
      (for [[id vals] {:statuses statuses, :locations locations, :goods goods, :transporters transporters}]
        [:datalist {:id id}
         (for [val vals]
           [:option {:value val}])])]]))

(defn list-entries [entries]
  (let [actions [:a.button.button-primary {:href (str "entry-new.html")} "Nieuw"]]
    [:table
     [:thead
      [:tr
       [:th.date "Status"]
       [:th.date "Order nr."]
       [:th.date "Datum"]
       [:th.from "Laadadres"]
       [:th.to "Losadres"]
       [:th.goods "Goederen"]
       [:th.actions actions]]]
     [:tbody
      (for [{:keys [status ref date from to goods]} entries]
        [:tr.entry
         [:td.status status]
         [:td.ref ref]
         [:td.date date]
         [:td.from from]
         [:td.to to]
         [:td.goods goods]
         [:td.actions
          [:a.button.button-secondary {:href (str "entry-" ref ".html")} "Openen"]]])]
     [:tfoot
      [:tr
       [:td.actions {:colspan 7} actions]]]]))

(defn field [{:keys [name label type value list]}]
  [:div.field
   [:label {:for name} label]
   (cond
     (nil? type)
     [:input {:name name, :disabled true, :value value}]

     (= "textarea" type)
     [:textarea {:name name} value]

     :else
     [:input {:name name, :type type, :value value, :list list}])])

(defn edit-entry [{:keys [status ref date from from-notes to to-notes goods transporter]}]
  [:form.edit
   (field {:name "status", :label "Status", :value status})
   (field {:name "ref", :label "Order nr.", :type "number", :value ref})
   (field {:name "date", :label "Datum", :type "date", :value date})
   (field {:name "from", :label "Laadadres", :type "text", :value from, :list "locations"})
   (field {:name "from-notes", :label "Opmerkingen", :type "textarea", :value from-notes})
   (field {:name "to", :label "Losadres", :type "text", :value to, :list "locations"})
   (field {:name "to-notes", :label "Opmerkingen", :type "textarea", :value to-notes})
   (field {:name "goods", :label "Goederen", :type "text", :value goods, :list "goods"})
   (field {:name "transporter", :label "Transporter", :type "text", :value transporter, :list "transporters"})
   [:div.actions
    [:button.button.button-primary {:type "submit"} "Bewaren"]
    [:a.button.button-secondary {:href (str "entry-" ref "-publish.html")} "Publiseren"]
    [:a.button {:href "index.html"} "Annuleren"]]])

(defn publish-entry [{:keys [ref date from to goods transporter]}]
  [:form.publish
   [:section.details
    [:dl
     [:div
      [:dt "Order nr."]
      [:dd ref]]
     [:div
      [:dt "Datum"]
      [:dd date]]
     [:div
      [:dt "Transporteur"]
      [:dd transporter]]]]
   [:section.trip
    [:fieldset.from
     [:legend "Laadadres"]
     [:h3 from]
     [:pre "Kerkstraat 1\n1234 AB  Nergenshuizen"]]
    [:fieldset.to
     [:legend "Losadres"]
     [:h3 to]
     [:pre "Dorpsweg 2\n4321 YZ  Andershuizen"]]]
   [:section.goods
    [:fieldset
     [:legend "Goederen"]
     [:pre goods]]]
   [:div.actions
    [:a.button.button-primary {:href (str "entry-" ref "-sent.html")
                               :onclick "return confirm('Zeker weten?')"}
     "Versturen"]
    [:a.button {:href (str "entry-" ref ".html")} "Annuleren"]]])

(defn sent-entry [{:keys [from transporter]}]
  [:section
   [:p "Transportopdracht verstuurd naar locatie " [:q from] " en transporteur " [:q transporter] "."]
   [:div.actions
    [:a.button {:href "index.html"} "Terug naar overzicht"]]])

(defn pick [coll]
  (first (shuffle coll)))

(defn format-date [date]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") date))

(defn days-from-now [i]
  (java.util.Date/from (.plusSeconds (java.time.Instant/now) (* 60 60 24 i))))

(def entries
  (let [start 1337]
    (mapv (fn [i]
            (let [from (pick locations)]
              {:ref         (+ i start 20240000)
               :status      (pick statuses)
               :date        (format-date (days-from-now (/ i 5)))
               :from        from
               :to          (pick (disj locations from))
               :goods       (pick goods)
               :transporter (pick transporters)}))
          (range 20))))

(defn to-html [fn title h]
  (println fn)
  (with-open [writer (io/writer fn :encoding "UTF-8")]
    (binding [*out* writer]
      (println (str "<!DOCTYPE HTML>" (hiccup/html (template title h)))))))

(defn -main []
  (to-html "erp/index.html"
           "Orders"
           (list-entries entries))
  (to-html "erp/entry-new.html"
           "New entry"
           (edit-entry {:ref    (inc (apply max (->> entries (map :ref))))
                        :status "New"}))
  (doseq [{:keys [ref] :as entry} entries]
    (to-html (str "erp/entry-" ref ".html")
             "Order"
             (edit-entry entry))
    (to-html (str "erp/entry-" ref "-publish.html")
             "Order publiceren"
             (publish-entry entry))
    (to-html (str "erp/entry-" ref "-sent.html")
             "Order gepubliceerd"
             (sent-entry entry))))
