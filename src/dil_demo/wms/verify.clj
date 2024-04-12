(ns dil-demo.wms.verify
  (:require [dil-demo.ishare.client :as ishare-client]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.otm :as otm]))

(defn verify-owner
  "Ask AR of owner if carrier is allowed to pickup order."
  [client-data transport-order {:keys [carrier-eori]}]
  (try
    (let [owner-eori (otm/transport-order-owner-eori transport-order)
          target     (policies/->delegation-target (otm/transport-order-ref transport-order))
          mask       (policies/->delegation-mask {:issuer  owner-eori
                                                  :subject carrier-eori
                                                  :target  target})]
      (-> client-data
          (ishare-client/ar-delegation-evidence mask
                                                {:party-eori owner-eori})
          (policies/rejection-reasons target)))
    (catch Throwable ex
      [(str "Technische fout opgetreden: " (.getMessage ex))])))

(defn verify-carrier
  "Ask AR of carrier if driver is allowed to pickup order."
  [client-data transport-order {:keys [driver-id-digits license-plate carrier-eori]}]

  (try
    (let [target (policies/->delegation-target (otm/transport-order-ref transport-order))
          mask   (policies/->delegation-mask {:subject (str driver-id-digits "|" license-plate)
                                              ;; TODO: use function for subject
                                              :target  target
                                              ;; FEEDBACK: Kunnen we er vanuit gaan dat issuer
                                              ;; en dataspace id samen de AR bepalen?
                                              :issuer  carrier-eori})]
      (-> client-data
          (ishare-client/ar-delegation-evidence mask
                                                {:party-eori carrier-eori})
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

(comment
  (def store (dil-demo.store/load-store "/tmp/dil-demo.edn"))
  (def transport-order (-> store :transport-orders first val))
  (def client-data (-> (dil-demo.core/->config) :wms (ishare-client/->client-data)))
  (def carrier-eori (-> (dil-demo.core/->config) :tms :eori))
  )
