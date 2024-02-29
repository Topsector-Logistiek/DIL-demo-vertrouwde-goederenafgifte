(ns dil-demo.tms.web
  (:require [dil-demo.web-utils :as w]))

(defn list-entries [entries]
  [:table
   [:thead
    [:tr
     [:th.date "Ophaaldatum"]
     [:th.ref "Klantorder nr."]
     [:th.address "Ophaaladres"]
     [:th.address "Afleveradres"]
     [:th.goods "Goederen"]
     [:th.status "Status"]
     [:th.actions]]]
   [:tbody
    (for [{:keys [status ref pickup-date pickup-address delivery-address goods]} entries]
      [:tr.entry
       [:td.date pickup-date]
       [:td.ref ref]
       [:td.address pickup-address]
       [:td.address delivery-address]
       [:td.goods goods]
       [:td.status status]
       [:td.actions
        [:a.button.button-secondary {:href (str "entry-" ref ".html")} "Openen"]]])]])

(defn edit-entry [{:keys [ref pickup-date pickup-address pickup-notes delivery-date delivery-address delivery-notes goods]}]
  [:form
   [:section.details
    [:dl
     [:div
      [:dt "Klantorder nr."]
      [:dd ref]]
     [:div
      [:dt "Ophaaldatum"]
      [:dd pickup-date]]
     [:div
      [:dt "Afleverdatum"]
      [:dd delivery-date]]]
    [:section.trip
     [:fieldset.pickup-address
      [:legend "Ophaaladres"]
      [:h3 pickup-address]
      [:pre "Kerkstraat 1\n1234 AB  Nergenshuizen"]
      [:blockquote.notes pickup-notes]]
     [:fieldset.delivery-address
      [:legend "Afleveradres"]
      [:h3 delivery-address]
      [:pre "Dorpsweg 2\n4321 YZ  Andershuizen"]
      [:blockquote.notes delivery-notes]]]
    [:section.goods
     [:fieldset
      [:legend "Goederen"]
      [:pre goods]]]

    (w/field {:name "license-plate", :label "Kenteken", :type "text"})
    (w/field {:name "chauffeur-id", :label "Chauffeur ID", :type "text"})

    [:div.actions
     [:a.button.button-primary {:href (str "entry-" ref "-assigned.html")} "Toewijzen"]
     [:a.button {:href "index.html"} "Annuleren"]]]])

(defn assigned-entry [{:keys [ref]}]
  [:div
   [:section
    [:p "Transportopdracht " [:q ref] " toegewezen."]
    [:img {:src "../assets/qr-sample.png"}]
    [:div.actions
     [:a.button {:href "index.html"} "Terug naar overzicht"]]]
   [:details.explaination
    [:summary "Uitleg"]
    [:ol
     [:li
      [:h3 "Autoriseer de Chauffeur names de Vervoerder voor de Klantorder vervoerd met Kenteken"]
      [:p "API call naar " [:strong "AR van de Vervoerder"] " om een autorisatie te registeren"]
      [:ul [:li "Klantorder nr."] [:li "Chauffeur ID"] [:li "Kenteken"]]]]]])

(def entries
  (let [start 1337]
    (mapv (fn [i]
            (let [pickup-address (w/pick w/locations)]
              {:ref              (+ i start 20240000)
               :status           (w/pick w/statuses)
               :pickup-date      (w/format-date (w/days-from-now (/ i 5)))
               :pickup-address   pickup-address
               :pickup-notes     "hier komen ophaalnotities"
               :delivery-date    (w/format-date (w/days-from-now (/ i 5)))
               :delivery-address (w/pick (disj w/locations pickup-address))
               :delivery-notes   "hier komen aflevernotities"
               :goods            (w/pick w/goods)
               :transporter      (w/pick w/transporters)}))
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
