(ns dil-demo.wms.web
  (:require [compojure.core :refer [defroutes DELETE GET POST]]
            [dil-demo.otm :as otm]
            [dil-demo.web-utils :as w]
            [clojure.data.json :refer [json-str]]
            [ring.util.response :refer [content-type redirect response]])
  (:import [java.util UUID]))

(defn list-transport-orders [transport-orders]
  [:table
   [:thead
    [:tr
     [:th.date "Ophaaldatum"]
     [:th.ref "Opdracht nr."]
     [:th.goods "Goederen"]
     [:th.actions]]]
   [:tbody
    (when-not (seq transport-orders)
      [:tr.empty
       [:td {:colspan 999}
        "Nog geen transportopdrachten geregistreerd.."]])

    (for [{:keys [id ref load-date goods]}
          (map otm/transport-order->map transport-orders)]
      [:tr.transport-order
       [:td.date load-date]
       [:td.ref ref]
       [:td.goods goods]
       [:td.actions
        [:a.button.button-secondary {:href (str "transport-order-" id)} "Openen"]
        (w/delete-button (str "transport-order-" id))]])]])

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
      [:div
       [:dt "Opmerkingen"]
       [:dd [:blockquote.remarks load-remarks]]]]
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
  (let [{:keys [id ref load-remarks]}
        (otm/transport-order->map transport-order)]
    [:form {:method "POST", :action (str "verify-" id)}
     (w/anti-forgery-input)

     (w/field {:label "Opdracht nr.", :value ref, :disabled true})
     (w/field {:label "Opmerkingen", :value load-remarks, :type "textarea", :disabled true})


     [:div.actions
      (qr-code-scan-button "carrier-eori" "driver-id-digits" "license-plate")]

     (w/field {:id       "carrier-eori"
               :name     "carrier-eori", :label "Vervoerder EORI"
               :required true})
     (w/field {:id          "driver-id-digits"
               :name        "driver-id-digits",  :label   "Chauffeur ID",
               :placeholder "Laatste 4 cijfers", :pattern "\\d{4}",
               :required    true})
     (w/field {:id   "license-plate"
               :name "license-plate", :label "Kenteken",
               :required true})

     [:div.actions
      [:button.button-primary {:type "submit"} "Veriferen"]
      [:a.button {:href "."} "Annuleren"]]]))

(defn accepted-transport-order [transport-order
                                {:keys [carrier-eori driver-id-digits license-plate]}]
  [:div
   [:section
    [:h2.verification.verification-accepted "Afgifte akkoord"]
    [:p
     "Transportopdracht "
     [:q (otm/transport-order-ref transport-order)]
     " goedgekeurd voor vervoerder met EORI "
     [:q carrier-eori]
     ", chauffeur met ID eindigend op "
     [:q driver-id-digits]
     " en kenteken "
     [:q license-plate]
     "."]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   [:details.explaination
    [:summary "Uitleg"]
    [:ol
     [:li
      [:h3 "Check Authorisatie Vervoerder names de Verlader"]
      [:p "API call naar " [:strong "AR van de Verlader"] " om te controleren of Vervoerder names Verlader de transportopdracht uit mag voeren."]
      [:ul [:li "Klantorder nr."] [:li "Vervoerder EORI"]]]
     [:li
      [:h3 "Check Authorisatie Chauffeur en Kenteken names de Vervoerder"]
      [:p "API call naar " [:strong "AR van de Vervoerder"] " om te controleren of de Chauffeur met Kenteken de transportopdracht"]
      [:ul [:li "Klantorder nr."] [:li "Chauffeur ID (laatste 4 cijfers)"] [:li "Kenteken"]]]]]])

(defn rejected-transport-order [transport-order
                                {:keys [carrier-eori driver-id-digits license-plate]}]
  [:div
   [:section
    [:h2.verification.verification-rejected "Afgifte NIET akkoord"]
    [:p
     "Transportopdracht "
     [:q (otm/transport-order-ref transport-order)]
     " is AFGEKEURD voor vervoerder met EORI "
     [:q carrier-eori]
     ", chauffeur met ID eindigend op "
     [:q driver-id-digits]
     " en kenteken "
     [:q license-plate]
     "."]
    [:div.actions
     [:a.button {:href "."} "Terug naar overzicht"]]]
   [:details.explaination
    [:summary "Uitleg"]
    [:ol
     [:li
      [:h3 "Check Authorisatie Vervoerder names de Verlader"]
      [:p "API call naar " [:strong "AR van de Verlader"] " om te controleren of Vervoerder names Verlader de transportopdracht uit mag voeren."]
      [:ul [:li "Klantorder nr."] [:li "Vervoerder EORI"]]]
     [:li
      [:h3 "Check Authorisatie Chauffeur en Kenteken names de Vervoerder"]
      [:p "API call naar " [:strong "AR van de Vervoerder"] " om te controleren of de Chauffeur met Kenteken de transportopdracht"]
      [:ul [:li "Klantorder nr."] [:li "Chauffeur ID (laatste 4 cijfers)"] [:li "Kenteken"]]]]]])



(defn get-transport-orders [store]
  (->> store :transport-orders vals (sort-by :ref) (reverse)))

(defn get-transport-order [store id]
  (get-in store [:transport-orders id]))



(defn verify [transport-order {:keys [carrier-eori driver-id-digits license-plate]}]
  (= 0 (int (* 2 (rand))))) ;; TODO



(defn render [title h flash]
  (-> (w/render-body "wms" (str "WMS — " title ) h
                     :flash flash)
      (response)
      (content-type "text/html")))

(defroutes handler
  (GET "/" {:keys [flash store]}
    (render "Transportopdrachten"
            (list-transport-orders (get-transport-orders store))
            flash))

  (GET "/transport-order-:id" {:keys [flash store]
                               {:keys [id]} :params}
    (when-let [transport-order (get-transport-order store id)]
      (render (str "Transportopdracht: "
                   (otm/transport-order-ref transport-order))
              (show-transport-order transport-order)
              flash)))

  (DELETE "/transport-order-:id" {:keys [store]
                                  {:keys [id]} :params}
    (when (get-transport-order store id)
      (-> "."
          (redirect :see-other)
          (assoc :flash {:success "Transportopdracht verwijderd"})
          (assoc :store-commands [[:delete! :transport-orders id]]))))

  (GET "/verify-:id" {:keys [flash store]
                      {:keys [id]} :params}
    (when-let [transport-order (get-transport-order store id)]
      (render (str "verificatie Transportopdracht: "
                   (otm/transport-order-ref transport-order))
              (verify-transport-order transport-order)
              flash)))

  (POST "/verify-:id" {:keys [flash store]
                       {:keys [id] :as params} :params}
    (when-let [transport-order (get-transport-order store id)]
      (if (verify transport-order params)
        (render (str "Transportopdracht ("
                     (otm/transport-order-ref transport-order)
                     ") geaccepteerd")
                (accepted-transport-order transport-order params)
                flash)
        (render (str "Transportopdracht ("
                     (otm/transport-order-ref transport-order)
                     ") afgewezen")
                (rejected-transport-order transport-order params)
                flash)))))
