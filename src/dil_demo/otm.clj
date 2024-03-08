(ns dil-demo.otm
  (:import [java.util UUID]))

(def statuses {"draft"     "Klad"
               "requested" "Ingediend"
               "confirmed" "Bevestigd"
               "inTransit" "In Transit"
               "completed" "Afgerond"
               "cancelled" "Geannuleerd"})



;; OTM Consignment for ERP

(defn map->consignment
  [{:keys [id ref status goods carrier-eori load-date load-location load-remarks unload-date unload-location unload-remarks]}]
  {:id                  id
   :external-attributes {:ref ref}
   :status              status

   :goods
   [{:association-type "inline"
     :entity           {:description goods}}]

   :actors
   [{:association-type "inline"
     :roles #{"carrier"}
     :entity
     {:contact-details [{:type "eori"
                         :value carrier-eori}]}}]

   :actions
   [{:association-type "inline"
     :entity
     {:action-type "load"
      :start-time  load-date
      :location    {:association-type "inline"
                    :entity           {:name          load-location
                                       :type          "warehouse"
                                       :geo-reference {}}}
      :remarks     load-remarks}}
    {:association-type "inline"
     :entity
     {:action-type "unload"
      :start-time  unload-date
      :location    {:association-type "inline"
                    :entity           {:name          unload-location
                                       :type          "warehouse"
                                       :geo-reference {}}}
      :remarks     unload-remarks}}]})

(defn consignment-ref [{{:keys [ref]} :external-attributes}]
  ref)

(defn consignment-status [{:keys [status]}]
  status)

(defn consignment-goods [{[{{:keys [description]} :entity}] :goods}]
  description)

(defn consignment-action [{:keys [actions]} action-type]
  (->> actions
       (map :entity)
       (filter #(= action-type (:action-type %)))
       (first)))

(defn consignment-load-date [consignment]
  (:start-time (consignment-action consignment "load")))

(defn consignment-unload-date [consignment]
  (:start-time (consignment-action consignment "unload")))

(defn consignment-action-location [consignment action-type]
  (-> consignment
      (consignment-action action-type)
      :location
      :entity
      :name))

(defn consignment-load-location [consignment]
  (consignment-action-location consignment "load"))

(defn consignment-unload-location [consignment]
  (consignment-action-location consignment "unload"))

(defn consignment-load-remarks [consignment]
  (-> consignment
      (consignment-action "load")
      :remarks))

(defn consignment-unload-remarks [consignment]
  (-> consignment
      (consignment-action "unload")
      :remarks))

(defn consignment-actor [{:keys [actors]} role]
  (->> actors
       (filter #(get (:roles %) role))
       (map :entity)
       (first)))

(defn consignment-carrier-eori [consignment]
  (->> (-> consignment
           (consignment-actor "carrier")
           :contact-details)
       (filter #(= "eori" (:type %)))
       (first)
       :value))

(defn consignment->map [consignment]
  {:id              (:id consignment)
   :ref             (consignment-ref consignment)
   :status          (consignment-status consignment)
   :load-date       (consignment-load-date consignment)
   :load-location   (consignment-load-location consignment)
   :load-remarks    (consignment-load-remarks consignment)
   :unload-date     (consignment-unload-date consignment)
   :unload-location (consignment-unload-location consignment)
   :unload-remarks  (consignment-unload-remarks consignment)
   :goods           (consignment-goods consignment)
   :carrier-eori    (consignment-carrier-eori consignment)})



;; OTM TransportOrder for WMS

(defn consignment->transport-order [consignment]
  {:id           (str (UUID/randomUUID))
   :consignments
   [{:association-type "inline"
     :entity           (-> consignment
                           (dissoc :actors)
                           (update :actions
                                   (fn [actions]
                                     (filter #(= "load" (-> % :entity :action-type))
                                             actions))))}]})

(defn transport-order->map [{:keys [id] :as transport-order}]
  (-> transport-order
      (get-in [:consignments 0 :entity])
      (consignment->map)
      (assoc :id id)))

(defn transport-order-consignment [transport-order]
  (get-in transport-order [:consignments 0 :entity]))

(defn transport-order-ref [transport-order]
  (consignment-ref (transport-order-consignment transport-order)))



;; OTM Trip for TMS

(defn consignment->trip [consignment]
  {:id                  (str (UUID/randomUUID))
   :external-attributes {:ref (consignment-ref consignment)}

   :actions
   [{:association-type "inline"
     :entity
     {:action-type "load"
      :start-time  (consignment-load-date consignment)
      :location    {:association-type "inline"
                    :entity           {:name          (consignment-load-location consignment)
                                       :type          "warehouse"
                                       :geo-reference {}}}
      :remarks     (consignment-load-remarks consignment)}}
    {:association-type "inline"
     :entity
     {:action-type "unload"
      :start-time  (consignment-unload-date consignment)
      :location    {:association-type "inline"
                    :entity           {:name          (consignment-unload-location consignment)
                                       :type          "warehouse"
                                       :geo-reference {}}}
      :remarks     (consignment-unload-remarks consignment)}}]})

(defn map->trip [{:keys [id ref load-date load-location load-remarks unload-date unload-location unload-remarks driver-id-digits license-plate]}]
  {:id                  id
   :external-attributes {:ref ref}

   :vehicle
   [{:association-type "inline"
     :entity           {:license-plate license-plate}}]

   :actors
   [{:association-type "inline"
     :roles #{"driver"}
     :entity
     {:external-attributes {:id-digits driver-id-digits}}}]

   :actions
   [{:association-type "inline"
     :entity
     {:action-type "load"
      :start-time  load-date
      :location    {:association-type "inline"
                    :entity           {:name          load-location
                                       :type          "warehouse"
                                       :geo-reference {}}}
      :remarks     load-remarks}}
    {:association-type "inline"
     :entity
     {:action-type "unload"
      :start-time  unload-date
      :location    {:association-type "inline"
                    :entity           {:name          unload-location
                                       :type          "warehouse"
                                       :geo-reference {}}}
      :remarks     unload-remarks}}]})

(defn trip-ref [trip]
  (get-in trip [:external-attributes :ref]))

(defn trip-action [{:keys [actions]} action-type]
  (->> actions
       (map :entity)
       (filter #(= action-type (:action-type %)))
       (first)))

(defn trip-load-date [consignment]
  (:start-time (consignment-action consignment "load")))

(defn trip-unload-date [consignment]
  (:start-time (consignment-action consignment "unload")))

(defn trip-action-location [consignment action-type]
  (-> consignment
      (consignment-action action-type)
      :location
      :entity
      :name))

(defn trip-load-location [consignment]
  (consignment-action-location consignment "load"))

(defn trip-unload-location [consignment]
  (consignment-action-location consignment "unload"))

(defn trip-load-remarks [consignment]
  (-> consignment
      (consignment-action "load")
      :remarks))

(defn trip-unload-remarks [consignment]
  (-> consignment
      (consignment-action "unload")
      :remarks))

(defn trip-actor [{:keys [actors]} role]
  (->> actors
       (filter #(get (:roles %) role))
       (map :entity)
       (first)))

(defn trip-driver-id-digits [trip]
  (-> trip
      (trip-actor "driver")
      :external-attributes
      :id-digits))

(defn trip-driver-id-digits! [trip driver-id-digits]
  (update trip :actors
          (fn [actors]
            (concat (filterv (complement #(get (:roles %) "driver")) actors)
                    [{:association-type "inline"
                      :roles #{"driver"}
                      :entity {:external-attributes {:id-digits driver-id-digits}}}]))))

(defn trip-license-plate [trip]
  (get-in trip [:vehicle 0 :entity :license-plate]))

(defn trip-license-plate! [trip license-plate]
  (assoc trip :vehicle [{:association-type "inline"
                         :entity {:license-plate license-plate}}]))

(defn trip->map [trip]
  {:id              (:id trip)
   :ref             (trip-ref trip)
   :load-date       (trip-load-date trip)
   :load-location   (trip-load-location trip)
   :load-remarks    (trip-load-remarks trip)
   :unload-date     (trip-unload-date trip)
   :unload-location (trip-unload-location trip)
   :unload-remarks  (trip-unload-remarks trip)
   :driver-id-digits          (trip-driver-id-digits trip)
   :license-plate   (trip-license-plate trip)})
