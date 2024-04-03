(ns dil-demo.erp
  (:require [dil-demo.erp.web :as web]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.otm :as otm]
            [dil-demo.web-utils :as web-utils])
  (:import (clojure.lang ExceptionInfo)))

(defn- trip->delegation-evidence [client-id trip]
  (let [{:keys [ref carrier-eori]} (otm/trip->map trip)]
    {"delegationEvidence"
     {"notBefore"    0 ;; TODO
      "notOnOrAfter" 0 ;; TODO
      "policyIssuer" client-id
      "target"       {"accessSubject" carrier-eori}
      "policySets"
      [{"policies" [{"rules"  [{"effect" "Permit"}]
                     "target" {"resource" {"type"        "klantordernummer"
                                           "identifiers" [ref]
                                           "attributes"  ["*"]}
                               "actions"  ["BDI.PICKUP"]}}]
        "target"   {"resource"    {"type"        "klantordernummer"
                                   "identifiers" [ref]
                                   "attributes"  ["*"]}
                    "actions"     ["BDI.PICKUP"]
                    "environment" {"licenses" ["0001"]}}}]}}))

(defn- ->ishare-ar-policy-request [{:ishare/keys [client-id] :as client-data}
                                  trip]
  (assoc client-data
         :ishare/endpoint "https://ar.isharetest.net/"
         :ishare/server-id "EU.EORI.NL000000004"
         :ishare/message-type :ishare/policy
         :ishare/params (trip->delegation-evidence client-id trip)))

(defn- trips->ishare-ar! [client-data trips]
  (mapv #(let [req (->ishare-ar-policy-request client-data %)
               res (:ishare/result (ishare-client/exec req))]
           {:request  (dissoc req :ishare/private-key)
            :response res})
        trips))

(defn wrap-delegation
  "Create policies in AR when trips is created."
  [app {:keys [eori key-file chain-file]}]
  (let [client-data {:ishare/client-id   eori
                     :ishare/private-key (ishare-client/private-key key-file)
                     :ishare/x5c         (ishare-client/x5c chain-file)}]
    (fn delegation-wrapper [req]
      (let [{:keys [store-commands] :as res} (app req)]
        (try
          (let [ishare (->> store-commands
                            (filter #(= [:put! :trips] (take 2 %)))
                            (map #(nth % 2))
                            (trips->ishare-ar! client-data))]
            (if (seq ishare)
              (assoc-in res [:flash :ishare] ishare)
              res))
          (catch ExceptionInfo ex
            (-> res
                (assoc-in [:flash :ishare] (-> ex (ex-data) (select-keys [:status :body])))
                (assoc-in [:flash :error] "Aanmaken AR policy mislukt"))))))))

(defn make-handler [config]
  (-> web/handler
      (web-utils/wrap-config config)
      (wrap-delegation config)))
