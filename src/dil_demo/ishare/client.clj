(ns dil-demo.ishare.client
  (:require [babashka.http-client :as http]
            [babashka.http-client.interceptors :as interceptors]
            [babashka.json :as json]
            [buddy.core.keys :as keys]
            [clojure.string :as string]
            [dil-demo.ishare.jwt :as jwt])
  (:import (java.net URI)))

(defn private-key
  "Read private key from file."
  [key-file]
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

(defn x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(def unsign-token-interceptor
  {:name     ::unsign-token
   :description
   "A request with `:ishare/unsign prop` will unsign the jwt under `prop` in response body"
   :response (fn [response]
               (let [k (get-in response [:request :ishare/unsign-token])]
                 (if (and k (get-in response [:body k]))
                   (update-in response [:body k] jwt/unsign-token)
                   response)))})

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
  {:name    ::fetch-bearer-token
   :doc     "When request has no :ishare/bearer-token, fetch it from the endpoint.
When bearer token is not needed, provide a `nil` token"
   :request (fn [request]
              (if (contains? request :ishare/bearer-token)
                request
                (let [response (-> request
                                   (select-keys [:ishare/endpoint
                                                 :ishare/client-id
                                                 :ishare/server-id
                                                 :ishare/x5c
                                                 :ishare/private-key])
                                   (assoc :ishare/message-type :access-token)
                                   exec)
                      token (:ishare/result response)]
                  (when-not token
                    ;; FEEDBACK: bij invalid client op /token komt 202 status terug?
                    (throw (ex-info "Error fetching access token" {:response response})))
                  (assoc request
                         :ishare/bearer-token token))))})

(def lens-interceptor
  {:name     ::lens
   :description
   "If request contains :ishare/lens path, put the object at path in
   reponse, under :ishare/result"
   :response (fn [response]
               (if-let [path (get-in response [:request :ishare/lens])]
                 (assoc response :ishare/result (get-in response path))
                 response))})

(def ^:dynamic log-interceptor-atom nil)

(def log-interceptor
  {:name     ::log
   :response (fn [r]
               (when log-interceptor-atom
                 (swap! log-interceptor-atom conj r))
               r)})

(defn resolve-uri [endpoint path]
  (let [endpoint (if (string/ends-with? endpoint "/")
                   endpoint
                   (str endpoint "/"))]
    (-> endpoint
        (URI.)
        (.resolve (URI. path))
        (.normalize)
        (str))))

(def build-uri-interceptor
  {:name ::build-uri-interceptor
   :request (fn [{:keys [path ishare/endpoint] :as request}]
              (if (and path endpoint)
                (assoc request :uri (resolve-uri endpoint path))
                request))})

(defmulti ishare->http-request
  :ishare/message-type)

(def ishare-interpreter-interactor
  {:name ::ishare-interpretor-interactor
   :request ishare->http-request})

(def interceptors
  [ishare-interpreter-interactor
   interceptors/throw-on-exceptional-status-code
   log-interceptor
   lens-interceptor
   unsign-token-interceptor
   build-uri-interceptor
   fetch-bearer-token-interceptor
   bearer-token-interceptor
   interceptors/construct-uri
   interceptors/accept-header
   interceptors/query-params
   interceptors/form-params
   json-interceptor ;; should be between decode-body and
   ;; throw-on-exceptional-status-code, so that JSON
   ;; error messages are decoded
   interceptors/decode-body
   interceptors/decompress-body])

(defn exec
  [request]
  (http/request (assoc request
                       :interceptors interceptors)))



(defmethod ishare->http-request :access-token
  [{:ishare/keys [client-id] :as request}]
  {:pre [client-id]}
  (assoc request
         :path          "connect/token"
         :method       :post
         :as           :json
         :ishare/bearer-token nil
         :form-params  {"client_id"             client-id
                        "grant_type"            "client_credentials"
                        "scope"                 "iSHARE" ;; TODO: allow restricting scope?
                        "client_assertion_type" "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                        "client_assertion"      (jwt/make-client-assertion request)}
         ;; NOTE: body includes expiry information, which we could use
         ;; for automatic caching -- check with iSHARE docs to see if
         ;; that's always available
         :ishare/lens          [:body "access_token"]))

;; Parties is a satellite endpoint; response will be signed by the
;; satellite, and we cannot use `/parties` endpoint to validate the
;; signature of the `/parties` request.

(defmethod ishare->http-request :parties
  [{:ishare/keys [params] :as request}]
  (assoc request
         :method       :get
         :path          "parties"
         :as           :json
         :query-params  params
         :ishare/unsign-token "parties_token"
         ;; NOTE: pagination to be implemented
         :ishare/lens   [:body "parties_token"]))

(defmethod ishare->http-request :party
  [{:ishare/keys [party-id] :as request}]
  (assoc request
         :method       :get
         :path         (str "parties/" party-id)
         :as           :json
         :ishare/unsign-token "party_token"
         :ishare/lens [:body "party_token"]))

(defmethod ishare->http-request :trusted-list
  [request]
  (assoc request
         :method       :get
         :path         "trusted_list"
         :as           :json
         :ishare/unsign-token "trusted_list_token"
         :ishare/lens         [:body "trusted_list_token"]))

(defmethod ishare->http-request :capabilities
  [request]
  (assoc request
         :method       :get
         :path         "capabilities"
         :as           :json
         :ishare/unsign-token "capabilities_token"
         :ishare/lens         [:body "capabilities_token"]))

(defmethod ishare->http-request :ishare/policy ;; ishare AR specific
  [{delegation-evidence :ishare/params :as request}]
  {:pre [delegation-evidence]}
  (assoc request
         :method       :post
         :path         "policy"
         :as           :json
         :json-params  delegation-evidence
         :ishare/unsign-token "policy_token"
         :ishare/lens         [:body "policy_token"]))

(defmethod ishare->http-request :poort8/policy ;; Poort8 AR specific
  [{params :ishare/params :as request}]
  (assoc request
         :method :post
         :path "../policies"
         :as :json
         :json-params (assoc params
                             :useCase "iSHARE")
         :ishare/lens [:body]))

(defmethod ishare->http-request :poort8/delete-policy ;; Poort8 AR specific
  [{params :ishare/params :as request}]
  (assoc request
         :method :delete
         :path (str "../policies/" (:policyId params))
         :as :json
         :ishare/lens [:body]))

(defmethod ishare->http-request :delegation
  [{delegation-mask :ishare/params :as request}]
  {:pre [delegation-mask]}
  (assoc request
         :method       :post
         :path         "delegation"
         :as           :json
         :json-params  delegation-mask
         :ishare/unsign-token "delegation_token"
         :ishare/lens         [:body "delegation_token"]))



(defn satellite-request [request]
  (assoc request
         :ishare/endpoint    "https://dilsat1-mw.pg.bdinetwork.org/"
         :ishare/server-id   "EU.EORI.NLDILSATTEST1"))

(defn satellite-party [client-data party-id]
  (-> client-data
      (satellite-request)
      (assoc :ishare/party-id party-id)
      (assoc :ishare/message-type :party)
      exec
      :ishare/result))

;; TODO: use ishare/namespace keys for args
;; TODO: turn into interceptor
(defn satellite-party-authorisation-registry
  [{:ishare/keys [dataspace-id] :as client-data} party-id]
  {:pre [dataspace-id]}
  (when-let [{:keys [authorizationRegistryName
                     authorizationRegistryID
                     authorizationRegistryUrl
                     dataspaceID
                     dataspaceName]}
             (->> (-> client-data
                      (satellite-party party-id)
                      :party_info
                      :authregistery)
                  (filter #(= dataspace-id (:dataspaceID %)))
                  first)]
    {:ishare/server-id      authorizationRegistryID
     :ishare/server-name    authorizationRegistryName
     :ishare/endpoint       authorizationRegistryUrl}))

;; TODO merge client-data and args
(defn ar-delegation-evidence
  "Get Delegation Evidence from AR of given party associated with
  dataspace-id using delegation-mask."
  [client-data delegation-mask {:keys [party-eori]}]
  {:pre [client-data party-eori delegation-mask (:ishare/dataspace-id client-data)]}
  ;; TODO: turn this into interceptor
  (let [ar-info (satellite-party-authorisation-registry client-data
                                                        party-eori)]
    ;; FEEDBACK: als subject eori niet bekend wordt er een 404 teruggegeven!?
    (-> client-data
        (merge ar-info)
        (assoc :ishare/message-type :delegation
               :ishare/params delegation-mask)
        exec
        :ishare/result
        :delegationEvidence)))



(defn ->client-data [{:keys [eori key-file chain-file dataspace-id]}]
  {:ishare/client-id    eori
   :ishare/dataspace-id dataspace-id
   :ishare/private-key  (private-key key-file)
   :ishare/x5c          (x5c chain-file)})

(defn wrap-client-data
  [app config]
  (let [client-data (->client-data config)]
    (fn client-data-wrapper [req]
      (app (assoc req :client-data client-data)))))



(comment
  (def client-data
    {:ishare/client-id   "EU.EORI.NLSMARTPHON"
     :ishare/x5c         (x5c "credentials/EU.EORI.NLSMARTPHON.crt")
     :ishare/private-key (private-key "credentials/EU.EORI.NLSMARTPHON.pem")})

  (def ishare-ar-request
    {:ishare/endpoint    "https://ar.isharetest.net/"
     :ishare/server-id   "EU.EORI.NL000000004"
     :ishare/client-id   "EU.EORI.NLSMARTPHON"
     :ishare/x5c         (x5c "credentials/EU.EORI.NLSMARTPHON.crt")
     :ishare/private-key (private-key "credentials/EU.EORI.NLSMARTPHON.pem")})

  (def poort8-ar-request
    {:ishare/endpoint    "https://tsl-ishare-dataspace-coremanager-preview.azurewebsites.net/api/ishare/"
     :ishare/server-id   "EU.EORI.NLP8TSLAR1"
     :ishare/client-id   "EU.EORI.NLPRECIOUSG"
     :ishare/x5c         (x5c "credentials/EU.EORI.NLPRECIOUSG.crt")
     :ishare/private-key (private-key "credentials/EU.EORI.NLPRECIOUSG.pem")})

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

  (-> client-data
      (satellite-request)
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

  (satellite-party-authorisation-registry client-data "EU.EORI.NLSMARTPHON" "NLDILDEMOGOEDEREN"))
