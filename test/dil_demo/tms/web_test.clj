(ns dil-demo.tms.web-test
  (:require [clojure.test :refer [deftest is testing]]
            [dil-demo.tms.web :as sut]
            [nl.jomco.http-status-codes :as http-status]
            [ring.mock.request :refer [request]]))

(def store
  {:trips
   {"some-id"
    {:id "some-id"
     :external-attributes {:consignment-ref "some-ref"}}}})

(deftest handler
  (testing "/"
    (let [{:keys [status headers]} (sut/handler (request :get "/"))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "/assign-not-found"
    (is (nil? (sut/handler (request :get "/assign-not-found")))))

  (testing "/assign-some-id"
    (let [{:keys [status headers body]}
          (sut/handler (assoc (request :get "/assign-some-id")
                              :store store))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))
      (is (re-find #"<dd>some-ref</dd>" body)))))
