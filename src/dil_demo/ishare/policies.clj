(ns dil-demo.ishare.policies
  (:import (java.time LocalDate LocalDateTime ZoneId)))

;; https://ishare.eu/licenses/
(def license "ISHARE.0001") ;; FEEDBACK waarom deze "Re-sharing with Adhering Parties only"?

(defn ->carrier-delegation-target
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
  [target]
  (assoc-in target [:environment :serviceProviders] []))

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
