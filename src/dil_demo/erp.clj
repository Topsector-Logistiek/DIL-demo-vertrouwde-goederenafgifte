;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.erp
  (:require [dil-demo.erp.web :as web]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.store :as store]
            [dil-demo.otm :as otm]
            [clojure.tools.logging :as log]
            [dil-demo.web-utils :as web-utils]))

(defn- map->delegation-evidence
  [client-id effect {:keys [ref load-date] :as m}]
  {:pre [client-id effect ref load-date]}
  (policies/->delegation-evidence
   {:issuer  client-id
    :subject (policies/ishare-delegation-access-subject m)
    :target  (policies/->delegation-target ref)
    :date    load-date
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

(defn- ishare-ar! [client-data effect obj]
  (binding [ishare-client/log-interceptor-atom (atom [])]
    [(try (-> client-data
              (->ishare-ar-policy-request effect obj)
              (ishare-client/exec))
          (catch Throwable ex
            (log/error ex)
            false))
     @ishare-client/log-interceptor-atom]))

(defn- wrap-policy-deletion
  "When a trip is added or deleted, retract existing policies in the AR"
  [app]
  (fn policy-deletion-wrapper
    [{:keys [client-data ::store/store] :as req}]

    (let [{::store/keys [commands] :as res} (app req)]
      (if-let [id (-> (filter #(= [:delete! :consignments] (take 2 %))
                              commands)
                        (first)
                        (nth 2))]
        (let [consignment (get-in store [:consignments id])
              [result log] (ishare-ar! client-data "Deny" (otm/consignment->map consignment))]
          (cond-> (assoc-in res [:flash :ishare-log] log)
            (not result) (assoc-in [:flash :error] "Verwijderen AR policy mislukt")))

        res))))

(defn wrap-delegation
  "Create policies in AR when trips is created."
  [app]
  (fn delegation-wrapper [{:keys [client-data] :as req}]
    (let [{::store/keys [commands] :as res} (app req)
          trip (->> commands
                    (filter #(= [:publish! :trips] (take 2 %)))
                    (map #(nth % 3))
                    (first))]
      (if trip
        (let [[result log] (ishare-ar! client-data "Permit" (otm/trip->map trip))]
          (cond-> (assoc-in res [:flash :ishare-log] log)
            (not result) (assoc-in [:flash :error] "Aanmaken AR policy mislukt")))
        res))))

(defn make-handler [config]
  (-> (web/make-handler config)
      (web-utils/wrap-config config)
      (wrap-policy-deletion)
      (wrap-delegation)
      (ishare-client/wrap-client-data config)))
