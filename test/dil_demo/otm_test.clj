;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.otm-test
  (:require [clojure.test :refer [deftest is testing]]
            [dil-demo.otm :as sut]))

(def test-consignment
  {:id                  "0"
   :external-attributes {:ref "test-ref"}
   :status              sut/status-draft

   :goods
   [{:association-type sut/association-type-inline
     :entity           {:description "test-goods"}}]

   :actors
   [{:association-type sut/association-type-inline
     :roles            #{sut/role-carrier}
     :entity
     {:contact-details [{:type  sut/contact-details-type-eori
                         :value "EU.EORI.CARRIER"}]}}
    {:association-type sut/association-type-inline
     :roles            #{sut/role-owner}
     :entity
     {:contact-details [{:type  sut/contact-details-type-eori
                         :value "EU.EORI.OWNER"}]}}]

   :actions
   [{:association-type sut/association-type-inline
     :entity
     {:action-type sut/action-type-load
      :start-time  "2000-01-01T00:00"
      :location    {:association-type sut/association-type-inline
                    :entity           {:name          "test-load-name"
                                       :type          sut/location-type-warehouse
                                       :geo-reference {}}}
      :remarks     "test-load-remarks"}}
    {:association-type sut/association-type-inline
     :entity
     {:action-type sut/action-type-unload
      :start-time  "2000-01-01T23:59"
      :location    {:association-type sut/association-type-inline
                    :entity           {:name          "test-unload-name"
                                       :type          sut/location-type-warehouse
                                       :geo-reference {}}}
      :remarks     "test-unload-remarks"}}]})

(deftest consigment
  (testing "status"
    (is (= sut/status-draft (sut/consignment-status test-consignment)))
    (is (= sut/status-cancelled
           (-> test-consignment
               (sut/consignment-status! sut/status-cancelled)
               (sut/consignment-status)))))

  (testing "actions"
    (is (= "2000-01-01T00:00" (sut/consignment-load-date test-consignment)))
    (is (= "test-load-name" (sut/consignment-load-location test-consignment)))
    (is (= "test-load-remarks" (sut/consignment-load-remarks test-consignment)))
    (is (= "2000-01-01T23:59" (sut/consignment-unload-date test-consignment)))
    (is (= "test-unload-name" (sut/consignment-unload-location test-consignment)))
    (is (= "test-unload-remarks" (sut/consignment-unload-remarks test-consignment))))

  (testing "actors"
    (is (= "EU.EORI.CARRIER" (sut/consignment-carrier-eori test-consignment)))
    (is (= "EU.EORI.OWNER" (sut/consignment-owner-eori test-consignment))))

  (testing "->map"
    (is (= {:id              "0"
            :status          "draft"
            :ref             "test-ref"
            :load-date       "2000-01-01T00:00"
            :load-location   "test-load-name"
            :load-remarks    "test-load-remarks"
            :unload-date     "2000-01-01T23:59"
            :unload-location "test-unload-name"
            :unload-remarks  "test-unload-remarks"
            :goods           "test-goods"
            :carrier-eori    "EU.EORI.CARRIER"
            :owner-eori      "EU.EORI.OWNER"}
           (sut/consignment->map test-consignment)))))

(def test-transport-order
  {:id "0"
   :consignments [{:association-type sut/association-type-inline
                   :entity
                   {:id "0"
                    :external-attributes {:ref "test-ref"}
                    :status sut/status-draft
                    :goods
                    [{:association-type sut/association-type-inline
                      :entity
                      {:description "test-goods"}}]
                    :actors
                    [{:association-type sut/association-type-inline
                      :roles #{sut/role-owner}
                      :entity
                      {:contact-details [{:type sut/contact-details-type-eori
                                          :value "EU.EORI.OWNER"}]}}]
                    :actions
                    [{:association-type sut/association-type-inline
                      :entity {:action-type sut/action-type-load
                               :start-time "2000-01-01T00:00"
                               :location {:association-type sut/association-type-inline
                                          :entity
                                          {:name "test-load-name"
                                           :type sut/location-type-warehouse
                                           :geo-reference {}}}
                               :remarks "test-load-remarks"}}]}}]})

(deftest transport-order
  (testing "->map"
    (is (= {:id "0"
            :status "draft"
            :ref "test-ref"
            :load-date "2000-01-01T00:00"
            :load-location "test-load-name"
            :load-remarks "test-load-remarks"
            :goods "test-goods"
            :owner-eori "EU.EORI.OWNER"}
           (sut/transport-order->map test-transport-order)))))

(def test-trip
  {:id                  "0"
   :external-attributes {:consignment-ref "test-ref"}
   :status              sut/status-draft

   :vehicle
   [{:association-type sut/association-type-inline
     :entity           {:license-plate "AB12CDE"}}]

   :actors
   [{:association-type sut/association-type-inline
     :roles            #{sut/role-carrier}
     :entity
     {:contact-details [{:type  sut/contact-details-type-eori
                         :value "EU.EORI.CARRIER"}]}}

    {:association-type sut/association-type-inline
     :roles            #{sut/role-driver}
     :entity
     {:external-attributes {:id-digits "3141"}}}]

   :actions
   [{:association-type sut/association-type-inline
     :entity
     {:action-type sut/action-type-load
      :start-time  "2000-01-01T00:00"
      :location    {:association-type sut/association-type-inline
                    :entity           {:name          "test-load-name"
                                       :type          sut/location-type-warehouse
                                       :geo-reference {}}}
      :remarks     "test-load-remarks"}}
    {:association-type sut/association-type-inline
     :entity
     {:action-type sut/action-type-unload
      :start-time  "2000-01-01T23:59"
      :location    {:association-type sut/association-type-inline
                    :entity           {:name          "test-unload-name"
                                       :type          sut/location-type-warehouse
                                       :geo-reference {}}}
      :remarks     "test-unload-remarks"}}]})

(deftest trip
  (testing "status"
    (is (= sut/status-draft (sut/trip-status test-trip)))
    (is (= sut/status-cancelled
           (-> test-trip
               (sut/trip-status! sut/status-cancelled)
               (sut/trip-status)))))

  (testing "carrier EORI"
    (is (= "EU.EORI.CARRIER" (sut/trip-carrier-eori test-trip)))
    (is (= "EU.EORI.OTHER-CARRIER"
           (-> test-trip
               (sut/trip-add-subcontractor! "EU.EORI.OTHER-CARRIER")
               (sut/trip-carrier-eori)))))

  (testing "driver id digits"
    (is (= "3141" (sut/trip-driver-id-digits test-trip)))
    (is (= "1413"
           (-> test-trip
               (sut/trip-driver-id-digits! "1413")
               (sut/trip-driver-id-digits)))))

  (testing "license plate"
    (is (= "AB12CDE" (sut/trip-license-plate test-trip)))
    (is (= "ZY09XWV"
           (-> test-trip
               (sut/trip-driver-id-digits! "ZY09XWV")
               (sut/trip-driver-id-digits)))))

  (testing "->map"
    (is (= {:id               "0"
            :status           "draft"
            :ref              "test-ref"
            :load-date        "2000-01-01T00:00"
            :load-location    "test-load-name"
            :load-remarks     "test-load-remarks"
            :unload-remarks   "test-unload-remarks"
            :unload-date      "2000-01-01T23:59"
            :unload-location  "test-unload-name"
            :carrier-eori      "EU.EORI.CARRIER"
            :driver-id-digits "3141"
            :license-plate    "AB12CDE"}
           (sut/trip->map test-trip)))))
