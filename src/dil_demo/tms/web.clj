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

(defn list-trips [trips master-data]
  [:main
   (when-not (seq trips)
     [:article.empty
      [:p "Nog geen transportopdrachten geregistreerd.."]])

   (for [{:keys [id ref status load unload driver-id-digits license-plate carriers]} trips]
     [:article
      [:header
       [:div.status (otm/status-titles status)]
       [:div.ref-date ref " / " (:date load)]
       [:div.from-to
        (get (:warehouses master-data)
             (-> load :location))
        " → "
        (:location unload)]]
      (cond
        (and driver-id-digits license-plate)
        [:p.assigned
         "Toegewezen aan "
         [:q.driver-id-digits (w/or-em-dash driver-id-digits)]
         " / "
         [:q.license-plate (w/or-em-dash license-plate)]]

        (and (= otm/status-outsourced status) (-> carriers last :eori))
        [:p.outsourced
         "Uitbesteed aan "
         [:q (get (:carriers master-data)
                  (-> carriers last :eori))]]

        :else
        [:em "Nog niet toegewezen.."])
      [:footer.actions
       (when (= otm/status-requested status)
         [:a.button.primary {:href  (str "assign-" id)
                             :title "Toewijzen aan een chauffeur en wagen"}
          "Toewijzen"])
       (when (= otm/status-requested status)
         [:a.button.secondary {:href  (str "outsource-" id)
                               :title "Uitbesteden aan een andere vervoerder"}
          "Uitbesteden"])
       (w/delete-button (str "trip-" id))]])])

(defn qr-code-dil-demo [{:keys [carriers driver-id-digits license-plate]}]
  (w/qr-code (str ":dil-demo"
                  ":" (->> carriers (map :eori) (string/join ","))
                  ":" driver-id-digits
                  ":" license-plate)))

(defn chauffeur-list-trips [trips {:keys [warehouses]}]
  [:main
   (when-not (seq trips)
     [:article.empty
      [:p "Nog geen transportopdrachten geregistreerd.."]])

   (for [{:keys [id ref load unload]} trips]
     [:article
      [:header
       [:div.ref-date ref " / " (:date load)]]
      [:div.from-to (-> load :location warehouses) " → " (:location unload)]
      [:footer.actions
       [:a.button.primary {:href (str "trip-" id)} "Tonen"]]])])

(defn chauffeur-trip [{:keys [carriers driver-id-digits license-plate] :as trip}]
  [:div.trip
   [:section
    (qr-code-dil-demo trip)]

   [:fieldset
    (w/field {:label    "Vervoerder EORI's"
              :value    (->> carriers (map :eori) (string/join ","))
              :readonly true})
    (w/field {:label    "Rijbewijs"
              :value    driver-id-digits
              :readonly true})
    (w/field {:label    "Kenteken"
              :value    license-plate
              :readonly true})]

   [:div.actions
    [:a.button {:href "../chauffeur/"} "Terug naar overzicht"]]])

(defn trip-details [{:keys [ref load unload]}
                    {:keys [warehouses warehouse-addresses]}]
  [:section
   [:section.details
    [:dl
     [:div
      [:dt "Klantorder nr."]
      [:dd ref]]
     [:div
      [:dt "Ophaaldatum"]
      [:dd (:date load)]]
     [:div
      [:dt "Afleverdatum"]
      [:dd (:date unload)]]]]

   [:section.trip
    [:fieldset.load-location
     [:legend "Ophaaladres"]
     [:h3 (-> load :location warehouses)]
     (when-let [address (-> load :location warehouse-addresses)]
       [:pre address])
     (when-not (string/blank? (:remarks load))
       [:blockquote.remarks (:remarks load)])]
    [:fieldset.unload-location
     [:legend "Afleveradres"]
     [:h3 (:location unload)]
     (when-let [address (-> unload :location d/locations)]
       [:pre address])
     (when-not (string/blank? (:remarks unload))
       [:blockquote.remarks (:remarks unload)])]]])

(defn assign-trip [{:keys [driver-id-digits license-plate] :as trip}
                   master-data]
  [:form {:method "POST"}
   (w/anti-forgery-input)

   (when (and driver-id-digits license-plate)
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
    [:a.button {:href "."} "Annuleren"]]])

(defn assigned-trip [{:keys [ref] :as trip} {:keys [explanation]}]
  [:div
   [:section
    [:p "Opdracht " [:q ref] " toegewezen."]

    (qr-code-dil-demo trip)

    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   (w/explanation explanation)])

(defn outsource-trip [trip {:keys [carriers] :as master-data}]
  [:form {:method "POST"}
   (w/anti-forgery-input)

   (trip-details trip master-data)

   [:section
    (let [;; add empty option
          carriers (into {nil nil} carriers)]
      (w/field {:name  "carrier[eori]",
                :label "Vervoerder", :type     "select",
                :list  carriers,     :required true}))]

   [:div.actions
    [:button.button-primary {:type "submit"} "Uitbesteden"]
    [:a.button {:href "."} "Annuleren"]]])

(defn outsourced-trip [{:keys [ref carriers]}
                       {:keys [explanation]}
                       master-data]
  [:div
   [:section
    [:p
     "Opdracht " [:q ref]
     " uitbesteed aan " [:q (get (:carriers master-data)
                                 (-> carriers last :eori))]]

    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   (w/explanation explanation)])

(defn deleted-trip [{:keys [explanation]}]
  [:div
   [:section
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   (w/explanation explanation)])



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
       (render "Opdrachten"
               (list-trips (get-trips store) master-data)
               flash))

     (GET "/chauffeur/" {:keys [flash master-data ::store/store]}
       (render "Opdrachten"
               (chauffeur-list-trips (filter #(= otm/status-assigned (:status %))
                                             (get-trips store))
                                     master-data)
               flash
               :slug-postfix "-chauffeur"))

     (GET "/chauffeur/trip-:id" {:keys        [flash ::store/store]
                                 {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (when (= otm/status-assigned (:status trip))
           (render (:ref trip)
                   (chauffeur-trip trip)
                   flash
                   :slug-postfix "-chauffeur"))))

     (DELETE "/trip-:id" {::store/keys [store]
                          {:keys [id]} :params}
       (when (get-trip store id)
         (-> "deleted"
             (redirect :see-other)
             (assoc :flash {:success "Opdracht verwijderd"})
             (assoc ::store/commands [[:delete! :trips id]]))))

     (GET "/deleted" {:keys [flash]}
       (render "Opdracht verwijderd"
               (deleted-trip flash)
               flash))

     (GET "/assign-:id" {:keys        [flash master-data]
                         ::store/keys [store]
                         {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (render (str "Opdracht " (:ref trip) " toewijzen")
                 (assign-trip trip master-data)
                 flash)))

     (POST "/assign-:id" {::store/keys            [store]
                          {:keys [id
                                  driver-id-digits
                                  license-plate]} :params}
       (when-let [trip (get-trip store id)]
         (let [trip (-> trip
                        (assoc :status otm/status-assigned
                               :driver-id-digits driver-id-digits
                               :license-plate license-plate))]
           (-> (str "assigned-" id)
               (redirect :see-other)
               (assoc :flash {:success (str "Chauffeur en kenteken toegewezen aan opdracht " (:ref trip))})
               (assoc ::store/commands [[:put! :trips trip]])))))

     (GET "/assigned-:id" {:keys        [flash]
                           ::store/keys [store]
                           {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (render (str "Opdracht " (:ref trip) " toegewezen")
                 (assigned-trip trip flash)
                 flash)))

     (GET "/outsource-:id" {:keys        [flash master-data]
                            ::store/keys [store]
                            {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (render (str "Opdracht " (:ref trip) " uitbesteden aan een andere vervoerder")
                 (outsource-trip trip (-> master-data
                                          ;; can't outsource to ourselves
                                          (update :carriers dissoc own-eori)))
                 flash)))

     (POST "/outsource-:id" {::store/keys         [store]
                             {:keys [id carrier]} :params}
       (when-let [trip (get-trip store id)]
         (let [trip (update trip :carriers conj carrier)]
           (-> (str "outsourced-" id)
               (redirect :see-other)
               (assoc :flash {:success     (str "Opdracht " (:ref trip) " naar vervoerder gestuurd")
                              :explanation [["Stuur OTM Trip naar TMS van andere vervoerder"
                                             {:otm-object (otm/->trip trip)}]]})
               (assoc ::store/commands [[:put! :trips (assoc trip :status otm/status-outsourced)]
                                        [:publish! :trips (:eori carrier) trip]])))))

     (GET "/outsourced-:id" {:keys        [flash master-data ::store/store]
                             {:keys [id]} :params}
       (when-let [trip (get-trip store id)]
         (render (str "Opdracht " (:ref trip) " uitbesteed")
                 (outsourced-trip trip flash master-data)
                 flash))))))
