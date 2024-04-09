(ns dil-demo.tms
  (:require [dil-demo.tms.web :as web]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.otm :as otm]))


(defn driver-licence-ref
  "Returns driver+license-plate ref

  Returns nil if driver or license-plate is missing."
  [trip]
  (let [id-digits     (otm/trip-driver-id-digits trip)
        licence-plate (otm/trip-license-plate trip)]
    (when (and id-digits licence-plate)
      (str id-digits "|" licence-plate))))

(defn- ->poort8-policy-request [client-data trip]
  (assoc client-data
         :ishare/endpoint    "https://tsl-ishare-dataspace-coremanager-preview.azurewebsites.net/api/ishare"
         :ishare/server-id   "EU.EORI.NLP8TSLAR1"
         :ishare/message-type :poort8/policy
         :ishare/params (policies/->poort8-policy {:consignment-ref (otm/trip-ref trip)
                                                   :subject (driver-licence-ref trip)})))

(defn- trip->poort8-ar! [client-data trip]
  (binding [ishare-client/log-interceptor-atom (atom [])]
    [(try (-> client-data
              (->poort8-policy-request trip)
              (ishare-client/exec))
          ;; TODO: log/show Exceptions for debugging
          (catch Throwable _ false))
     @ishare-client/log-interceptor-atom]))

;; FEEDBACK: Hoe trekken we een policy weer in als we chauffeur
;; veranderen?

(defn wrap-delegation
  "Create policies in AR when trips is created."
  [app]
  (fn delegation-wrapper [{:keys [client-data] :as req}]
    (let [{:keys [store-commands] :as res} (app req)
          trip (->> store-commands
                    (filter #(= [:put! :trips] (take 2 %)))
                    (map #(nth % 2))
                    (first))]
      (if (and trip (driver-licence-ref trip))
        (let [[result log] (trip->poort8-ar! client-data trip)]
          (cond-> (assoc-in res [:flash :ishare-log] log)
            (not result) (assoc-in [:flash :error] "Aanmaken AR policy mislukt")))
        res))))

(defn make-handler [config]
  (-> web/handler
      (wrap-delegation)
      (ishare-client/wrap-client-data config)))
