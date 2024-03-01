(ns dil-demo.erp.web
  (:require [compojure.core :refer [GET POST defroutes]]
            [dil-demo.web-utils :as w]
            [ring.util.response :refer [redirect]]))

(defn list-entries [entries]
  (let [actions [:a.button.button-primary {:href "entry-new"} "Nieuw"]]
    [:table
     [:thead
      [:tr
       [:th.order-date "Orderdatum"]
       [:th.ref "Klantorder nr."]
       [:th.pickup-address "Ophaaladres"]
       [:th.delivery-address "Afleveradres"]
       [:th.goods "Goederen"]
       [:th.status "Status"]
       [:td.actions actions]]]
     [:tbody
      (for [{:keys [status ref order-date pickup-address delivery-address goods]} entries]
        [:tr.entry
         [:td.order-date order-date]
         [:td.ref ref]
         [:td.pickup-address pickup-address]
         [:td.delivery-address delivery-address]
         [:td.goods goods]
         [:td.status status]
         [:td.actions
          [:a.button.button-secondary {:href (str "entry-" ref)} "Openen"]]])]
     [:tfoot
      [:tr
       [:td.actions {:colspan 7} actions]]]]))

(defn edit-entry [{:keys [status ref order-date
                          pickup-date pickup-address pickup-notes
                          delivery-date delivery-address delivery-notes
                          goods transporter]}]
  [:form
   [:section
    (w/field {:name "status", :label "Status", :value status, :disabled true})
    (w/field {:name "ref", :label "Klantorder nr.", :type "number", :value ref})
    (w/field {:name "order-date", :label "Orderdatum", :type "date", :value order-date})]
   [:section
    (w/field {:name "pickup-date", :label "Ophaaldatum", :type "date", :value pickup-date})
    (w/field {:name "pickup-address", :label "Ophaaladres", :type "text", :value pickup-address, :list "locations"})
    (w/field {:name "pickup-notes", :label "Opmerkingen", :type "textarea", :value pickup-notes})]
   [:section
    (w/field {:name "delivery-date", :label "Afleverdatum", :type "date", :value delivery-date})
    (w/field {:name "delivery-address", :label "Afleveradres", :type "text", :value delivery-address, :list "locations"})
    (w/field {:name "delivery-notes", :label "Opmerkingen", :type "textarea", :value delivery-notes})]
   [:section
    (w/field {:name "goods", :label "Goederen", :type "text", :value goods, :list "goods"})
    (w/field {:name "transporter", :label "Transporter", :type "text", :value transporter, :list "transporters"})]
   [:div.actions
    [:button.button.button-primary {:type "submit"} "Bewaren"]
    [:a.button.button-secondary {:href (str "publish-" ref)} "Transportopdracht aanmaken"]
    [:a.button {:href "."} "Annuleren"]]])

(defn publish-entry [{:keys [ref pickup-date pickup-address pickup-notes delivery-date delivery-address delivery-notes goods transporter]}]
  [:form  {:method "POST", :action (str "publish-" ref)}
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
      [:dd delivery-date]]
     [:div
      [:dt "Vervoerder"]
      [:dd transporter]]]]
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
   [:div.actions
    [:button.button-primary {:type    "submit"
                             :onclick "return confirm('Zeker weten?')"}
     "Versturen"]
    [:a.button {:href (str "entry-" ref)} "Annuleren"]]])

(defn published-entry [{:keys [pickup-address transporter]}]
  [:div
   [:section
    [:p "Transportopdracht verstuurd naar locatie " [:q pickup-address] " en vervoerder " [:q transporter] "."]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   [:details.explaination
    [:summary "Uitleg"]
    [:ol
     [:li
      [:h3 "Autoriseer de Vervoerder names de Verlader voor de Klantorder"]
      [:p "API call naar " [:strong "AR van de Verlader"] " om een autorisatie te registeren"]
      [:ul [:li "Klantorder nr."] [:li "Vervoerder ID"]]]
     [:li
      [:h3 "Stuur Transportopdracht naar WMS van DC"]
      [:p "Via EDIFACT / E-mail etc."]]
     [:li
      [:h3 "Stuur Transportopdracht naar TMS van Vervoerder"]
      [:p "Via EDIFACT  / E-mail etc."]]]]])



(def entries
  (let [start 1337]
    (mapv (fn [i]
            (let [pickup-address (w/pick w/locations)]
              {:ref              (str (+ i start 20240000))
               :status           (w/pick w/statuses)
               :order-date       (w/format-date (w/days-from-now (/ i 5)))
               :pickup-date      (w/format-date (w/days-from-now (inc (/ i 5))))
               :pickup-address   pickup-address
               :pickup-notes     "hier komen ophaalnotities"
               :delivery-date    (w/format-date (w/days-from-now (+ 2 (/ i 5))))
               :delivery-address (w/pick (disj w/locations pickup-address))
               :delivery-notes   "hier komen aflevernotities"
               :goods            (w/pick w/goods)
               :transporter      (w/pick w/transporters)}))
          (range 20))))

(defn get-entry [ref]
  (first (filter #(= ref (:ref %)) entries)))

(defn next-entry-ref []
  (->> entries (map :ref) (map #(Integer/parseInt %)) (apply max) inc str))



(defn render-body [title h]
  (w/render-body "erp" (str "ERP — " title ) h))

(defroutes handler
  (GET "/" []
    {:body (render-body "Klantorders"
                        (list-entries entries))})

  (GET "/entry-new" []
    {:body (render-body "Nieuwe klantorder"
                        (edit-entry {:ref    (next-entry-ref)
                                     :status "New"}))})

  (GET "/entry-:ref" [ref]
    (when-let [entry (get-entry ref)]
      {:body (render-body (str "Klantorder: " ref)
                          (edit-entry entry))}))


  (GET "/publish-:ref" [ref]
    (when-let [entry (get-entry ref)]
      {:body (render-body "Transportopdracht aanmaken"
                          (publish-entry entry))}))

  (POST "/publish-:ref" [ref]
        ;; TODO
        (redirect (str "published-" ref) :see-other))

  (GET "/published-:ref" [ref]
    (when-let [entry (get-entry ref)]
      {:body (render-body "Transportopdracht aangemaakt"
                          (published-entry entry))})))
