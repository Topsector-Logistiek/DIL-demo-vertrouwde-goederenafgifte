;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.ishare.data-source
  (:require [clojure.walk :as walk]
            [dil-demo.ishare.client :as client]
            [org.bdinetwork.assocation-register.data-source :as data-source]))

(deftype RemoteDataSource [client-data] data-source/DataSourceProtocol
  (party [_ eori]
    (-> client-data
        (assoc :ishare/message-type :party, :ishare/party-id eori)
        (client/exec)
        :ishare/result
        :party_info
        (walk/stringify-keys))))

(defn ishare-client-data-source-factory
  "Create an iSHARE client backed data source."
  [{:ishare/keys [client-id x5c private-key satellite-id satellite-endpoint]
    :as client-data}]
  {:pre [client-id x5c private-key satellite-id satellite-endpoint]}
  (RemoteDataSource. client-data))

(comment
  (def client-data
    {:ishare/client-id   "EU.EORI.NLSMARTPHON"
     :ishare/x5c         (client/x5c "credentials/EU.EORI.NLSMARTPHON.crt")
     :ishare/private-key (client/private-key "credentials/EU.EORI.NLSMARTPHON.pem")

     ;; context
     :ishare/satellite-id (System/getenv "SATELLITE_ID")
     :ishare/satellite-endpoint (System/getenv "SATELLITE_ENDPOINT")})

  (let [ds (ishare-client-data-source-factory client-data)]
    (data-source/party ds "EU.EORI.NLSECURESTO")))
