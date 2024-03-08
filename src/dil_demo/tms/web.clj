(ns dil-demo.tms.web
  (:require [compojure.core :refer [defroutes DELETE GET POST]]
            [dil-demo.otm :as otm]
            [dil-demo.web-utils :as w]
            [ring.util.response :refer [content-type redirect response]]))

(defn list-trips [trips]
  [:table
   [:thead
    [:tr
     [:th.date "Ophaaldatum"]
     [:th.ref "Klantorder nr."]
     [:th.location "Ophaaladres"]
     [:th.location "Afleveradres"]
     [:th.driver "Chauffeur"]
     [:th.license-plate "Kenteken"]
     [:th.actions]]]
   [:tbody
    (when-not (seq trips)
      [:tr.empty
       [:td {:colspan 999}
        "Nog geen transportopdrachten geregistreerd.."]])

    (for [{:keys [id ref load-date load-location unload-location driver license-plate]}
          (map otm/trip->map trips)]
      [:tr.trip
       [:td.date load-date]
       [:td.ref ref]
       [:td.location load-location]
       [:td.location unload-location]
       [:td.driver driver]
       [:td.license-plate license-plate]
       [:td.actions
        [:a.button.button-secondary {:href (str "assign-" id)} "Openen"]
        (w/delete-button (str "trip-" id))]])]])

(defn assign-trip [trip]
  (let [{:keys [id ref load-date load-location load-remarks unload-date unload-location unload-remarks driver license-plate]}
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
        [:pre "Kerkstraat 1\n1234 AB  Nergenshuizen"] ;; TODO
        [:blockquote.remarks load-remarks]]
       [:fieldset.unload-location
        [:legend "Afleveradres"]
        [:h3 unload-location]
        [:pre "Dorpsweg 2\n4321 YZ  Andershuizen"] ;; TODO
        [:blockquote.remarks unload-remarks]]]

      (w/field {:name "driver", :value driver
                :label "Chauffeur ID", :type "text", :required true})
      (w/field {:name "license-plate", :value license-plate
                :label "Kenteken", :type "text", :required true})

      [:div.actions
       [:button.button-primary {:type "submit"} "Toewijzen"]
       [:a.button {:href "."} "Annuleren"]]]]))

(defn assigned-trip [trip]
  (let [{:keys [ref]} (otm/trip->map trip)]
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
        [:ul [:li "Klantorder nr."] [:li "Chauffeur ID"] [:li "Kenteken"]]]]]]))



(defn get-trips [store]
  (->> store :trips vals (sort-by :ref) (reverse)))

(defn get-trip [store id]
  (get-in store [:trips id]))



(defn render [title h flash]
  (-> (w/render-body "tms" (str "TMS — " title) h
                     :flash flash)
      (response)
      (content-type "text/html")))

(defroutes handler
  (GET "/"  {:keys [flash store]}
    (render "Transportopdrachten"
            (list-trips (get-trips store))
            flash))

  (DELETE "/trip-:id" {:keys [store]
                       {:keys [id]} :params}
    (when (get-trip store id)
      (-> "."
          (redirect :see-other)
          (assoc :flash {:success "Transportopdracht verwijderd"})
          (assoc :store-commands [[:delete! :trips id]]))))

  (GET "/assign-:id" {:keys [flash store]
                      {:keys [id]} :params}
    (when-let [trip (get-trip store id)]
      (render (str "Transportopdracht: " (otm/trip-ref trip))
              (assign-trip trip)
              flash)))

  (POST "/assign-:id" {:keys [store]
                       {:keys [id driver license-plate]} :params}
    (when-let [trip (get-trip store id)]
      (-> (str "assigned-" id)
          (redirect :see-other)
          (assoc :flash {:success "Chauffeur en kenteken toegewezen"})
          (assoc :store-commands [[:put! :trips (-> trip
                                                    (otm/trip-driver! driver)
                                                    (otm/trip-license-plate! license-plate))]]))))

  (GET "/assigned-:id" {:keys [flash store]
                        {:keys [id]} :params}
    (when-let [trip (get-trip store id)]
      (render (str "Transportopdracht toegewezen")
              (assigned-trip trip)
              flash))))
