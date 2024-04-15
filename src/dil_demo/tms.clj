(ns dil-demo.tms
  (:require [dil-demo.tms.web :as web]
            [clojure.tools.logging.readable :as log]
            [dil-demo.ishare.policies :as policies]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.otm :as otm]))

(defn- append-in
  "Insert `coll` into collection at `path` in `m`.

  If there is not collection at `path` insert into empty vector."
  [m path coll]
  (update-in m path (fnil into []) coll))

(defn- ishare-exec-with-log
  "Execute ishare command and update response.

  Inserts :ishare/result in response
  http request log will be put in the [:flash :ishare-log]
  if an exception occurs, it will be logged, and noted in [:flash :error]"
  [response command]
  (let [response (binding [ishare-client/log-interceptor-atom (atom [])]
                   (try
                     (let [result (:ishare/result (ishare-client/exec command))]
                       (-> response
                           (append-in [:flash :ishare-log] @ishare-client/log-interceptor-atom)
                           (assoc :ishare/result result)))
                     (catch Exception ex
                       (log/error ex)
                       (-> response
                           (append-in [:flash :ishare-log] @ishare-client/log-interceptor-atom)
                           (assoc-in [:flash :error] (str "Fout bij uitvoeren van iShare commando " (:ishare/message-type ex)))))))]
    response))

(defn- trip-added
  "Returns the trip that will stored from the response"
  [{:keys [store-commands] :as response}]
  (when-let [cmd (first (filter #(= [:put! :trips] (take 2 %)) store-commands))]
    (nth cmd 2)))

(defn- trip-deleted-id
  "Returns the trip id that will be deleted from the response"
  [{:keys [store-commands] :as response}]
  (when-let [cmd (first (filter #(= [:delete! :trips] (take 2 %)) store-commands))]
    (nth cmd 2)))

(defn- wrap-policy-deletion
  "When a trip is added or deleted, retract existing policies in the AR"
  [app]
  (fn policy-deletion-wrapper
    [{:keys [client-data store] :as req}]
    (let [response (app req)
          trip     (or (trip-added response)
                       (get-in store [:trips (trip-deleted-id response)]))]
      (if-let [policy-id (and trip (get-in store [:trip-policies (otm/trip-ref trip) :policy-id]))]
        (ishare-exec-with-log response (-> client-data
                                           (assoc :ishare/message-type :poort8/delete-policy
                                                  :ishare/params {:policyId policy-id}
                                                  :throw false ;; ignore HTTP errors
                                                  )))
        response))))

(defn- wrap-delegation
  "Create policies in AR when trips is created."
  [app]
  (fn delegation-wrapper [{:keys [client-data] :as req}]
    (let [response (app req)
          trip     (trip-added response)]
      (if-let [subject (and trip (policies/poort8-delegation-access-subject (otm/trip->map trip)))]
        (let [response (ishare-exec-with-log response
                                             (-> client-data
                                                 (assoc :ishare/message-type :poort8/policy
                                                        :ishare/params (policies/->poort8-policy {:consignment-ref (otm/trip-ref trip)
                                                                                                  :date            (otm/trip-load-date trip)
                                                                                                  :subject         subject}))))]
          (if-let [policy-id (get-in response [:ishare/result "policyId"])]
            (append-in response [:store-commands] [[:put! :trip-policies {:id        (otm/trip-ref trip)
                                                                          :policy-id policy-id}]])
            response))
        ;; no valid driver ref, so don't create a policy
        response))))

(defn make-handler [config]
  (-> web/handler
      (wrap-policy-deletion)
      (wrap-delegation)
      (ishare-client/wrap-client-data config)))
