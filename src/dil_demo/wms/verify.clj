(ns dil-demo.wms.verify
  (:require [dil-demo.ishare.client :as ishare-client]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.otm :as otm]))

(defn verify-owner
  "Ask AR of owner if carrier is allowed to pickup order."
  [client-data transport-order params]
  (try

    (let [issuer (otm/transport-order-owner-eori transport-order)
          target (policies/->delegation-target (otm/transport-order-ref transport-order))
          mask   (policies/->delegation-mask {:issuer  issuer
                                              :subject  (policies/ishare-delegation-access-subject params)
                                              :target  target})]
      (-> client-data
          (assoc :ishare/message-type :delegation
                 :ishare/policy-issuer issuer ;; ensures we target the right server (AR)
                 :ishare/params mask)
          ishare-client/exec
          :ishare/result
          :delegationEvidence
          (policies/rejection-reasons target)))
    (catch Throwable ex
      [(str "Technische fout opgetreden: " (.getMessage ex))])))

(defn verify-carrier
  "Ask AR of carrier if driver is allowed to pickup order."
  [client-data transport-order {:keys [carrier-eori] :as params}]

  (try
    (let [target (policies/->delegation-target (otm/transport-order-ref transport-order))
          mask   (policies/->delegation-mask {:subject (policies/poort8-delegation-access-subject params)
                                              :target  target
                                              ;; FEEDBACK: Kunnen we er vanuit gaan dat issuer
                                              ;; en dataspace id samen de AR bepalen?
                                              :issuer  carrier-eori})]
      (-> client-data
          (assoc :ishare/message-type :delegation
                 :ishare/policy-issuer carrier-eori
                 :ishare/params mask)
          ishare-client/exec
          :ishare/result
          :delegationEvidence
          (policies/rejection-reasons target)))
    (catch Throwable ex
      [(str "Technische fout opgetreden: " (.getMessage ex))])))

(defn verify! [client-data transport-order params]
  (binding [ishare-client/log-interceptor-atom (atom [])]
    (let [owner-rejections (verify-owner client-data transport-order params)]
      {:owner-rejections   owner-rejections
       :carrier-rejections (when-not owner-rejections
                             (verify-carrier client-data transport-order params))
       :ishare-log         @ishare-client/log-interceptor-atom})))

(defn permitted? [{:keys [owner-rejections carrier-rejections]}]
  (and (empty? owner-rejections)
       (empty? carrier-rejections)))
