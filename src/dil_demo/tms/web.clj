(ns dil-demo.tms.web
  (:require [dil-demo.web-utils :as w]))

(defn list-entries [entries]
  [:table
   [:thead
    [:tr
     [:th.status "Status"]
     [:th.ref "Opdracht nr."]
     [:th.date "Datum"]
     [:th.from "Laadadres"]
     [:th.to "Losadres"]
     [:th.goods "Goederen"]
     [:th.actions]]]
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
        [:a.button.button-secondary {:href (str "entry-" ref ".html")} "Openen"]]])]])

(defn edit-entry [{:keys [ref date from to goods]}]
  [:form
   [:section.details
    [:dl
     [:div
      [:dt "Order nr."]
      [:dd ref]]
     [:div
      [:dt "Datum"]
      [:dd date]]]
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

    (w/field {:name "license-plate", :label "Kenteken", :type "text"})
    (w/field {:name "chauffeur-id", :label "Chauffeur ID", :type "text"})

    [:div.actions
     [:a.button.button-primary {:href (str "entry-" ref "-assigned.html")} "Toewijzen"]
     [:a.button {:href "index.html"} "Annuleren"]]]])

(defn assigned-entry [{:keys [ref transporter]}]
  [:section
   [:p "Transportopdracht " [:q ref] " toegewezen."]
   [:div.actions
    [:a.button {:href "index.html"} "Terug naar overzicht"]]])

(def entries
  (let [start 1337]
    (mapv (fn [i]
            (let [from (w/pick w/locations)]
              {:ref         (+ i start 20240000)
               :status      (w/pick w/statuses)
               :date        (w/format-date (w/days-from-now (/ i 5)))
               :from        from
               :to          (w/pick (disj w/locations from))
               :goods       (w/pick w/goods)
               :transporter (w/pick w/transporters)}))
          (range 20))))

(defn -main []
  (let [to-html (partial w/to-html "tms")]
    (to-html "tms/index.html"
             "TMS — Transportopdrachten"
             (list-entries entries))
    (doseq [{:keys [ref] :as entry} entries]
      (to-html (str "tms/entry-" ref ".html")
               "TMS — Transportopdracht"
               (edit-entry entry))
      (to-html (str "tms/entry-" ref "-assigned.html")
               "TMS — Transportopdracht toegewezen aan chauffeur"
               (assigned-entry entry)))))
