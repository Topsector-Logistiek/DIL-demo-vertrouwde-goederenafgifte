;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.erp.web
  (:require [clojure.string :as string]
            [compojure.core :refer [routes DELETE GET POST]]
            [dil-demo.master-data :as d]
            [dil-demo.otm :as otm]
            [dil-demo.store :as store]
            [dil-demo.web-utils :as w]
            [ring.util.response :refer [redirect]])
  (:import (java.time LocalDateTime)
           (java.util Date UUID)))

(defn list-consignments [consignments {:keys [carriers warehouses]}]
  [:main
   [:section.actions
    [:a.button.primary {:href "consignment-new"} "Nieuwe order aanmaken"]]

   (when-not (seq consignments)
     [:article.empty
      [:p "Nog geen klantorders geregistreerd.."]])

   (for [{:keys [id ref load-date load-location unload-location goods carrier-eori status]}
         (map otm/consignment->map consignments)]
     [:article
      [:header
       [:div.status (otm/status-titles status)]
       [:div.ref-date ref " / " load-date]
       [:div.from-to (warehouses load-location) " → " unload-location]]

      [:div.goods goods]
      [:div.carrier (carriers carrier-eori)]

      [:footer.actions
       (when (= otm/status-draft status)
         [:a.button.primary {:href  (str "consignment-" id)
                             :title "Eigenschappen aanpassen"}
          "Aanpassen"])
       (when (= otm/status-draft status)
         [:a.button.secondary {:href  (str "publish-" id)
                               :title "Opdracht versturen naar locatie en vervoerder"}
          "Versturen"])
       (w/delete-button (str "consignment-" id))]])])

(defn edit-consignment [consignment {:keys [carriers warehouses]}]
  (let [{:keys [status ref load-date load-location load-remarks unload-location unload-date unload-remarks goods carrier-eori]}
        (otm/consignment->map consignment)

        ;; add empty option
        carriers (into {nil nil} carriers)]
    [:form {:method "POST"}
     (w/anti-forgery-input)

     [:section
      (w/field {:name  "status", :value status,
                :label "Status", :type  "select",
                :list  otm/status-titles})
      (w/field {:name     "ref",            :value ref,
                :label    "Klantorder nr.", :type  "number",
                :required true})]
     [:section
      (w/field {:name  "load-date",   :type  "date",
                :label "Ophaaldatum", :value load-date})
      (w/field {:name  "load-location", :value    load-location, ;; EORIs?!
                :label "Ophaaladres",   :type     "select",
                :list  warehouses,      :required true})
      (w/field {:name  "load-remarks", :value load-remarks,
                :label "Opmerkingen",  :type  "textarea"})]
     [:section
      (w/field {:name  "unload-date",  :value unload-date,
                :label "Afleverdatum", :type  "date"})
      (w/field {:name  "unload-location",  :value    unload-location,
                :label "Afleveradres",     :type     "text",
                :list  (keys d/locations), :required true})
      (w/field {:name  "unload-remarks", :value unload-remarks,
                :label "Opmerkingen",    :type  "textarea"})]
     [:section
      (w/field {:name  "goods",    :value    goods,
                :label "Goederen", :type     "text",
                :list  d/goods,    :required true})
      (w/field {:name  "carrier-eori", :value    carrier-eori,
                :label "Vervoerder",   :type     "select",
                :list  carriers,       :required true})]
     [:section.actions
      [:button {:type "submit"} "Opslaan"]
      [:a.button {:href "."} "Annuleren"]]]))

(defn deleted-consignment [{:keys [explanation]}]
  [:div
   [:section
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   (w/explanation explanation)])

(defn publish-consignment [consignment {:keys [carriers warehouses warehouse-addresses]}]
  (let [{:keys [status ref load-date load-location load-remarks unload-date unload-location unload-remarks goods carrier-eori]}
        (otm/consignment->map consignment)]
    [:form {:method "POST"}
     (w/anti-forgery-input)

     (when (not= otm/status-draft status)
       [:div.flash.flash-warning
        "Let op!  Deze opdracht is al verzonden!"])

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
        [:dd unload-date]]
       [:div
        [:dt "Vervoerder"]
        [:dd (carriers carrier-eori)]]]]
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
       (when-let [address (d/locations unload-location)]
         [:pre address])
       (when-not (string/blank? unload-remarks)
         [:blockquote.remarks unload-remarks])]]
     [:section.goods
      [:fieldset
       [:legend "Goederen"]
       [:pre goods]]]
     [:div.actions
      [:button {:type    "submit"
                :onclick "return confirm('Zeker weten?')"}
       "Versturen"]
      [:a.button {:href "."} "Annuleren"]]]))

(defn published-consignment [consignment
                             {:keys [carriers warehouses]}
                             {:keys [explanation]}]
  (let [{:keys [load-location carrier-eori]} (otm/consignment->map consignment)]
    [:div
     [:section
      [:p "Transportopdracht verstuurd naar locatie "
       [:q (warehouses load-location)]
       " en vervoerder "
       [:q (carriers carrier-eori)]
       "."]
      [:div.actions
       [:a.button {:href "."} "Terug naar overzicht"]]]
     (w/explanation (into [["Stuur OTM Transportopdracht naar WMS van DC"]
                           ["Stuur OTM Trip naar TMS van Vervoerder"]]
                          explanation))]))



(defn get-consignments [store]
  (->> store :consignments vals (sort-by :creation-date) (reverse)))

(defn get-consignment [store id]
  (get-in store [:consignments id]))

(defn min-ref [user-number]
  (let [dt (LocalDateTime/now)]
    (+ (* user-number
          100000000)
       (* (- (.getYear dt) 2000)
          1000000)
       (* (.getDayOfYear dt)
          1000))))

(defn next-consignment-ref [store user-number]
  (let [refs (->> store
                  (get-consignments)
                  (map otm/consignment-ref)
                  (map parse-long))]
    (str (inc (apply max (min-ref user-number) refs)))))



(defn make-handler [{:keys [eori id site-name]}]
  {:pre [(keyword? id) site-name]}
  (let [slug                (name id)
        render              (fn render [title main flash & {:keys [slug-postfix]}]
                              (w/render-body (str slug slug-postfix)
                                             main
                                             :flash flash
                                             :title title
                                             :site-name site-name))
        params->consignment (fn params->consignment [params]
                              (-> params
                                  (assoc :owner-eori eori)
                                  (otm/map->consignment)))]
    (routes
     (GET "/" {:keys [flash master-data ::store/store]}
       (render "Orders"
               (list-consignments (get-consignments store) master-data)
               flash))

     (GET "/consignment-new" {:keys [flash master-data ::store/store user-number]}
       (render "Order aanmaken"
               (edit-consignment
                (otm/map->consignment {:ref         (next-consignment-ref store user-number)
                                       :load-date   (w/format-date (Date.))
                                       :unload-date (w/format-date (Date.))
                                       :status      otm/status-draft})
                master-data)
               flash))

     (POST "/consignment-new" {:keys [params]}
       (let [consignment (-> params
                             (assoc :id (str (UUID/randomUUID))
                                    :status otm/status-draft)
                             (params->consignment))]
         (-> "."
             (redirect :see-other)
             (assoc :flash {:success (str "Order " (otm/consignment-ref consignment) " aangemaakt")})
             (assoc ::store/commands [[:put! :consignments consignment]]))))

     (GET "/consignment-:id" {:keys        [flash master-data ::store/store]
                              {:keys [id]} :params}
       (when-let [consignment (get-consignment store id)]
         (render (str "Order " (otm/consignment-ref consignment) " aanpassen")
                 (edit-consignment consignment master-data)
                 flash)))

     (POST "/consignment-:id" {:keys [params]}
       (let [consignment (params->consignment params)]
         (-> "."
             (redirect :see-other)
             (assoc :flash {:success (str "Order " (otm/consignment-ref consignment) " aangepast")})
             (assoc ::store/commands [[:put! :consignments consignment]]))))

     (DELETE "/consignment-:id" {:keys        [::store/store]
                                 {:keys [id]} :params}
       (when-let [consignment (get-consignment store id)]
         (-> "deleted"
             (redirect :see-other)
             (assoc :flash {:success (str "Order " (otm/consignment-ref consignment) " verwijderd")})
             (assoc ::store/commands [[:delete! :consignments id]]))))

     (GET "/deleted" {:keys [flash]}
       (render "Order verwijderd"
               (deleted-consignment flash)
               flash))

     (GET "/publish-:id" {:keys        [flash master-data ::store/store]
                          {:keys [id]} :params}
       (when-let [consignment (get-consignment store id)]
         (render (str "Order " (otm/consignment-ref consignment) " naar locatie en vervoerder sturen")
                 (publish-consignment consignment master-data)
                 flash)))

     (POST "/publish-:id" {:keys              [::store/store]
                           {:keys [id]}       :params}
       (when-let [consignment (get-consignment store id)]
         (let [consignment (otm/consignment-status! consignment otm/status-requested)]
           (-> (str "published-" id)
               (redirect :see-other)
               (assoc :flash {:success (str "Order " (otm/consignment-ref consignment) " verstuurd")})
               (assoc ::store/commands [[:put! :consignments consignment]
                                        [:publish! ;; to warehouse WMS
                                         :transport-orders
                                         (otm/consignment-warehouse-eori consignment)
                                         (otm/consignment->transport-order consignment)]
                                        [:publish! ;; to carrier TMS
                                         :trips
                                         (otm/consignment-carrier-eori consignment)
                                         (otm/consignment->trip consignment)]])))))

     (GET "/published-:id" {:keys        [flash master-data ::store/store]
                            {:keys [id]} :params}
       (when-let [consignment (get-consignment store id)]
         (render (str "Order "
                      (otm/consignment-ref consignment)
                      " verstuurd")
                 (published-consignment consignment master-data flash)
                 flash))))))
