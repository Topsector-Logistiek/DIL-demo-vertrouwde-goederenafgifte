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

(deftest handler
  (testing "/"
    (let [{:keys [status headers]} (sut/handler (request :get "/"))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "/consignment-new"
    (let [{:keys [status headers]} (sut/handler (request :get "/consignment-new"))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "/consignment-not-found"
    (is (nil? (sut/handler (request :get "/consignment-not-found")))))

  (testing "/consignment-some-id"
    (let [{:keys [status headers body]}
          (sut/handler (assoc (request :get "/consignment-some-id")
                              ::store/store store))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"<input [^>]*\bvalue=\"some-ref\"[^>]*>" body)))))
