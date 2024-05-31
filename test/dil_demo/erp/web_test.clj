;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
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
   {"some-id"
    {:id "some-id"
     :external-attributes {:ref "some-ref"}}}})

(def sut-handler (sut/make-handler {:id :erp, :site-name "ERP"}))

(deftest handler
  (testing "/"
    (let [{:keys [status headers]} (sut-handler (request :get "/"))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "/consignment-new"
    (let [{:keys [status headers]} (-> :get
                                       (request "/consignment-new")
                                       (assoc :user-number 1)
                                       (sut-handler))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "/consignment-not-found"
    (is (nil? (sut-handler (request :get "/consignment-not-found")))))

  (testing "/consignment-some-id"
    (let [{:keys [status headers body]}
          (sut-handler (assoc (request :get "/consignment-some-id")
                              ::store/store store))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"<input [^>]*\bvalue=\"some-ref\"[^>]*>" body)))))
