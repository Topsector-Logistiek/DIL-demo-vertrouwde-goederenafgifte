;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.tms.web
  (:require [clojure.string :as string]
            [compojure.core :refer [routes DELETE GET POST]]
            [dil-demo.master-data :as d]
            [dil-demo.otm :as otm]
            [dil-demo.store :as store]
            [dil-demo.web-utils :as w]
            [ring.util.response :refer [redirect]]))

(defn list-trips [trips {:keys [warehouses]}]
  [:table
   [:thead
    [:tr
     [:th.status "Status"]
     [:th.date "Ophaaldatum"]
     [:th.ref "Klantorder nr."]
     [:th.location "Ophaaladres"]
     [:th.location "Afleveradres"]
     [:th.id-digits "Rijbewijs cijfers"]
     [:th.license-plate "Kenteken"]
     [:th.actions]]]
   [:tbody
    (when-not (seq trips)
      [:tr.empty
       [:td {:colspan 999}
        "Nog geen transportopdrachten geregistreerd.."]])

    (for [{:keys [id ref status load-date load-location unload-location driver-id-digits license-plate]}
          (map otm/trip->map trips)]
      [:tr.trip
       [:td.status (otm/statuses status)]
       [:td.date load-date]
       [:td.ref ref]
       [:td.location (warehouses load-location)]
       [:td.location unload-location]
       [:td.id-digits (w/or-em-dash driver-id-digits)]
       [:td.license-plate (w/or-em-dash license-plate)]
       [:td.actions
        [:ul
         [:li [:a.button.button-secondary {:href (str "assign-" id)} "Openen"]]
         (when-not (= otm/status-outsourced status)
           [:li [:a.button.button-secondary {:href (str "outsource-" id)} "Outsource"]])
         [:li (w/delete-button (str "trip-" id))]]]])]])

(defn qr-code-dil-demo [trip]
  (let [carrier-eori-list (otm/trip-carrier-eori-list trip)
        driver-id-digits (otm/trip-driver-id-digits trip)
        license-plate (otm/trip-license-plate trip)]
    (w/qr-code (str ":dil-demo"
                    ":" (string/join "," carrier-eori-list)
                    ":" driver-id-digits
                    ":" license-plate))))

(defn chauffeur-list-trips [trips {:keys [warehouses]}]
  (if (seq trips)
    [:ul.cards
     (for [{:keys [id ref load-location load-date unload-location]} (map otm/trip->map trips)]
       [:li.card.trip
        [:a.button {:href (str "trip-" id)}
         [:div.date load-date]
         [:div.trip
          [:span.ref ref]
          " / "
          [:span.load-location (warehouses load-location)]
          " → "
          [:span.unload-location unload-location]]]])]
    [:ul.empty
     [:li
      "Nog geen transportopdrachten geregistreerd.."]]))

(defn chauffeur-trip [trip]
  [:div.trip
   (qr-code-dil-demo trip)
   [:div.actions
    [:a.button {:href "../chauffeur/"} "Terug naar overzicht"]]])

(defn trip-details [trip {:keys [warehouses warehouse-addresses]}]
  (let [{:keys [ref load-date load-location load-remarks unload-date unload-location unload-remarks]}
        (otm/trip->map trip)]
    [:section
     [:section.details
      [:dl
       [:div
        [:dt "Klantorder nr."]
        [:dd ref]]
       [:div
        [:dt "Ophaaldatum"]
        [:dd load-date]]
       [:div
        [:dt "Afleverdatum"]
        [:dd unload-date]]]]

     [:section.trip
      [:fieldset.load-location
       [:legend "Ophaaladres"]
       [:h3 (warehouses load-location)]
       (when-let [address (warehouse-addresses load-location)]
         [:pre address])
       (when-not (string/blank? load-remarks)
         [:blockquote.remarks load-remarks])]
      [:fieldset.unload-location
       [:legend "Afleveradres"]
       [:h3 unload-location]
       (when-let [address (get d/locations unload-location)]
         [:pre address])
       (when-not (string/blank? unload-remarks)
         [:blockquote.remarks unload-remarks])]]]))

(defn assign-trip [trip master-data]
  (let [{:keys [carrier-eori driver-id-digits license-plate]}
        (otm/trip->map trip)]
    [:form {:method "POST"}
     (w/anti-forgery-input)

     (when (and carrier-eori driver-id-digits license-plate)
       (qr-code-dil-demo trip))

     (trip-details trip master-data)

     (w/field {:name  "driver-id-digits", :value       driver-id-digits
               :label "Rijbewijs",        :placeholder "Laatste 4 cijfers"
               :type  "text",             :pattern     "\\d{4}", :required true})
     (w/field {:name  "license-plate", :value    license-plate
               :label "Kenteken",
               :type  "text",          :required true})

     [:div.actions
      [:button.button-primary {:type "submit"} "Toewijzen"]
      [:a.button {:href "."} "Annuleren"]]]))

(defn assigned-trip [trip {:keys [ishare-log]}]
  [:div
   [:section
    [:p "Transportopdracht " [:q (otm/trip-ref trip)] " toegewezen."]

    (qr-code-dil-demo trip)

    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   [:details.explanation
    [:summary "Uitleg"]
    [:ol
     [:li
      [:h3 "Autoriseer de Chauffeur names de Vervoerder voor de Klantorder vervoerd met Kenteken"]
      [:p "API call naar " [:strong "AR van de Vervoerder"] " om een autorisatie te registeren"]
      [:ul [:li "Klantorder nr."] [:li "Rijbewijs (laatste 4 cijfers)"] [:li "Kenteken"]]]
     [:li
      [:h3 "OTM Trip"]
      [:pre.json (w/to-json trip)]]
     (w/ishare-log-intercept-to-hiccup ishare-log)]]])

(defn outsource-trip [trip {:keys [carriers] :as master-data}]
  [:form {:method "POST"}
   (w/anti-forgery-input)

   (trip-details trip master-data)

   [:section
    (let [carriers (into {nil nil} carriers)]
      (w/field {:label "Vervoerder"
                :name  "carrier-eori", :required true
                :type  "select",       :list     carriers}))]

   [:div.actions
    [:button.button-primary {:type "submit"} "Uitbesteden"]
    [:a.button {:href "."} "Annuleren"]]])

(defn outsourced-trip [trip {:keys [ishare-log]}]
  (let [{:keys [ref]} (otm/trip->map trip)]
    [:div
     [:section
      [:p "Transportopdracht " [:q ref] " uitbesteed."]

      [:div.actions
       [:a.button {:href "."} "Terug naar overzicht"]]]
     [:details.explanation
      [:summary "Uitleg"]
      [:ol
       (w/ishare-log-intercept-to-hiccup ishare-log)]]]))

(defn deleted-trip [{:keys [ishare-log]}]
  [:div
   [:section
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   [:details.explanation
    [:summary "Uitleg"]
    [:ol
     [:li
      [:h3 "Autorisatie van Chauffeur ingetrokken"]
      [:p "API call naar " [:strong "AR van de Vervoerder"] " om een autorisatie te verwijderen"]
      [:ul [:li "Klantorder nr."]
       [:li "Rijbewijs (laatste 4 cijfers)"]
       [:li "Kenteken"]]]
     (w/ishare-log-intercept-to-hiccup ishare-log)]]])



(defn get-trips [store]
  (->> store :trips vals (sort-by :creation-date) (reverse)))

(defn get-trip [store id]
  (get-in store [:trips id]))



(def ^:dynamic *slug* nil)

(defn make-handler [{:keys [id site-name], own-eori :eori}]
  (let [slug   (name id)
        render (fn render [title main flash & {:keys [slug-postfix]}]
                 (w/render-body (str slug slug-postfix)
                                main
                                :flash flash
                                :title title
                                :site-name site-name))]
    (routes
     (GET "/" {:keys [flash master-data ::store/store]}
       (render "Transportopdrachten"
               (list-trips (get-trips store) master-data)
               flash))

     (GET "/chauffeur/" {:keys [flash master-data ::store/store]}
       (render "Transportopdrachten"
               (chauffeur-list-trips (get-trips store) master-data)
               flash
               :slug-postfix "-chauffeur"))

     (GET "/chauffeur/trip-:id" {:keys        [flash ::store/store]
                                 {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (render (otm/trip-ref trip)
                 (chauffeur-trip trip)
                 flash
                 :slug-postfix "-chauffeur")))

     (DELETE "/trip-:id" {::store/keys [store]
                          {:keys [id]} :params}
       (when (get-trip store id)
         (-> "deleted"
             (redirect :see-other)
             (assoc :flash {:success "Transportopdracht verwijderd"})
             (assoc ::store/commands [[:delete! :trips id]]))))

     (GET "/deleted" {{:keys [ishare-log] :as flash} :flash}
       (render "Transportopdracht verwijderd"
               (deleted-trip {:ishare-log ishare-log})
               flash))

     (GET "/assign-:id" {:keys        [flash master-data]
                         ::store/keys [store]
                         {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (render (str "Toewijzen: " (otm/trip-ref trip))
                 (assign-trip trip master-data)
                 flash)))

     (POST "/assign-:id" {::store/keys            [store]
                          {:keys [id
                                  driver-id-digits
                                  license-plate]} :params}
       (when-let [trip (get-trip store id)]
         (let [trip (-> trip
                        (otm/trip-status! otm/status-assigned)
                        (otm/trip-driver-id-digits! driver-id-digits)
                        (otm/trip-license-plate! license-plate))]
           (-> (str "assigned-" id)
               (redirect :see-other)
               (assoc :flash {:success "Chauffeur en kenteken toegewezen"})
               (assoc ::store/commands [[:put! :trips trip]])))))

     (GET "/assigned-:id" {:keys                [flash]
                           ::store/keys         [store]
                           {:keys [id]}         :params
                           {:keys [ishare-log]} :flash}
       (when-let [trip (get-trip store id)]
         (render (str "Transportopdracht toegewezen")
                 (assigned-trip trip {:ishare-log ishare-log})
                 flash)))

     (GET "/outsource-:id" {:keys        [flash master-data]
                            ::store/keys [store]
                            {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (render (str "Uitbesteden: " (otm/trip-ref trip))
                 (outsource-trip trip (-> master-data
                                          ;; can't outsource to ourselves
                                          (update :carriers dissoc own-eori)))
                 flash)))

     (POST "/outsource-:id" {::store/keys              [store]
                             {:keys [id carrier-eori]} :params}
       (when-let [trip (get-trip store id)]
         (let [trip (otm/trip-add-subcontractor! trip carrier-eori)]
           (-> (str "outsourced-" id)
               (redirect :see-other)
               (assoc :flash {:success "Naar vervoerder gestuurd"})
               (assoc ::store/commands [[:put! :trips (otm/trip-status! trip otm/status-outsourced)]
                                        [:publish! :trips carrier-eori trip]])))))

     (GET "/outsourced-:id" {:keys        [flash]
                             ::store/keys [store]
                             {:keys [id]} :params
                             {:keys [ishare-log]} :flash}
       (when-let [trip (get-trip store id)]
         (render "Transportopdracht uitbesteed"
                 (outsourced-trip trip {:ishare-log ishare-log})
                 flash))))))
