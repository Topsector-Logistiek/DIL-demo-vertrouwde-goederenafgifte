;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.wms.verify
  (:require [dil-demo.ishare.client :as ishare-client]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.otm :as otm]))

(defn rejection-reasons [client-data {:keys [issuer target mask]}]
  (try
    (-> client-data
        (assoc :ishare/policy-issuer issuer ;; ensures we target the right AR
               :ishare/message-type :delegation
               :ishare/params mask)
        ishare-client/exec
        :ishare/result
        :delegationEvidence
        (policies/rejection-reasons target))
    (catch Throwable ex
      (prn ex)
      [(str "Technische fout opgetreden: " (.getMessage ex))])))

(defn verify-owner
  "Ask AR of owner if carrier is allowed to pickup order. Return list of
  rejection reasons or nil well access is allowed."
  [client-data transport-order
   {:keys [carrier-eori ref] :as params}]
  {:pre [carrier-eori ref]}

  (let [issuer (otm/transport-order-owner-eori transport-order)
        target (policies/->delegation-target (otm/transport-order-ref transport-order))
        mask   (policies/->delegation-mask {:issuer  issuer
                                            :subject (policies/outsource-pickup-access-subject params)
                                            :target  target})]
    (rejection-reasons client-data {:issuer issuer
                                    :target target
                                    :mask   mask})))

(defn verify-carrier-pickup
  "Ask AR of carrier if driver is allowed to pickup order. Return list
  of rejection reasons or nil well access is allowed."
  [client-data transport-order
   {:keys [carrier-eori driver-id-digits license-plate] :as params}]
  {:pre [carrier-eori driver-id-digits license-plate]}

  (let [target (policies/->delegation-target (otm/transport-order-ref transport-order))
        mask   (policies/->delegation-mask {:issuer  carrier-eori
                                            :subject (policies/pickup-access-subject params)
                                            :target  target})]
    (rejection-reasons client-data {:issuer carrier-eori
                                    :target target
                                    :mask   mask})))

(defn verify! [client-data transport-order params]
  (binding [ishare-client/log-interceptor-atom (atom [])]
    (let [owner-rejections (verify-owner client-data transport-order params)]
      {:owner-rejections   owner-rejections
       :carrier-rejections (when-not owner-rejections
                             (verify-carrier-pickup client-data transport-order params))
       :ishare-log         @ishare-client/log-interceptor-atom})))

(defn permitted? [{:keys [owner-rejections carrier-rejections]}]
  (and (empty? owner-rejections)
       (empty? carrier-rejections)))
