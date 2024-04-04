(ns dil-demo.ishare.jwt
  "Create, sign and unsign iSHARE JWTs

  See also: https://dev.ishareworks.org/reference/jwt.html"
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [clojure.spec.alpha :as s])
  (:import java.time.Instant
           java.util.UUID
           java.io.StringReader))

;; Data specs; these are used to validate the data shape.
;;
;; Since assertions and conditions can be disabled, public methods and
;; code directly handling external input MUST use other methods to
;; ensure input data is valid. See `check!` below.

(defn- check!
  "Check that `x` is valid for spec `spec-key`. Returns `x` if valid.

  Raises an exception if `x` is invalid. Unlike `s/assert` this cannot
  be disabled."
  [spec-key x]
  (when-let [data (s/explain-data spec-key x)]
    (throw (ex-info (s/explain-str spec-key x)
                    {:spec    spec-key
                     :x       x
                     :explain data})))
  x)


;; iSHARE JWT Header data specification

;; From https://dev.ishareworks.org/reference/jwt.html#jwt-header
;;
;;
;;  "Signed JWTs MUST use and specify the RS256 algorithm in the
;;   alg header parameter.
;;
;;   Signed JWTs MUST contain an array of the complete certificate
;;   chain that should be used for validating the JWT’s signature in
;;   the x5c header parameter up until an Issuing CA is listed from
;;   the iSHARE Trusted List.
;;
;;   Certificates MUST be formatted as base64 encoded PEM.
;;
;;   The certificate of the client MUST be the first in the array, the
;;   root certificate MUST be the last.
;;
;;   Except from the alg, typ and x5c parameter, the JWT header SHALL
;;   NOT contain other header parameters."

(s/def ::typ #{"JWT"})
(s/def ::alg #{:rs256})
(s/def ::base64-str
  (s/and string?
         #(re-matches #"[A-Za-z0-9\+/=]+" %)))
(s/def ::cert-str ::base64-str)
(s/def ::x5c (s/coll-of ::cert-str :kind vector? :min-count 1))

(defn- no-additional-keys?
  "True if m has no keys but the keys in `ks`"
  [m ks]
  (and (map? m)
       (every? (set ks) (keys m))))

(s/def ::header
  (s/and
     (s/keys :req-un [::typ ::alg ::x5c])
     #(no-additional-keys? % [:typ :alg :x5c])))


;; iSHARE JWT payload data specs
;;
;; From https://dev.ishareworks.org/reference/jwt.html#jwt-payload
;;
;;   "The JWT payload MUST conform to the private_key_jwt method as
;;    specified in OpenID Connect 1.0 Chapter 9.
;;
;;    The JWT MUST always contain the iat claim.
;;
;;    The iss and sub claims MUST contain the valid iSHARE
;;    identifier (EORI) of the client.
;;
;;    The aud claim MUST contain only the valid iSHARE identifier of
;;    the server. Including multiple audiences creates a risk of
;;    impersonation and is therefore not allowed.
;;
;;    The JWT MUST be set to expire in 30 seconds. The combination of
;;    iat and exp claims MUST reflect that. Both iat and exp MUST be
;;    in seconds, NOT milliseconds. See UTC Time formatting for
;;    requirements.
;;
;;    The JWT MUST contain the jti claim for audit trail purposes. The
;;    jti is not necessary a GUID/UUID.
;;
;;     Depending on the use of the JWT other JWT payload data MAY be
;;     defined."

(s/def ::signed-token
  (s/and string?
         seq))

(s/def ::timestamp-seconds
  integer?)
(s/def ::iat ::timestamp-seconds)
(s/def ::exp ::timestamp-seconds)
(s/def ::nbf ::timestamp-seconds)

(s/def ::ishare-identifier
  (s/and string?
         #(re-matches #"EU\.EORI\..*" %)))

(s/def ::iss ::ishare-identifier)
(s/def ::sub ::ishare-identifier)
(s/def ::aud ::ishare-identifier)

(s/def ::jti (s/and string? seq))

(defn expires-in-30-seconds?
  [{:keys [iat exp]}]
  (= 30 (- exp iat)))

(defn nbf-equal-to-iat?
  [{:keys [nbf iat] :as payload}]
  ;; nbf is optional, only do the check if nbf is present
  (when (contains? payload :nbf)
    (= nbf iat)))

(defn iss-equal-to-sub?
  [{:keys [iss sub] :as payload}]
  (= iss sub))

(s/def ::payload
  (s/and (s/keys :req-un [::iss ::sub ::aud ::jti ::iat ::exp]
                 :opt-un [::nbf])
         expires-in-30-seconds?
         nbf-equal-to-iat?
         iss-equal-to-sub?))


;; Parsing and validating iSHARE JWTs

(s/fdef cert-reader :args (s/cat :cert-str ::cert-str))

(defn- cert-reader
  "Convert base64 encoded certificate string into a reader for parsing
  as a PEM."
  [cert-str]
  ;; TODO: validate cert-str format
  (StringReader. (str "-----BEGIN CERTIFICATE-----\n"
                      cert-str
                      "\n-----END CERTIFICATE-----\n")))

(defn unsign-token
  "Parse a signed token. Returns parsed data or raises exception.

  Raises exception when token is not a valid iSHARE JWT for any
  reason, including expiration."
  [token]
  (check! ::signed-token token)
  (let [{:keys [x5c]} (check! ::header (jwt/decode-header token))
        cert-str      (first x5c)
        pkey          (keys/public-key (cert-reader cert-str))]
    (check! ::payload (jwt/unsign token pkey {:alg :rs256 :leeway 5}))))


;; Creating iSHARE JWTs

(defn- seconds-since-unix-epoch
  "Current number of seconds since the UNIX epoch."
  []
  (.getEpochSecond (Instant/now)))

(defn make-client-assertion
  "Create a signed client assertion for requesting an access token.

  The client assertion will be valid for 30 seconds."
  [{:ishare/keys [client-id server-id x5c private-key]}]
  {:pre [client-id server-id x5c private-key]}
  (let [iat (seconds-since-unix-epoch)
        exp (+ iat 30)]
    (jwt/sign {:iss client-id
               :sub client-id
               :aud server-id
               :jti (UUID/randomUUID)
               :iat iat
               :nbf iat ;; nbf is not required according to the spec,
               ;; but the Poort8 AR used to require it
               :exp exp}
              private-key
              {:alg    :rs256
               :header {:x5c x5c
                        :typ "JWT"}})))
