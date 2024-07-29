;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.wms.events-test
  (:require [clojure.test :refer [deftest is testing]]
            [dil-demo.http-utils :as http-utils]
            [dil-demo.store :as store]
            [dil-demo.wms.events :as sut]
            [nl.jomco.http-status-codes :as http-status]
            [ring.mock.request :refer [request]]))

(def eori "EU.EORI.TEST")
(def other-eori "EU.EORI.OTHER")

(def store
  {:events
   {"31415"
    {:id           "31415"
     :body         "it happened"
     :content-type "text/plain"
     :targets      #{eori}}}})

(def ^:dynamic *client-id* nil)

(defn do-request [method path]
  (-> (request method path)
      (assoc
       ::store/store store
       :user-number 1
       :app-id :wms
       :base-uri "/base/uri"
       :client-id *client-id*
       :eori eori)
      (sut/handler)))

(deftest handler
  (testing "GET /"
    (is (nil? (do-request :get "/"))))

  (testing "GET /event/31415"
    (testing "without authentication"
      (let [{:keys [status headers]} (do-request :get "/event/31415")]
        (is (= http-status/unauthorized status))
        (is (= ["Bearer"
                {"scope"                 "iSHARE"
                 "server_eori"           eori
                 "server_token_endpoint" "/base/uri/connect/token"}]
               (-> headers
                   (get "WWW-Authenticate")
                   (http-utils/parse-www-authenticate))))))

    (testing "with authentication"
      (binding [*client-id* eori]
        (let [{:keys [status headers body]} (do-request :get "/event/31415")]
          (is (= http-status/ok status))
          (is (= "text/plain" (get headers "Content-Type")))
          (is (= "it happened" body)))))

    (testing "with authentication but wrong party"
      (binding [*client-id* other-eori]
        (let [{:keys [status]} (do-request :get "/event/31415")]
          (is (= http-status/forbidden status)))))))