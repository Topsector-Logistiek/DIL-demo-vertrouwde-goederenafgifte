;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.erp.web-test
  (:require [clojure.test :refer [deftest is testing]]
            [dil-demo.erp.web :as sut]
            [dil-demo.store :as store]
            [nl.jomco.http-status-codes :as http-status]
            [ring.mock.request :refer [request]]))

(def store
  {:consignments
   {"31415"
    {:id                  "31415"
     :ref                 "31415"}}})

(defn do-request [method path & [params]]
  ((sut/make-handler {:id :erp, :site-name "ERP"})
   (assoc (request method path params)
          ::store/store store
          :user-number 1
          :master-data {:carriers {}
                        :eori->name {}
                        :warehouses {}
                        :warehouse-addresses {}})))

(deftest handler
  (testing "GET /"
    (let [{:keys [status headers body]} (do-request :get "/")]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"\b31415\b" body))))

  (testing "GET /consignment-new"
    (let [{:keys [status headers]} (do-request :get "/consignment-new")]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "POST /consignment-new"
    (let [{:keys [status ::store/commands]} (do-request :post "/consignment-new")]
      (is (= http-status/see-other status))
      (is (= [:put! :consignments] (->> commands first (take 2))))))

  (testing "GET /consignment-not-found"
    (is (nil? (do-request :get "/consignment-not-found"))))

  (testing "GET /consignment-31415"
    (let [{:keys [status headers body]} (do-request :get "/consignment-31415")]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"\b31415\b" body))))

  (testing "POST /consignment-31415"
    (let [{:keys [status ::store/commands]} (do-request :post "/consignment-31415")]
      (is (= http-status/see-other status))
      (is (= [:put! :consignments] (->> commands first (take 2))))))

  (testing "DELETE /consignment-31415"
    (let [{:keys [status ::store/commands]} (do-request :delete "/consignment-31415")]
      (is (= http-status/see-other status))
      (is (= [:delete! :consignments] (->> commands first (take 2))))))

  (testing "GET /publish-31415"
    (let [{:keys [status headers body]} (do-request :get "/publish-31415")]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"\b31415\b" body))))

  (testing "POST /publish-31415"
    (let [{:keys [status ::store/commands]} (do-request :post "/publish-31415")]
      (is (= http-status/see-other status))
      (is (= #{:put! :publish!} (->> commands (map first) set)))))

  (testing "GET /published-31415"
    (let [{:keys [status headers body]} (do-request :get "/publish-31415")]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"\b31415\b" body)))))
