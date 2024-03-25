(ns dil-demo.erp.web
  (:require [compojure.core :refer [defroutes DELETE GET POST]]
            [dil-demo.otm :as otm]
            [dil-demo.web-utils :as w]
            [ring.util.response :refer [content-type redirect response]])
  (:import [java.util UUID Date]))

(defn list-consignments [consignments]
  (let [actions [:a.button.button-primary {:href "consignment-new"} "Nieuw"]]
    [:table
     [:thead
      [:tr
       [:th.ref "Klantorder nr."]
       [:th.date "Orderdatum"]
       [:th.location "Ophaaladres"]
       [:th.location "Afleveradres"]
       [:th.goods "Goederen"]
       [:th.status "Status"]
       [:td.actions actions]]]
     [:tbody
      (when-not (seq consignments)
        [:tr.empty
         [:td {:colspan 999}
          "Nog geen klantorders geregistreerd.."]])

      (for [{:keys [id ref load-date load-location unload-location goods status]}
            (map otm/consignment->map consignments)]
        [:tr.consignment
         [:td.ref ref]
         [:td.date load-date]
         [:td.location load-location]
         [:td.location unload-location]
         [:td.goods goods]
         [:td.status (otm/statuses status)]
         [:td.actions
          [:a.button.button-secondary {:href (str "consignment-" id)} "Openen"]
          (w/delete-button (str "consignment-" id))]])]
     [:tfoot
      [:tr
       [:td.actions {:colspan 7} actions]]]]))

(defn edit-consignment [consignment {:keys [carriers]}]
  (let [{:keys [id status ref load-date load-location load-remarks unload-location unload-date unload-remarks goods carrier-eori]}
        (otm/consignment->map consignment)]
    [:form {:method "POST"}
     (w/anti-forgery-input)

     [:section
      (w/field {:name  "status", :value status,
                :label "Status", :type  "select", :list otm/statuses})
      (w/field {:name  "ref",            :value ref,
                :label "Klantorder nr.", :type  "number", :required true})]
     [:section
      (w/field {:name  "load-date",   :type  "date",
                :label "Ophaaldatum", :value load-date})
      (w/field {:name  "load-location", :value load-location,
                :label "Ophaaladres",   :type  "text", :list w/locations, :required true})
      (w/field {:name  "load-remarks", :value load-remarks,
                :label "Opmerkingen",  :type  "textarea"})]
     [:section
      (w/field {:name  "unload-date",  :value unload-date,
                :label "Afleverdatum", :type  "date"})
      (w/field {:name  "unload-location", :value unload-location,
                :label "Afleveradres",    :type  "text", :list w/locations, :required true})
      (w/field {:name  "unload-remarks", :value unload-remarks,
                :label "Opmerkingen",    :type  "textarea"})]
     [:section
      (w/field {:name  "goods",    :value goods,
                :label "Goederen", :type  "text", :list w/goods, :required true})
      (w/field {:name  "carrier-eori", :value carrier-eori,
                :label "Vervoerder",   :type  "select", :list carriers, :required true})]
     [:div.actions
      [:button.button.button-primary {:type "submit"} "Bewaren"]
      (when id
        [:a.button.button-secondary {:href (str "publish-" id)} "Transportopdracht aanmaken"])
      [:a.button {:href "."} "Annuleren"]]]))

(defn publish-consignment [consignment {:keys [carriers]}]
  (let [{:keys [id ref load-date load-location load-remarks unload-date unload-location unload-remarks goods carrier-eori]}
        (otm/consignment->map consignment)]
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
        [:dd unload-date]]
       [:div
        [:dt "Vervoerder"]
        [:dd (carriers carrier-eori)]]]]
     [:section.trip
      [:fieldset.load-location
       [:legend "Ophaaladres"]
       [:h3 load-location]
       [:pre "Kerkstraat 1\n1234 AB  Nergenshuizen"]
       [:blockquote.remarks load-remarks]]
      [:fieldset.unload-location
       [:legend "Afleveradres"]
       [:h3 unload-location]
       [:pre "Dorpsweg 2\n4321 YZ  Andershuizen"]
       [:blockquote.remarks unload-remarks]]]
     [:section.goods
      [:fieldset
       [:legend "Goederen"]
       [:pre goods]]]
     [:div.actions
      [:button.button-primary {:type    "submit"
                               :onclick "return confirm('Zeker weten?')"}
       "Versturen"]
      [:a.button {:href (str "consignment-" id)} "Annuleren"]]]))

(defn published-consignment [consignment {:keys [carriers ishare]}]
  (let [{:keys [load-location carrier-eori]} (otm/consignment->map consignment)]
    [:div
     [:section
      [:p "Transportopdracht verstuurd naar locatie "
       [:q load-location]
       " en vervoerder "
       [:q (carriers carrier-eori)]
       "."]
      [:div.actions
       [:a.button {:href "."} "Terug naar overzicht"]]]
     [:details.explanation
      [:summary "Uitleg"]
      [:ol
       [:li
        [:h3 "Stuur OTM Transportopdracht naar WMS van DC"]
        [:pre.json (w/to-json (otm/consignment->transport-order consignment))]]
       [:li
        [:h3 "Stuur OTM Trip naar TMS van Vervoerder"]
        [:pre.json (w/to-json (otm/consignment->trip consignment))]]
       [:li
        [:h3 "Registreer policy bij AR"]
        [:pre.json (w/to-json ishare)]]]]]))



(defn get-consignments [store]
  (->> store :consignments vals (sort-by :ref) (reverse)))

(defn get-consignment [store id]
  (get-in store [:consignments id]))

(defn next-consignment-ref [store]
  (let [refs (->> store
                  (get-consignments)
                  (map otm/consignment-ref)
                  (map #(Integer/parseInt %)))]
    (str (inc (apply max 20231337 refs)))))



(defn render [title h flash]
  (-> (w/render-body "erp" (str "ERP — " title ) h
                     :flash flash)
      (response)
      (content-type "text/html")))

(defroutes handler
  (GET "/" {:keys [flash store]}
    (render "Klantorders"
            (list-consignments (get-consignments store))
            flash))

  (GET "/consignment-new" {:keys [carriers flash store]}
    (render "Nieuwe klantorder"
            (edit-consignment
             (otm/map->consignment {:ref         (next-consignment-ref store)
                                    :load-date   (w/format-date (Date.))
                                    :unload-date (w/format-date (Date.))
                                    :status      "draft"})
             {:carriers carriers})
            flash))

  (POST "/consignment-new" {:keys [params]}
    (let [id (str (UUID/randomUUID))]
      (-> "."
          (redirect :see-other)
          (assoc :flash {:success "Klantorder aangemaakt"})
          (assoc :store-commands [[:put! :consignments
                                   (otm/map->consignment (assoc params
                                                                :id id
                                                                :status "draft"))]]))))

  (GET "/consignment-:id" {:keys [carriers flash store] {:keys [id]} :params}
    (let [consignment (get-consignment store id)]
      (render (str "Klantorder: " (otm/consignment-ref consignment))
              (edit-consignment consignment {:carriers carriers})
              flash)))

  (POST "/consignment-:id" {:keys [params]}
    (-> "."
        (redirect :see-other)
        (assoc :flash {:success "Klantorder aangepast"})
        (assoc :store-commands [[:put! :consignments (otm/map->consignment params)]])))

  (DELETE "/consignment-:id" {:keys [store] {:keys [id]} :params}
    (when (get-consignment store id)
      (-> "."
          (redirect :see-other)
          (assoc :flash {:success "Klantorder verwijderd"})
          (assoc :store-commands [[:delete! :consignments id]]))))

  (GET "/publish-:id" {:keys [carriers flash store] {:keys [id]} :params}
    (when-let [consignment (get-consignment store id)]
      (render "Transportopdracht aanmaken"
              (publish-consignment consignment {:carriers carriers})
              flash)))

  (POST "/publish-:id" {:keys [store] {:keys [id]} :params}
    (when-let [consignment (get-consignment store id)]
      (-> (str "published-" id)
          (redirect :see-other)
          (assoc :flash {:success "Transportopdracht aangemaakt"})
          (assoc :store-commands [[:put! :consignments (assoc consignment :status "requested")]
                                  [:put! :transport-orders (otm/consignment->transport-order consignment)]
                                  [:put! :trips (otm/consignment->trip consignment)]]))))

  (GET "/published-:id" {:keys            [carriers flash store]
                         {:keys [id]}     :params
                         {:keys [ishare]} :flash}
    (when-let [consignment (get-consignment store id)]
      (render "Transportopdracht aangemaakt"
              (published-consignment consignment {:carriers carriers
                                                  :ishare   ishare})
              flash))))
