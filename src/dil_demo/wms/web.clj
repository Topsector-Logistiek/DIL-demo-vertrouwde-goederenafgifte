(ns dil-demo.wms.web
  (:require [compojure.core :refer [defroutes GET POST]]
            [dil-demo.web-utils :as w]
            [ring.util.response :refer [redirect]]))

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
        [:a.button.button-secondary {:href (str "entry-" ref)} "Openen"]]])]])

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
    [:a.button.button-primary {:href (str "verify-" ref)} "Veriferen"]
    [:a.button {:href "."} "Annuleren"]]])

(defn verify-entry [{:keys [ref pickup-notes transporter]}]
  [:form {:method "POST", :action (str "verify-" ref)}
   (w/field {:label "Opdracht nr.", :value ref, :disabled true})
   (w/field {:label "Vervoerder", :value transporter, :disabled true})
   (w/field {:label "Opmerkingen", :value pickup-notes, :type "textarea", :disabled true})

   [:div.actions
    [:a.button {:onclick "alert('Nog niet geïmplementeerd..')"} "Scan QR"]]

   (w/field {:name "license-plate", :label "Kenteken", :required true})
   (w/field {:name "chauffeur-ref", :label "Chauffeur ID", :required true})

   [:div.actions
    [:button.button-primary {:type "submit"} "Veriferen"]
    [:a.button {:href "."} "Annuleren"]]])

(defn accepted-entry [{:keys [ref transporter]}]
  [:div
   [:section
    [:h2.verification.verification-accepted "Afgifte akkoord"]
    [:p "Transportopdracht " [:q ref] " goedgekeurd voor transporteur " [:q transporter] "."]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
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

(defn rejected-entry [{:keys [ref transporter]}]
  [:div
   [:section
    [:h2.verification.verification-rejected "Afgifte NIET akkoord"]
    [:p "Transportopdracht " [:q ref] " is AFGEKEURD voor transporteur " [:q transporter] "."]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
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
              {:ref              (str (+ i start 20240000))
               :status           (w/pick w/statuses)
               :pickup-date      (w/format-date (w/days-from-now (/ i 5)))
               :pickup-address   pickup-address
               :pickup-notes     "hier komen ophaalnotities"
               :delivery-address (w/pick (disj w/locations pickup-address))
               :goods            (w/pick w/goods)
               :transporter      (w/pick w/transporters)}))
          (range 20))))

(defn get-entry [ref]
  (first (filter #(= ref (:ref %)) entries)))



(defn render-body [title h]
  (w/render-body "wms" (str "WMS — " title ) h))

(defroutes handler
  (GET "/" []
    {:body (render-body "Transportopdrachten"
                        (list-entries entries))})

  (GET "/entry-:ref" [ref]
    (when-let [entry (get-entry ref)]
      {:body (render-body (str "Transportopdracht: " ref)
                          (show-entry entry))}))

  (GET "/verify-:ref" [ref]
    (when-let [entry (first (filter #(= ref (:ref %)) entries))]
      {:body (render-body (str "verificatie Transportopdracht: " ref)
                          (verify-entry entry))}))

  (POST "/verify-:ref" [ref]
    ;; TODO
    (redirect (str (if (= 0 (int (* 2 (rand))))
                     "rejected-"
                     "accepted-") ref) :see-other))

  (GET "/accepted-:ref" [ref]
    (when-let [entry (first (filter #(= ref (:ref %)) entries))]
      {:body (render-body (str "Transportopdracht (" ref ") geaccepteerd")
                          (accepted-entry entry))}))

  (GET "/rejected-:ref" [ref]
    (when-let [entry (first (filter #(= ref (:ref %)) entries))]
      {:body (render-body (str "Transportopdracht (" ref ") afgewezen")
                          (rejected-entry entry))})))
