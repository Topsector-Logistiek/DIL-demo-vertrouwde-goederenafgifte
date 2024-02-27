(ns dil-demo.wms.web
  (:require [dil-demo.web-utils :as w]))

(defn list-entries [entries]
  [:table
   [:thead
    [:tr
     [:th.status "Status"]
     [:th.ref "Opdracht nr."]
     [:th.date "Datum"]
     [:th.transporter "Transporteur"]
     [:th.goods "Goederen"]
     [:th.actions]]]
   [:tbody
    (for [{:keys [status ref date transporter goods]} entries]
      [:tr.entry
       [:td.status status]
       [:td.ref ref]
       [:td.date date]
       [:td.transporter transporter]
       [:td.goods goods]
       [:td.actions
        [:a.button.button-secondary {:href (str "entry-" ref ".html")} "Openen"]]])]])

(defn show-entry [{:keys [status ref date from from-notes to to-notes goods transporter]}]
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
      [:dd transporter]]]
   [:div.actions
    [:a.button.button-primary {:href (str "entry-" ref "-verify.html")} "Veriferen"]
    [:a.button {:href "index.html"} "Annuleren"]]])

(defn verify-entry [{:keys [ref date from to goods transporter]}]
  [:form.verify
   (w/field {:label "Opdracht nr.", :value ref})
   (w/field {:label "Transporteur", :value transporter})

   [:div.actions
    [:a.button {:onclick "alert('Nog niet geïmplementeerd..')"} "Scan QR"]]

   (w/field {:name "license-plate", :label "Kenteken", :type "text"})
   (w/field {:name "chauffeur-id", :label "Chauffeur ID", :type "text"})

   [:div.actions
    [:a.button.button-primary {:href (str "entry-" ref "-accepted.html")} "Veriferen"]
    [:a.button {:href "index.html"} "Annuleren"]]])

(defn accepted-entry [{:keys [ref transporter]}]
  [:section
   [:h2.verification-accepted "Afgifte akkoord"]
   [:p "Transportopdracht " [:q ref] " goedgekeurd voor transporteur " [:q transporter] "."]
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
