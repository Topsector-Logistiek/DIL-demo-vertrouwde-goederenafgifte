;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.tms
  (:require [dil-demo.tms.web :as tms.web]
            [clojure.tools.logging.readable :as log]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.store :as store]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.otm :as otm]))

(defn- append-in
  "Insert `coll` into collection at `path` in `m`.

  If there is not collection at `path` insert into empty vector."
  [m path coll]
  (update-in m path (fnil into []) coll))



(defn- ishare-exec! [{:keys [client-data] :as req} cmd]
  (binding [ishare-client/log-interceptor-atom (atom [])]
    (try
      (let [result (:ishare/result (ishare-client/exec (merge client-data cmd)))]
        (-> req
            (append-in [:flash :ishare-log] @ishare-client/log-interceptor-atom)
            (assoc :ishare/result result)))
      (catch Exception ex
        (log/error ex)
        (-> req
            (append-in [:flash :ishare-log] @ishare-client/log-interceptor-atom)
            (assoc-in [:flash :error] (str "Fout bij uitvoeren van iShare commando " (:ishare/message-type ex))))))))



(defn-  ->ar-type
  [{{:ishare/keys [authentication-registry-type]} :client-data} & _]
  authentication-registry-type)

(defn- ishare-create-policy-command
  [{{:ishare/keys [client-id]} :client-data} subject trip effect]
  {:pre [(#{"Deny" "Permit"} effect)]}
  {:ishare/message-type :ishare/policy
   :ishare/params       (policies/->delegation-evidence
                         {:issuer  client-id
                          :subject subject
                          :target  (policies/->delegation-target (otm/trip-ref trip))
                          :date    (otm/trip-load-date trip)
                          :effect  effect})})

(defmulti delete-policy-for-trip! ->ar-type)

(defmethod delete-policy-for-trip! :poort8
  [{:keys [::store/store] :as req} {:keys [id] :as _trip} _subject]
  (if-let [policy-id (get-in store [:trip-policies id :policy-id])]
    (-> req
        (ishare-exec! {:throw false ;; ignore HTTP errors for when
                                    ;; policy already deleted

                       :ishare/message-type :poort8/delete-policy
                       :ishare/params       {:policyId policy-id}})
        (append-in [::store/commands]
                   [[:delete! :trip-policies id]]))
    req))

(defmethod delete-policy-for-trip! :ishare
  [req trip subject]
  (ishare-exec! req
                (ishare-create-policy-command req subject trip "Deny")))

(defmulti create-policy-for-trip! ->ar-type)

(defmethod create-policy-for-trip! :poort8
  [req {:keys [id] :as trip} subject]
  (let [cmd
        {:ishare/message-type :poort8/policy
         :ishare/params
         (policies/->poort8-policy
          {:consignment-ref (otm/trip-ref trip)
           :date            (otm/trip-load-date trip)
           :subject         subject})}
        res (ishare-exec! req cmd)]

    (if-let [policy-id (get-in res [:ishare/result "policyId"])]
      (append-in res [::store/commands]
                 [[:put! :trip-policies {:id id, :policy-id policy-id}]])
      res)))

(defmethod create-policy-for-trip! :ishare
  [req trip subject]
  (ishare-exec! req
                (ishare-create-policy-command req subject trip "Permit")))

(defmulti delegation-effect!
  "Apply delegation effects from store commands."
  (fn [_req [cmd type & _args]] [cmd type]))

(defmethod delegation-effect! [:delete! :trips]
  [{:keys [::store/store] :as req} [_ _ id]]
  (if-let [trip (get-in store [:trips id])]
    (cond-> req
      (and (otm/trip-driver-id-digits trip) (otm/trip-license-plate trip))
      (delete-policy-for-trip! trip
                               (policies/pickup-access-subject (otm/trip->map trip)))

      :and
      (delete-policy-for-trip! trip
                               (policies/outsource-pickup-access-subject (otm/trip->map trip))))
    req))

(defmethod delegation-effect! [:put! :trips]
  [{:keys [::store/store] :as req} [_ _ trip]]
  (let [old-trip (get-in store [:trips (:id trip)])]

    (cond-> req
      ;; delete pre existing driver/pickup policy
      (and (otm/trip-driver-id-digits old-trip) (otm/trip-license-plate old-trip))
      (delete-policy-for-trip! old-trip
                               (policies/pickup-access-subject (otm/trip->map old-trip)))

      ;; create driver/pickup policy
      (and (otm/trip-driver-id-digits trip) (otm/trip-license-plate trip))
      (create-policy-for-trip! trip
                               (policies/pickup-access-subject (otm/trip->map trip))))))

(defmethod delegation-effect! [:publish! :trips]
  [req [_ _ other-eori trip]]
  (let [sub (policies/outsource-pickup-access-subject
             (-> trip
                 (otm/trip->map)
                 (assoc :carrier-eori other-eori)))

        ;; remove already existing policy
        res (delete-policy-for-trip! req trip sub)]

    ;; create outsource policy
    (create-policy-for-trip! res trip sub)))

;; do nothing for other store commands
(defmethod delegation-effect! :default [req & _] req)



(defn- wrap-delegation
  "Create policies in AR when trip is stored."
  [app]
  (fn delegation-wrapper [{:keys [client-data ::store/store] :as req}]
    (let [{::store/keys [commands] :as res} (app req)]
      (reduce delegation-effect!
              (assoc res
                     :client-data client-data
                     ::store/store store)
              commands))))

(defn make-handler [config]
  (-> (tms.web/make-handler config)
      (wrap-delegation)
      (ishare-client/wrap-client-data config)))
