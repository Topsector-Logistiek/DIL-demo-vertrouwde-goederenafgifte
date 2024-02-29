(ns dil-demo.wms.web
  (:require [dil-demo.web-utils :as w]))

(defn list-entries [entries]
  [:table
   [:thead
    [:tr
     [:th.date "Ophaaldatum"]
     [:th.ref "Opdracht nr."]
     [:th.transporter "Vervoerder"]
     [:th.goods "Goederen"]
     [:th.status "Status"]
     [:th.actions]]]
   [:tbody
    (for [{:keys [status ref pickup-date transporter goods]} entries]
      [:tr.entry
       [:td.date pickup-date]
       [:td.ref ref]
       [:td.transporter transporter]
       [:td.goods goods]
       [:td.status status]
       [:td.actions
        [:a.button.button-secondary {:href (str "entry-" ref ".html")} "Openen"]]])]])

(defn show-entry [{:keys [ref pickup-date pickup-notes transporter]}]
  [:section.details
    [:dl
     [:div
      [:dt "Klantorder nr."]
      [:dd ref]]
     [:div
      [:dt "Ophaaldatum"]
      [:dd pickup-date]]
     [:div
      [:dt "Vervoerder"]
      [:dd transporter]]
     [:div
      [:dt "Opmerkingen"]
      [:dd [:blockquote.notes pickup-notes]]]]
   [:div.actions
    [:a.button.button-primary {:href (str "entry-" ref "-verify.html")} "Veriferen"]
    [:a.button {:href "index.html"} "Annuleren"]]])

(defn verify-entry [{:keys [ref pickup-notes transporter]}]
  [:form.verify
   (w/field {:label "Opdracht nr.", :value ref, :disabled true})
   (w/field {:label "Vervoerder", :value transporter, :disabled true})
   (w/field {:label "Opmerkingen", :value pickup-notes, :type "textarea", :disabled true})

   [:div.actions
    [:a.button {:onclick "alert('Nog niet geïmplementeerd..')"} "Scan QR"]]

   (w/field {:name "license-plate", :label "Kenteken"})
   (w/field {:name "chauffeur-id", :label "Chauffeur ID"})

   [:div.actions
    [:a.button.button-primary {:href (str "entry-" ref "-accepted.html")} "Veriferen"]
    [:a.button {:href "index.html"} "Annuleren"]]])

(defn accepted-entry [{:keys [ref transporter]}]
  [:div
   [:section
    [:h2.verification-accepted "Afgifte akkoord"]
    [:p "Transportopdracht " [:q ref] " goedgekeurd voor transporteur " [:q transporter] "."]
    [:div.actions
     [:a.button {:href "index.html"} "Terug naar overzicht"]]]
   [:details.explaination
    [:summary "Uitleg"]
    [:ol
     [:li
      [:h3 "Check Authorisatie Vervoerder names de Verlader"]
      [:p "API call naar " [:strong "AR van de Verlader"] " om te controleren of Vervoerder names Verlader de transportopdracht uit mag voeren."]
      [:ul [:li "Klantorder nr."] [:li "Vervoerder ID"]]]
     [:li
      [:h3 "Check Authorisatie Chauffeur en Kenteken names de Vervoerder"]
      [:p "API call naar " [:strong "AR van de Vervoerder"] " om te controleren of de Chauffeur met Kenteken de transportopdracht"]
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
               :delivery-address (w/pick (disj w/locations pickup-address))
               :goods            (w/pick w/goods)
               :transporter      (w/pick w/transporters)}))
          (range 20))))

(defn -main []
  (let [to-html (partial w/to-html "wms")]
    (to-html "wms/index.html"
             "WMS — Transportopdrachten"
             (list-entries entries))
    (doseq [{:keys [ref] :as entry} entries]
      (to-html (str "wms/entry-" ref ".html")
               "WMS — Transportopdracht"
               (show-entry entry))
      (to-html (str "wms/entry-" ref "-verify.html")
               "WMS — Transportopdracht verificatie"
               (verify-entry entry))
      (to-html (str "wms/entry-" ref "-accepted.html")
               "WMS — Transportopdracht geaccepteerd"
               (accepted-entry entry)))))
