(ns dil-demo.tms.web
  (:require [clojure.data.json :refer [json-str]]
            [clojure.string :as string]
            [compojure.core :refer [defroutes DELETE GET POST]]
            [dil-demo.otm :as otm]
            [dil-demo.web-utils :as w]
            [hiccup2.core :as h]
            [ring.util.response :refer [content-type redirect response]])
  (:import (java.util UUID)))

(defn list-trips [trips]
  [:table
   [:thead
    [:tr
     [:th.date "Ophaaldatum"]
     [:th.ref "Klantorder nr."]
     [:th.location "Ophaaladres"]
     [:th.location "Afleveradres"]
     [:th.id-digits "Chauffeur ID cijfers"]
     [:th.license-plate "Kenteken"]
     [:th.actions]]]
   [:tbody
    (when-not (seq trips)
      [:tr.empty
       [:td {:colspan 999}
        "Nog geen transportopdrachten geregistreerd.."]])

    (for [{:keys [id ref load-date load-location unload-location driver-id-digits license-plate]}
          (map otm/trip->map trips)]
      [:tr.trip
       [:td.date load-date]
       [:td.ref ref]
       [:td.location load-location]
       [:td.location unload-location]
       [:td.id-digits (w/or-em-dash driver-id-digits)]
       [:td.license-plate (w/or-em-dash license-plate)]
       [:td.actions
        [:a.button.button-secondary {:href (str "assign-" id)} "Openen"]
        (w/delete-button (str "trip-" id))]])]])

(defn assign-trip [trip]
  (let [{:keys [ref load-date load-location load-remarks unload-date unload-location unload-remarks driver-id-digits license-plate]}
        (otm/trip->map trip)]
    [:form {:method "POST"}
     (w/anti-forgery-input)

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
        [:dd unload-date]]]
      [:section.trip
       [:fieldset.load-location
        [:legend "Ophaaladres"]
        [:h3 load-location]
        (when-let [address (get w/locations load-location)]
          [:pre address])
        (when-not (string/blank? load-remarks)
          [:blockquote.remarks ])]
       [:fieldset.unload-location
        [:legend "Afleveradres"]
        [:h3 unload-location]
        (when-let [address (get w/locations unload-location)]
          [:pre address])
        (when-not (string/blank? unload-remarks)
          [:blockquote.remarks unload-remarks])]]

      (w/field {:name "driver-id-digits", :value driver-id-digits
                :label "Chauffeur ID", :placeholder "Laatste 4 cijfers"
                :type "text", :pattern "\\d{4}", :required true})
      (w/field {:name "license-plate", :value license-plate
                :label "Kenteken",
                :type "text", :required true})

      [:div.actions
       [:button.button-primary {:type "submit"} "Toewijzen"]
       [:a.button {:href "."} "Annuleren"]]]]))

(defn qr-code [text]
  (let [id (str "qrcode-" (UUID/randomUUID))]
    [:div.qr-code-container
     [:script {:src "/assets/qrcode.js"}] ;; https://davidshimjs.github.io/qrcodejs/

     [:div.qr-code {:id id}]
     [:script (h/raw
               (str "new QRCode(document.getElementById(" (json-str id) "), " (json-str text) ")"))]]))

(defn assigned-trip [trip {:keys [ishare-log]}]
  (let [{:keys [ref driver-id-digits license-plate carrier-eori]} (otm/trip->map trip)]
    [:div
     [:section
      [:p "Transportopdracht " [:q ref] " toegewezen."]

      (qr-code (str ":dil-demo:" carrier-eori ":" driver-id-digits ":" license-plate))

      [:div.actions
       [:a.button {:href "."} "Terug naar overzicht"]]]
     [:details.explanation
      [:summary "Uitleg"]
      [:ol
       [:li
        [:h3 "Autoriseer de Chauffeur names de Vervoerder voor de Klantorder vervoerd met Kenteken"]
        [:p "API call naar " [:strong "AR van de Vervoerder"] " om een autorisatie te registeren"]
        [:ul [:li "Klantorder nr."] [:li "Chauffeur ID (laatste 4 cijfers)"] [:li "Kenteken"]]]
       [:li
        [:h3 "OTM Trip"]
        [:pre.json (w/to-json trip)]]
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
      [:h3 "Trek autorisatie van Chauffeur in."]
      [:p "API call naar " [:strong "AR van de Vervoerder"] " om een autorisatie te verwijderen"]
      [:ul [:li "Klantorder nr."] [:li "Chauffeur ID (laatste 4 cijfers)"] [:li "Kenteken"]]]
     (w/ishare-log-intercept-to-hiccup ishare-log)]]])



(defn get-trips [store]
  (->> store :trips vals (sort-by :creation-date) (reverse)))

(defn get-trip [store id]
  (get-in store [:trips id]))



(defn render [title h flash]
  (-> (w/render-body "tms" (str "TMS — " title) h
                     :flash flash)
      (response)
      (content-type "text/html")))

(defroutes handler
  (GET "/" {:keys [flash store]}
    (render "Transportopdrachten"
            (list-trips (get-trips store))
            flash))

  (DELETE "/trip-:id" {:keys        [store]
                       {:keys [id]} :params}
    (when (get-trip store id)
      (-> "deleted"
          (redirect :see-other)
          (assoc :flash {:success "Transportopdracht verwijderd"})
          (assoc :store-commands [[:delete! :trips id]]))))

  (GET "/deleted" {{:keys [ishare-log] :as flash} :flash}
      (render "Transportopdracht verwijderd"
              (deleted-trip {:ishare-log ishare-log})
              flash))

  (GET "/assign-:id" {:keys                [flash store]
                      {:keys [id]}         :params
                      {:keys [ishare-log]} :flash}
    (when-let [trip (get-trip store id)]
      (render (str "Transportopdracht: " (otm/trip-ref trip))
              (assign-trip trip)
              flash)))

  (POST "/assign-:id" {:keys                                       [store]
                       {:keys [id driver-id-digits license-plate]} :params}
    (when-let [trip (get-trip store id)]
      (-> (str "assigned-" id)
          (redirect :see-other)
          (assoc :flash {:success "Chauffeur en kenteken toegewezen"})
          (assoc :store-commands [[:put! :trips (-> trip
                                                    (otm/trip-driver-id-digits! driver-id-digits)
                                                    (otm/trip-license-plate! license-plate))]]))))

  (GET "/assigned-:id" {:keys                [flash store]
                        {:keys [id]}         :params
                        {:keys [ishare-log]} :flash}
    (when-let [trip (get-trip store id)]
      (render (str "Transportopdracht toegewezen")
              (assigned-trip trip {:ishare-log ishare-log})
              flash))))
