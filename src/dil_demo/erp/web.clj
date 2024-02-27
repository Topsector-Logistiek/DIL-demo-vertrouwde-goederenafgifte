(ns dil-demo.erp.web
  (:require [dil-demo.web-utils :as w]))

(defn list-entries [entries]
  (let [actions [:a.button.button-primary {:href (str "entry-new.html")} "Nieuw"]]
    [:table
     [:thead
      [:tr
       [:th.status "Status"]
       [:th.ref "Order nr."]
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

(defn edit-entry [{:keys [status ref date from from-notes to to-notes goods transporter]}]
  [:form.edit
   (w/field {:name "status", :label "Status", :value status})
   (w/field {:name "ref", :label "Order nr.", :type "number", :value ref})
   (w/field {:name "date", :label "Datum", :type "date", :value date})
   (w/field {:name "from", :label "Laadadres", :type "text", :value from, :list "locations"})
   (w/field {:name "from-notes", :label "Opmerkingen", :type "textarea", :value from-notes})
   (w/field {:name "to", :label "Losadres", :type "text", :value to, :list "locations"})
   (w/field {:name "to-notes", :label "Opmerkingen", :type "textarea", :value to-notes})
   (w/field {:name "goods", :label "Goederen", :type "text", :value goods, :list "goods"})
   (w/field {:name "transporter", :label "Transporter", :type "text", :value transporter, :list "transporters"})
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
  (let [to-html (partial w/to-html "erp")]
    (to-html "erp/index.html"
             "ERP — Orders"
             (list-entries entries))
    (to-html "erp/entry-new.html"
             "ERP — Nieuwe entry"
             (edit-entry {:ref    (inc (apply max (->> entries (map :ref))))
                          :status "New"}))
    (doseq [{:keys [ref] :as entry} entries]
      (to-html (str "erp/entry-" ref ".html")
               "ERP — Order"
               (edit-entry entry))
      (to-html (str "erp/entry-" ref "-publish.html")
               "ERP — Order publiceren"
               (publish-entry entry))
      (to-html (str "erp/entry-" ref "-sent.html")
               "ERP — Order gepubliceerd"
               (sent-entry entry)))))
