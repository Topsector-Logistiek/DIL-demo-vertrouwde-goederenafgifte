(ns dil-demo.erp
  (:require [dil-demo.erp.web :as web]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.otm :as otm]
            [dil-demo.web-utils :as web-utils]))

(defn- ->ishare-ar-policy-request [{:ishare/keys [client-id]
                                    :as          client-data} trip]
  (assoc client-data
         :ishare/endpoint "https://ar.isharetest.net/"
         :ishare/server-id "EU.EORI.NL000000004"
         :ishare/message-type :ishare/policy

         :ishare/params
         (policies/->delegation-evidence
          {:issuer  client-id
           :subject (otm/trip-carrier-eori trip)
           :target  (policies/->carrier-delegation-target (otm/trip-ref trip))})))


(comment
  (def store (dil-demo.store/load-store "/tmp/dil-demo.edn"))
  (def trip (-> store :trips first val))
  (def client-data (-> (dil-demo.core/->config) :erp (ishare-client/->client-data)))
  (def client-id (:ishare/client-id client-data))
  (def carrier-eori (-> (dil-demo.core/->config) :tms :eori))
  )

(defn- trip->ishare-ar! [client-data trip]
  (binding [ishare-client/log-interceptor-atom (atom [])]
    [(try (-> client-data
              (->ishare-ar-policy-request trip)
              (ishare-client/exec))
          (catch Throwable _ false))
     @ishare-client/log-interceptor-atom]))

(defn wrap-delegation
  "Create policies in AR when trips is created."
  [app]
  (fn delegation-wrapper [{:keys [client-data] :as req}]
    (let [{:keys [store-commands] :as res} (app req)
          trip (->> store-commands
                    (filter #(= [:put! :trips] (take 2 %)))
                    (map #(nth % 2))
                    (first))]
      (if trip
        (let [[result log] (trip->ishare-ar! client-data trip)]
          (cond-> (assoc-in res [:flash :ishare-log] log)
            (not result) (assoc-in [:flash :error] "Aanmaken AR policy mislukt")))
        res))))

(defn make-handler [config]
  (-> web/handler
      (web-utils/wrap-config config)
      (wrap-delegation)
      (ishare-client/wrap-client-data config)))
