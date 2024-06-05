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
            [dil-demo.otm :as otm]
            [dil-demo.store :as store]
            [dil-demo.web-utils :as w]
            [dil-demo.wms.verify :as verify]
            [ring.util.response :refer [redirect]])
  (:import (java.util UUID)))

(defn list-transport-orders [transport-orders]
  (if (seq transport-orders)
    [:ul.cards
     (for [{:keys [id ref load-date goods]}
           (map otm/transport-order->map transport-orders)]
       [:li.card.transport-order
        [:a.button {:href (str "verify-" id)}
         [:div.date load-date]
         [:div.ref ref]
         [:div.goods goods]]
        (w/delete-button (str "transport-order-" id))])]
    [:ul.empty
     [:li
      "Nog geen transportopdrachten geregistreerd.."]]))

(defn show-transport-order [transport-order]
  (let [{:keys [id ref load-date load-remarks]}
        (otm/transport-order->map transport-order)]
    [:section.details
     [:dl
      [:div
       [:dt "Klantorder nr."]
       [:dd ref]]
      [:div
       [:dt "Ophaaldatum"]
       [:dd load-date]]
      (when-not (string/blank? load-remarks)
        [:div
         [:dt "Opmerkingen"]
         [:dd [:blockquote.remarks load-remarks]]])]
     [:div.actions
      [:a.button.button-primary {:href (str "verify-" id)} "Veriferen"]
      [:a.button {:href "."} "Annuleren"]]]))

(defn qr-code-scan-button [carrier-id driver-id plate-id]
  (let [id (str "qr-code-video-" (UUID/randomUUID))]
    [:div.qr-code-scan-container
     [:video {:id id, :style "display:none"}]

     [:script {:src "/assets/qr-scanner.legacy.min.js"}] ;; https://github.com/nimiq/qr-scanner
     [:script {:src "/assets/scan-qr.js"}]
     [:a.button
      {:onclick (str "scanDriverQr(this, " (json-str id) ", " (json-str carrier-id) ", " (json-str driver-id) ", " (json-str plate-id) ", " ")")}
      "Scan QR"]]))

(defn verify-transport-order [transport-order]
  (let [{:keys [id ref load-date load-remarks goods]}
        (otm/transport-order->map transport-order)]
    [:form {:method "POST", :action (str "verify-" id)}
     (w/anti-forgery-input)

     (w/field {:label "Opdracht nr.", :value ref, :disabled true})
     (w/field {:label "Datum", :value load-date, :disabled true})
     (w/field {:label "Goederen", :value goods, :disabled true})
     (when-not (string/blank? load-remarks)
       (w/field {:label "Opmerkingen", :value load-remarks, :type "textarea", :disabled true}))


     [:div.actions
      (qr-code-scan-button "carrier-eori" "driver-id-digits" "license-plate")]

     (w/field {:id       "carrier-eori"
               :name     "carrier-eori", :label "Vervoerder EORI"
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
      [:a.button {:href "."} "Annuleren"]]]))

(defn accepted-transport-order [transport-order
                                {:keys [carrier-eori driver-id-digits license-plate]}
                                {:keys [ishare-log]}]
  [:div
   [:section
    [:h3.verification.verification-accepted "Afgifte akkoord"]
    [:p
     "Transportopdracht "
     [:q (otm/transport-order-ref transport-order)]
     " goedgekeurd voor vervoerder met EORI "
     [:q carrier-eori]
     ", chauffeur met rijbewijs eindigend op "
     [:q driver-id-digits]
     " en kenteken "
     [:q license-plate]
     "."]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   [:details.explanation
    [:summary "Uitleg"]
    [:ol (w/ishare-log-intercept-to-hiccup ishare-log)]]])

(defn rejected-transport-order [transport-order
                                {:keys [carrier-eori driver-id-digits license-plate]}
                                {:keys [ishare-log
                                        owner-rejections
                                        carrier-rejections]}]
  [:div
   [:section
    [:h3.verification.verification-rejected "Afgifte NIET akkoord"]
    (when owner-rejections
      [:div.owner-rejections
       [:p
        "Transportopdracht "
        [:q (otm/transport-order-ref transport-order)]
        " is AFGEKEURD voor vervoerder met EORI "
        [:q carrier-eori]
        "."]
       [:ul.rejections
        (for [rejection owner-rejections]
          [:li rejection])]])
    (when carrier-rejections
      [:div.carrier-rejections
       [:p
        "Transportopdracht "
        [:q (otm/transport-order-ref transport-order)]
        " is AFGEKEURD chauffeur met rijbewijs eindigend op "
        [:q driver-id-digits]
        " en kenteken "
        [:q license-plate]
        "."]
       [:ul.rejections
        (for [rejection carrier-rejections]
          [:li rejection])]
       ])

    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   [:details.explanation
    [:summary "Uitleg"]
    [:ol (w/ishare-log-intercept-to-hiccup ishare-log)]]])



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
         (render "Verificatie"
                 (verify-transport-order transport-order)
                 flash)))

     (POST "/verify-:id" {:keys                   [client-data flash]
                          ::store/keys            [store]
                          {:keys [id] :as params} :params}
       (when-let [transport-order (get-transport-order store id)]
         (let [params (merge (otm/transport-order->map transport-order)
                             (select-keys params [:carrier-eori :driver-id-digits :license-plate]))
               result (verify/verify! client-data transport-order params)]
           (if (verify/permitted? result)
             (render "Transportopdracht geaccepteerd"
                     (accepted-transport-order transport-order params result)
                     flash)
             (render "Transportopdracht afgewezen"
                     (rejected-transport-order transport-order params result)
                     flash))))))))
