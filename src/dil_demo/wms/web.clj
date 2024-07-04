;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.wms.web
  (:require [clojure.data.json :refer [json-str]]
            [clojure.string :as string]
            [compojure.core :refer [routes DELETE GET POST]]
            [dil-demo.store :as store]
            [dil-demo.web-utils :as w]
            [dil-demo.wms.verify :as verify]
            [ring.util.response :refer [redirect]])
  (:import (java.util UUID)))

(defn list-transport-orders [transport-orders]
  [:main
   (when-not (seq transport-orders)
     [:article.empty
      [:p "Nog geen transportopdrachten geregistreerd.."]])

   (for [{:keys [id ref load goods]} transport-orders]
     [:article
      [:header
       [:div.ref-date ref " / " (:date load)]]
      [:div.goods goods]

      [:footer.actions
       [:a.button.primary {:href (str "verify-" id)}
        "Veriferen"]
       (w/delete-button (str "transport-order-" id))]])])

(defn qr-code-scan-button [carrier-id driver-id plate-id]
  (let [id (str "qr-code-video-" (UUID/randomUUID))]
    [:div.qr-code-scan-container
     [:video {:id id, :style "display:none"}]

     [:script {:src "/assets/qr-scanner.legacy.min.js"}] ;; https://github.com/nimiq/qr-scanner
     [:script {:src "/assets/scan-qr.js"}]
     [:a.button.secondary {:onclick (str "scanDriverQr(this, "
                                         (json-str id) ", "
                                         (json-str carrier-id) ", "
                                         (json-str driver-id) ", "
                                         (json-str plate-id) ")")}
      "Scan QR"]]))

(defn verify-transport-order [{:keys [id ref load goods]}]
  [:form {:method "POST", :action (str "verify-" id)}
   (w/anti-forgery-input)

   (w/field {:label "Opdracht nr.", :value ref, :disabled true})
   (w/field {:label "Datum", :value (:date load), :disabled true})
   (w/field {:label "Goederen", :value goods, :disabled true})

   (when-not (string/blank? (:remarks load))
     (w/field {:label "Opmerkingen", :value (:remarks load), :type "textarea", :disabled true}))

   [:div.actions
    (qr-code-scan-button "carrier-eoris" "driver-id-digits" "license-plate")]

   (w/field {:id       "carrier-eoris"
             :name     "carrier-eoris", :label "Vervoerder EORI's"
             :required true})
   (w/field {:id          "driver-id-digits"
             :name        "driver-id-digits",  :label   "Rijbewijs",
             :placeholder "Laatste 4 cijfers", :pattern "\\d{4}",
             :required    true})
   (w/field {:id   "license-plate"
             :name "license-plate", :label "Kenteken",
             :required true})

   [:div.actions
    [:button.button-primary
     {:type "submit"
      :onclick "return confirm('Kloppen de rijbewijs cijfers en het kenteken?')"}
     "Veriferen"]
    [:a.button {:href "."} "Annuleren"]]])

(defn accepted-transport-order [transport-order
                                {:keys [carrier-eoris driver-id-digits license-plate]}
                                {:keys [explanation]}
                                {:keys [eori->name]}]
  [:div
   [:section
    [:h3.verification.verification-accepted "Afgifte akkoord"]
    [:p
     "Afgifte transportopdracht "
     [:q (:ref transport-order)]
     " goedgekeurd voor vervoerder "
     [:q (-> carrier-eoris last eori->name)]
     ", chauffeur met rijbewijs eindigend op "
     [:q driver-id-digits]
     " en kenteken "
     [:q license-plate]
     "."]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   (w/explanation explanation)])

(defn rejected-transport-order [transport-order
                                {:keys [carrier-eoris driver-id-digits license-plate]}
                                {:keys [explanation] :as result}
                                {:keys [eori->name]}]
  [:div
   [:section
    [:h3.verification.verification-rejected "Afgifte " [:strong "NIET"] " akkoord"]
    [:p
     "Afgifte transportopdracht "
     [:q (:ref transport-order)]
     " " [:strong "NIET"] " goedgekeurd voor vervoerder "
     [:q (eori->name (last carrier-eoris))]
     ", chauffeur met rijbewijs eindigend op "
     [:q driver-id-digits]
     " en kenteken "
     [:q license-plate]
     "."]

    [:p
     "Afgewezen na inspectie van het Authorisatie Register van "
     [:q (eori->name (verify/rejection-eori result))]
     " met de volgende bevindingen:"]

    [:ul.rejections
     (for [rejection (verify/rejection-reasons result)]
       [:li rejection])]

    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   (w/explanation explanation)])



(defn get-transport-orders [store]
  (->> store :transport-orders vals (sort-by :creation-date) (reverse)))

(defn get-transport-order [store id]
  (get-in store [:transport-orders id]))



(defn make-handler [{:keys [id site-name]}]
  (let [slug   (name id)
        render (fn render [title main flash & {:keys [slug-postfix]}]
                 (w/render-body (str slug slug-postfix)
                                main
                                :flash flash
                                :title title
                                :site-name site-name))]
    (routes
     (GET "/" {:keys        [flash]
               ::store/keys [store]}
       (render "Transportopdrachten"
               (list-transport-orders (get-transport-orders store))
               flash))

     (DELETE "/transport-order-:id" {::store/keys [store]
                                     {:keys [id]} :params}
       (when (get-transport-order store id)
         (-> "."
             (redirect :see-other)
             (assoc :flash {:success "Transportopdracht verwijderd"})
             (assoc ::store/commands [[:delete! :transport-orders id]]))))

     (GET "/verify-:id" {:keys        [flash]
                         ::store/keys [store]
                         {:keys [id]} :params}
       (when-let [transport-order (get-transport-order store id)]
         (render "Verificatie afgifte"
                 (verify-transport-order transport-order)
                 flash)))

     (POST "/verify-:id" {:keys                   [client-data flash master-data ::store/store]
                          {:keys [id] :as params} :params}
       (when-let [transport-order (get-transport-order store id)]
         (let [params (update params :carrier-eoris string/split #",")
               result (verify/verify! client-data transport-order params)]
           (if (verify/permitted? result)
             (render "Afgifte goedgekeurd"
                     (accepted-transport-order transport-order params result master-data)
                     flash)
             (render "Afgifte afgewezen"
                     (rejected-transport-order transport-order params result master-data)
                     flash))))))))
