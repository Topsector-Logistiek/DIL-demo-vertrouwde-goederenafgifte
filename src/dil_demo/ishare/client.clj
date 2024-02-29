(ns dil-demo.ishare.client
  (:require [babashka.http-client :as http]
            [babashka.http-client.interceptors :as interceptors]
            [babashka.json :as json]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import java.time.Instant
           java.util.UUID
           java.io.StringReader
           org.bouncycastle.openssl.PEMParser))

;; key-file is a PEM file with private key and full certificate chains
;;
;; you can create one using

;; /usr/bin/openssl pkcs12 -in XX.p12 -nocerts -nodes -out XX.pem
;;
(def key-file "credentials/EU.EORI.NLSMARTPHON.pem")

(def private-key
  (keys/private-key key-file))


;; From https://dev.ishareworks.org/reference/jwt.html#refjwt
;;
;;  "Signed JWTs MUST contain an array of the complete certificate
;;   chain that should be used for validating the JWT’s signature in
;;   the x5c header parameter up until an Issuing CA is listed from
;;   the iSHARE Trusted List."
;;
;; Does this mean we don't need to include the trusted CAs in the x5c
;; chain?
;;
;; FIXME: This assumes that certificates are in order and there is no
;; other item (like private key) in the middle
;;
;; /usr/bin/openssl pkcs12 -in XX.p12 -chain -nokeys -out XX.crt

(def cert-file "credentials/EU.EORI.NLSMARTPHON.crt")

(defn- repeat-some
  "Returns a lazy sequence of calls to `f`, terminating when item is nil."
  [f]
  (take-while some? (repeatedly f)))

(defn certificate-chain
  "Reads a certificate chain from a PEM encoded file or stream"
  [x]
  (with-open [reader (io/reader x)]
    (let [parser (PEMParser. reader)]
      (into [] (repeat-some #(.readObject parser))))))

(def x5c (->> (-> cert-file
                  slurp
                  (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
                  (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
                  (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
              (mapv #(string/replace % #"\s+" ""))))

(defn seconds-since-unix-epoch
  []
  (.getEpochSecond (Instant/now)))

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
;;
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


(defn make-assertion
  [{:ishare/keys [client-id server-id x5c private-key]}]
  {:pre [client-id server-id x5c private-key]}
  (let [iat (seconds-since-unix-epoch)
        exp (+ 30 iat)]
    (jwt/sign {:iss  client-id
               :sub  client-id
               :aud server-id
               :jti (UUID/randomUUID)
               :iat iat
               :exp exp}
              private-key
              {:alg    :rs256
               :header {:x5c x5c
                        :typ "JWT"}})))

(defn cert-reader
  "Convert base64 encoded certificate string into a reader for parsing
  as a PEM."
  [cert-str]
  (StringReader. (str "-----BEGIN CERTIFICATE-----\n"
                      cert-str
                      "\n-----END CERTIFICATE-----\n")))

(defn unsign-token
  "Parse a signed token"
  [token]
  {:pre [token]}
  (let [header   (jwt/decode-header token)
        cert-str (first (:x5c header))
        k        (keys/public-key (cert-reader cert-str))]
    (jwt/unsign token k {:alg :rs256 :leeway 5})))

(def unsign-token-interceptor
  {:name     ::unsign-token
   :description
   "A request with `:ishare/unsign prop` will unsign the jwt under `prop` in response body"
   :response (fn [response]
               (if-let [k (get-in response [:request :ishare/unsign-token])]
                 (update-in response [:body k] unsign-token)
                 response))})

(defn json-response?
  [response]
  (when-let [type (get-in response [:headers "content-type"])]
    (string/starts-with? type "application/json")))

(def json-interceptor
  {:name     ::json
   :description
   "A request with `:as :json` will automatically get the
   \"application/json\" accept header and the response is decoded as JSON.

    When :json-params is present in the request, an
    \"application/json\" content-type header is added and the
    json-params will be serialized as JSON and used as the request
    body."
   :request  (fn [{:keys [as json-params] :as request}]
               (cond-> request
                 (= :json as)
                 (-> (assoc-in [:headers :accept] "application/json")
                     ;; Read body as :string
                     ;; Mark request as amenable to json decoding
                     (assoc :as :string ::json true))

                 (contains? request :json-params) ;; use contains? to support `:json-params nil`
                 (-> (assoc-in [:headers "content-type"] "application/json")
                     (assoc :body (json/write-str json-params)))))
   :response (fn [response]
               (if (and (get-in response [:request ::json])
                        (json-response? response))
                 (update response :body #(json/read-str % {:key-fn identity}))
                 response))})

(def bearer-token-interceptor
  {:name     ::bearer-token
   :description
   "A request with a non-nil `:ishare/bearer-token` will get an Authorization
   header for the bearer token added."
   :request  (fn [{:ishare/keys [bearer-token] :as request}]
               (if bearer-token
                 (assoc-in request [:headers "Authorization"] (str "Bearer " bearer-token))
                 request))})

(declare exec)

(def fetch-bearer-token-interceptor
  {:name ::fetch-bearer-token
   :doc "When request has no :ishare/bearer-token, fetch it from the endpoint.
When bearer token is not needed, provide a `nil` token"
   :request (fn [request]
              (if (contains? request :ishare/bearer-token)
                request
                (assoc request
                       :ishare/bearer-token (-> request
                                                (select-keys [:ishare/endpoint :ishare/client-id
                                                              :ishare/server-id :ishare/x5c
                                                              :ishare/private-key])
                                                (assoc :ishare/message-type :access-token)
                                                exec
                                                :ishare/result))))})

(def lens-interceptor
  {:name     ::lens
   :description
   "If request contains :ishare/lens path, put the object at path in
   reponse, under :ishare/result"
   :response (fn [response]
               (if-let [path (get-in response [:request :ishare/lens])]
                 (assoc response :ishare/result (get-in response path))))})

(def log-interceptor
  {:name ::log
   :response (fn [r]
               (prn (:headers (:request r)))
               (prn (:headers r))
               (prn (:body r))
               r)})

(def build-uri-interceptor
  {:name ::build-uri-interceptor
   :request (fn [{:keys [path ishare/endpoint] :as request}]
              (if (and path endpoint)
                (assoc request :uri (str endpoint path))
                request))})

(defmulti ishare->http-request
  :ishare/message-type)

(def ishare-interpreter-interactor
  {:name ::ishare-interpretor-interactor
   :request ishare->http-request})

(def interceptors
  [ishare-interpreter-interactor
   lens-interceptor
   unsign-token-interceptor ;; above throw, so we don't try to decode an
   ;; error response
   build-uri-interceptor
   fetch-bearer-token-interceptor
   bearer-token-interceptor
   interceptors/throw-on-exceptional-status-code
   interceptors/construct-uri
   interceptors/accept-header
   interceptors/basic-auth ;; TODO: remove?
   interceptors/query-params
   interceptors/form-params
   interceptors/multipart ;; TODO: remove?
   json-interceptor       ;; should be between decode-body and
   ;; throw-on-exceptional-status-code, so that JSON
   ;; error messages are decoded
   interceptors/decode-body
   interceptors/decompress-body])

(defn exec
  [request]
  (http/request (assoc request
                       :interceptors interceptors)))

(defn exec-result
  [request]
  (-> request
      exec
      :ishare/result))

(def satellite-request
  {:ishare/endpoint    "https://dilsat1-mw.pg.bdinetwork.org"
   :ishare/server-id   "EU.EORI.NLDILSATTEST1"
   :ishare/client-id   "EU.EORI.NLSMARTPHON"
   :ishare/x5c         x5c
   :ishare/private-key private-key})

(def ishare-ar-request
  {:ishare/endpoint    "https://ar.isharetest.net/"
   :ishare/server-id   "EU.EORI.NL000000004"
   :ishare/client-id   "EU.EORI.NLSMARTPHON"
   :ishare/x5c         x5c
   :ishare/private-key private-key})



(defmethod ishare->http-request :default
  [request]
  (println "Warning: unknown message passed")
  request)

(defmethod ishare->http-request :access-token
  [{:ishare/keys [client-id] :as request}]
  {:pre [client-id]}
  (assoc request
         :path          "/connect/token"
         :method       :post
         :as           :json
         :ishare/bearer-token nil
         :form-params  {"client_id"             client-id
                        "grant_type"            "client_credentials"
                        "scope"                 "iSHARE" ;; TODO: allow restricting scope?
                        "client_assertion_type" "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                        "client_assertion"      (make-assertion request)}
         ;; NOTE: body includes expiry information, which we could use
         ;; for automatic caching -- check with iSHARE docs to see if
         ;; that's always available
         :ishare/lens          [:body "access_token"]))

;; Parties is a satellite endpoint; response will be signed by the
;; satellite, and we cannot use `/parties` endpoint to validate the
;; signature of the `/parties` request.

;; TODO: hoe komen we bij een AR van de verlader?
;; Wat als er heel veel ARs zijn (meerdere per party)?

;; TODO: ishare/namespace keys
;; TODO: multimethod ishare requests -> http requests

(defmethod ishare->http-request :parties
  [{:ishare/keys [params] :as request}]
  (assoc request
         :method       :get
         :path          "/parties"
         :as           :json
         :query-params  params
         :ishare/unsign-token "parties_token"
         ;; NOTE: pagination to be implemented
         :ishare/lens   [:body "parties_token"]))

(defmethod ishare->http-request :trusted-list
  [request]
  (assoc request
         :method       :get
         :path         "/trusted_list"
         :as           :json
         :ishare/unsign-token "trusted_list_token"
         :ishare/lens         [:body "trusted_list_token"]))

(defmethod ishare->http-request :capabilities
  [request]
  (assoc request
         :method       :get
         :path         "/capabilities"
         :as           :json
         :ishare/unsign-token "capabilities_token"
         :ishare/lens         [:body "capabilities_token"]))



(defmethod ishare->http-request :ishare/policy ;; ishare AR specific
  [{delegation-evidence :ishare/params :as request}]
  {:pre [delegation-evidence]}
  (assoc request
         :method       :post
         :path         "/policy"
         :as           :json
         :json-params  delegation-evidence
         :ishare/unsign-token "policy_token"
         :ishare/lens         [:body "policy_token"]))


(defmethod ishare->http-request :delegation
  [{delegation-mask :ishare/params :as request}]
  {:pre [delegation-mask]}
  (assoc request
         :method       :post
         :path         "/delegation"
         :as           :json
         :json-params  delegation-mask
         :ishare/unsign-token "delegation_token"
         :ishare/lens         [:body "delegation_token"]))

(comment


  (def delegation-evidence
    {"delegationEvidence"
     {"notBefore"    0
      "notOnOrAfter" 0
      "policyIssuer" "EU.EORI.NLSMARTPHON"
      "target"       {"accessSubject" "EU.EORI.NLPRECIOUSG"}
      "policySets"   [{"policies" [{"rules"  [{"effect" "Permit"}]
                                    "target" {"resource" {"type"        "klantordernummer"
                                                          "identifiers" ["112233"]
                                                          "attributes"  ["*"]}
                                              "actions"  ["BDI.PICKUP"]}}]
                       "target"   {"resource" {"type"        "klantordernummer"
                                               "identifiers" ["112233"]
                                               "attributes"  ["*"]}
                                   "actions"  ["BDI.PICKUP"]
                                   ;; `licenses` is required, but seems
                                   ;; to fit pretty badly, let's go
                                   ;; for "0001" -- "Re-sharing with
                                   ;; Adhering Parties only"
                                   "environment" {"licenses" ["0001"]}}}]}})

  (def delegation-mask
    {:delegationRequest
     {:policyIssuer "EU.EORI.NLSMARTPHON"
      :target       {:accessSubject "EU.EORI.NLPRECIOUSG"}
      :policySets   [{:policies [{:rules  [{:effect "Permit"}]
                                  :target {:resource    {:type        "klantordernummer"
                                                         :identifiers ["112233"]
                                                         :attributes  ["*"]}
                                           :actions     ["BDI.PICKUP"]
                                           :environment {:licenses ["0001"]
                                                         :serviceProviders []}}}]}]}})

  (-> satellite-request
      (assoc :ishare/message-type :access-token)
      exec
      :ishare/result)

  (-> ishare-ar-request
      (assoc :ishare/message-type :ishare/policy ;; ishare-ar specific call
             :ishare/params delegation-evidence)
      exec
      :ishare/result)

  (-> ishare-ar-request
      (assoc :ishare/message-type :delegation ;; standardized call
             :ishare/params delegation-mask)
      exec
      :ishare/result)
  )
