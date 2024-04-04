(ns dil-demo.ishare.policies)

;; https://ishare.eu/licenses/
(def licenses ["0001"]) ;; FEEDBACK waarom deze "Re-sharing with Adhering Parties only"?

(defn ->carrier-delegation-target
  [consignment-ref]
  {:resource    {:type        "consignment-ref"
                 :identifiers [consignment-ref]
                 :attributes  ["*"]} ;; FEEDBACK deze resource is natuurlijk een hack
   :actions     ["BDI.PICKUP"]})

(defn ->delegation-evidence
  [{:keys [issuer subject target]}]
  {:pre [issuer subject target]}
  {:delegationEvidence
   {:notBefore    0 ;; TODO
    :notOnOrAfter 0 ;; TODO
    :policyIssuer issuer
    :target       {:accessSubject subject}
    :policySets [{:target   {:environment {:licenses licenses}}
                  :policies [{:target target
                              :rules  [{:effect :Permit}]}]}]}})

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
  ;; TODO test notBefore / notOnOrAfter
  (= [{:effect "Permit"}]
     (->> delegation-evidence
          :policySets
          (mapcat :policies)
          (filter #(= (:target %) (mask-target target)))
          (mapcat :rules))))
