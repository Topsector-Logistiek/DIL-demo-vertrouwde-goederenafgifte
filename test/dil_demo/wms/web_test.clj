(ns dil-demo.wms.web-test
  (:require [clojure.test :refer [deftest is testing]]
            [dil-demo.wms.web :as sut]
            [nl.jomco.http-status-codes :as http-status]
            [ring.mock.request :refer [request]]))

(def store
  {:transport-orders
   {"some-id"
    {:id "some-id"
     :consignments
     [{:association-type "inline"
       :entity {:external-attributes {:ref "some-ref"}}}]}}})

(deftest handler
  (testing "/"
    (let [{:keys [status headers]} (sut/handler (request :get "/"))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "/verify-not-found"
    (is (nil? (sut/handler (request :get "/verify-not-found")))))

  (testing "/verify-some-id"
    (let [{:keys [status headers body]}
          (sut/handler (assoc (request :get "/verify-some-id")
                              :store store))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"<input [^>]*\bvalue=\"some-ref\"[^>]*>" body)))))
