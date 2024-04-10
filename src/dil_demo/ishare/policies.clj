(ns dil-demo.ishare.policies
  (:import (java.time Instant LocalDate LocalDateTime ZoneId)
           java.time.format.DateTimeFormatter))

;; https://ishare.eu/licenses/
(def license "ISHARE.0001") ;; FEEDBACK waarom deze "Re-sharing with Adhering Parties only"?

(defn ->carrier-delegation-target ;; TODO rename
  [consignment-ref]
  {:resource    {:type        "consignment-ref"
                 :identifiers [consignment-ref]
                 :attributes  ["*"]} ;; FEEDBACK deze resource is natuurlijk een hack
   :actions     ["BDI.PICKUP"]})

(defn local-date-time->epoch [^LocalDateTime dt]
  (-> dt
      (.atZone (ZoneId/systemDefault))
      (.toInstant)
      (.getEpochSecond)))

(defn epoch->local-date-time [^long secs]
  (-> secs
      (Instant/ofEpochSecond)
      (LocalDateTime/ofInstant (ZoneId/systemDefault))))

(defn local-date-time->str [^LocalDateTime dt]
  (.format dt DateTimeFormatter/ISO_LOCAL_DATE_TIME))

(defn epoch->str [^long secs]
  (-> secs
      (epoch->local-date-time)
      (local-date-time->str)))

(defn date->not-before-not-on-or-after [date]
  (let [date (LocalDate/parse date)
        not-before (.atStartOfDay date)
        not-on-or-after (.plusDays not-before 1)]
    {:notBefore    (local-date-time->epoch not-before)
     :notOnOrAfter (local-date-time->epoch not-on-or-after)}))

(defn ->delegation-evidence
  [{:keys [date issuer subject target]}]
  {:pre [date issuer subject target]}
  {:delegationEvidence
   (merge
    {:policyIssuer issuer
     :target       {:accessSubject subject}
     :policySets   [{:target   {:environment {:licenses [license]}}
                     :policies [{:target target
                                 :rules  [{:effect :Permit}]}]}]}
    (date->not-before-not-on-or-after date))})

(defn ->poort8-policy
  [{:keys [subject consignment-ref]}]
  {:pre [subject consignment-ref]}
  {:subjectId       subject
   :useCase         "iSHARE"
   :serviceProvider nil
   :action          "BDI.PICKUP"
   :resourceId      consignment-ref
   :type            "consignment-ref"
   :attribute       "*"
   :license         license})

(defn- mask-target
  ;; FEEDBACK waarom moet ik lege lijst van serviceProviders doorgeven voor masks?

  ;; FEEDBACK by Poort8 AR mag dit ook niet leeg zijn
  [target]
  (assoc-in target [:environment :serviceProviders] ["Dummy"]))

(defn ->delegation-mask
  [{:keys [issuer subject target]}]
  {:pre [issuer subject target]}
  {:delegationRequest
   {:policyIssuer issuer
    :target       {:accessSubject subject}
    :policySets   [{:policies [{:rules  [{:effect "Permit"}]
                                :target (mask-target target)}]}]}})

(defn permit?
  "Test if target is allowed on given Delegation Evidence."
  [delegation-evidence target]
  {:pre [delegation-evidence target]}
  (let [now (local-date-time->epoch (LocalDateTime/now))]
    (and (>= now (:notBefore delegation-evidence))
         (< now (:notOnOrAfter delegation-evidence))
         (= [{:effect "Permit"}]
            (->> delegation-evidence
                 :policySets
                 (mapcat :policies)
                 (filter #(= (:target %) (mask-target target)))
                 (mapcat :rules))))))

(defn rejection-reasons
  "Collect reasons to reject target for delegation-evidence.

  The result is a list of tuples: reason key and the offending value."
  [delegation-evidence target]
  {:pre [delegation-evidence target]}
  (let [now               (local-date-time->epoch (LocalDateTime/now))
        policies          (->> delegation-evidence
                               :policySets
                               (mapcat :policies))
        matching-policies (filter #(= (:target %) (mask-target target))
                                  policies)
        rules             (mapcat :rules matching-policies)]
    (cond-> []
      (< now (:notBefore delegation-evidence))
      (conj (str "Mag niet voor " (epoch->str (:notBefore delegation-evidence))) )

      (>= now (:notOnOrAfter delegation-evidence))
      (conj (str "Mag niet op of na " (epoch->str (:notOnOrAfter delegation-evidence))))

      (empty? matching-policies) ;; FEEDBACK: should not happen?
      (conj (str "Geen toepasbare policies gevonden: " (pr-str policies)))

      (and (seq rules)
           (not= rules [{:effect "Permit"}]))
      (conj (str "Geen toepasbare regels gevonden: " (pr-str rules)))

      :finally
      (seq))))
