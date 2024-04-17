(ns dil-demo.web-test
  (:require [clojure.test :refer [deftest is testing]]
            [dil-demo.web :as sut]
            [nl.jomco.http-status-codes :as http-status]
            [ring.mock.request :refer [request]]))

(deftest handler
  (testing "/"
    (let [{:keys [status headers]} (sut/handler (request :get "/"))]
      (is (= http-status/ok status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type")))))

  (testing "/assets"
    (let [{:keys [status headers]} (sut/handler (request :get "/assets/base.css"))]
      (is (= http-status/ok status))
      (is (= "text/css" (get headers "Content-Type")))))

  (testing "/not-found"
    (let [{:keys [status headers]} (sut/handler (request :get "/not-found"))]
      (is (= http-status/not-found status))
      (is (= "text/html; charset=utf-8" (get headers "Content-Type"))))))
