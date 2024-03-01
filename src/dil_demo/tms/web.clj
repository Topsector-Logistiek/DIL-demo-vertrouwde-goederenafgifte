(ns dil-demo.tms.web
  (:require [compojure.core :refer [defroutes GET POST]]
            [dil-demo.web-utils :as w]
            [ring.util.response :refer [redirect]]))

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
        [:a.button.button-secondary {:href (str "assign-" ref)} "Openen"]]])]])

(defn assign-entry [{:keys [ref pickup-date pickup-address pickup-notes delivery-date delivery-address delivery-notes goods]}]
  [:form {:method "POST", :action (str "assign-" ref)}
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
      [:pre "Kerkstraat 1\n1234 AB  Nergenshuizen"] ;; TODO
      [:blockquote.notes pickup-notes]]
     [:fieldset.delivery-address
      [:legend "Afleveradres"]
      [:h3 delivery-address]
      [:pre "Dorpsweg 2\n4321 YZ  Andershuizen"] ;; TODO
      [:blockquote.notes delivery-notes]]]
    [:section.goods
     [:fieldset
      [:legend "Goederen"]
      [:pre goods]]]

    (w/field {:name "license-plate", :label "Kenteken", :type "text", :required true})
    (w/field {:name "chauffeur-id", :label "Chauffeur ID", :type "text", :required true})

    [:div.actions
     [:button.button-primary {:type "submit"} "Toewijzen"]
     [:a.button {:href "."} "Annuleren"]]]])

(defn assigned-entry [{:keys [ref]}]
  [:div
   [:section
    [:p "Transportopdracht " [:q ref] " toegewezen."]
    [:img {:src "../assets/qr-sample.png"}]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
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
              {:ref              (str (+ i start 20240000))
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

(defn get-entry [ref]
  (first (filter #(= ref (:ref %)) entries)))



(defn render-body [title h]
  (w/render-body "tms" (str "TMS — " title) h))

(defroutes handler
  (GET "/" []
       {:body (render-body "Transportopdrachten"
                           (list-entries entries))})

  (GET "/assign-:ref" [ref]
       (when-let [entry (get-entry ref)]
         {:body (render-body (str "Transportopdracht: " ref)
                             (assign-entry entry))}))

  (POST "/assign-:ref" [ref]
        ;; TODO
        (redirect (str "assigned-" ref) :see-other))

  (GET "/assigned-:ref" [ref]
       (when-let [entry (get-entry ref)]
         {:body (render-body (str "Transportopdracht toegewezen")
                             (assigned-entry entry))})))
