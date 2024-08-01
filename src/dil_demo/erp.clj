;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.erp
  (:require [dil-demo.erp.web :as erp.web]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.store :as store]
            [clojure.tools.logging :as log]))

(defn- map->delegation-evidence
  [client-id effect {:keys [ref load] :as obj}]
  {:pre [client-id effect ref load]}
  (policies/->delegation-evidence
   {:issuer  client-id
    :subject (policies/outsource-pickup-access-subject obj)
    :target  (policies/->delegation-target ref)
    :date    (:date load)
    :effect  effect}))

(defn- ->ishare-ar-policy-request [{:ishare/keys [client-id]
                                    :as          client-data}
                                   effect
                                   obj]
  (assoc client-data
         :ishare/message-type :ishare/policy
         :ishare/params (map->delegation-evidence client-id
                                                  effect
                                                  obj)))

(defn- ishare-ar-create-policy! [client-data effect obj]
  (binding [ishare-client/log-interceptor-atom (atom [])]
    [(try (-> client-data
              (->ishare-ar-policy-request effect obj)
              (ishare-client/exec))
          (catch Throwable ex
            (log/error ex)
            false))
     @ishare-client/log-interceptor-atom]))

(defn- wrap-policy-deletion
  "When a trip is deleted, retract existing policies in the AR."
  [app]
  (fn policy-deletion-wrapper
    [{:keys [client-data ::store/store] :as req}]

    (let [{::store/keys [commands] :as res} (app req)]
      (if-let [id (-> (filter #(= [:delete! :consignments] (take 2 %))
                              commands)
                      (first)
                      (nth 2))]
        (let [old-consignment (get-in store [:consignments id])

              [result log]
              ;; kinda hackish way to delete a policy from a iSHARE AR
              (ishare-ar-create-policy! client-data "Deny" old-consignment)]
          (cond-> (update-in res [:flash :explanation] (fnil conj [])
                             ["Verwijderen policy" {:ishare-log log}])
            (not result) (assoc-in [:flash :error] "Verwijderen AR policy mislukt")))

        res))))

(defn wrap-delegation
  "Create policies in AR when trip is published."
  [app]
  (fn delegation-wrapper [{:keys [client-data] :as req}]
    (let [{::store/keys [commands] :as res} (app req)
          trip (->> commands
                    (filter #(= [:publish! :trips] (take 2 %)))
                    (map #(nth % 3))
                    (first))]
      (if trip
        (let [[result log]
              (ishare-ar-create-policy! client-data "Permit" trip)]
          (cond-> (update-in res [:flash :explanation] (fnil conj [])
                             ["Toevoegen policy" {:ishare-log log}])
            (not result) (assoc-in [:flash :error] "Aanmaken AR policy mislukt")))
        res))))

(defn make-handler [config]
  (-> (erp.web/make-handler config)
      (wrap-policy-deletion)
      (wrap-delegation)))
