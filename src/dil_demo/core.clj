(ns dil-demo.core
  (:gen-class)
  (:require [dil-demo.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn get-env
  ([k default]
   (or (System/getenv k) default))
  ([k]
   (or (System/getenv k)
       (throw (Exception. (str "environment variable " k " not set"))))))

(defn ->config []
  (let [erp-eori           (get-env "ERP_EORI")
        wms-eori           (get-env "WMS_EORI")
        tms-eori           (get-env "TMS_EORI")
        dataspace-id       (get-env "DATASPACE_ID")
        satellite-id       (get-env "SATELLITE_ID")
        satellite-endpoint (get-env "SATELLITE_ENDPOINT")]
    {:jetty {:port (Integer/parseInt (get-env "PORT" "8080"))}
     :store {:file (get-env "STORE_FILE" "/tmp/dil-demo.edn")}
     :auth  {:user-prefix  (get-env "AUTH_USER_PREFIX" "demo")
             :pass-multi   (parse-long (get-env "AUTH_PASS_MULTI" "31415"))
             :max-accounts (parse-long (get-env "AUTH_MAX_ACCOUNTS" "42"))}
     :erp   {:eori               erp-eori
             :dataspace-id       dataspace-id
             :satellite-id       satellite-id
             :ar-id              (get-env "ERP_AR_ID")
             :ar-endpoint        (get-env "ERP_AR_ENDPOINT")
             :satellite-endpoint satellite-endpoint
             :key-file           (get-env "ERP_KEY_FILE" (str "credentials/" erp-eori ".pem"))
             :chain-file         (get-env "ERP_CHAIN_FILE" (str "credentials/" erp-eori ".crt"))}
     :tms   {:eori               tms-eori
             :dataspace-id       dataspace-id
             :satellite-id       satellite-id
             :satellite-endpoint satellite-endpoint
             :ar-id              (get-env "TMS_AR_ID")
             :ar-endpoint        (get-env "TMS_AR_ENDPOINT")
             :key-file           (get-env "TMS_KEY_FILE" (str "credentials/" tms-eori ".pem"))
             :chain-file         (get-env "TMS_CHAIN_FILE" (str "credentials/" tms-eori ".crt"))}
     :wms   {:eori               wms-eori
             :dataspace-id       dataspace-id
             :satellite-id       satellite-id
             :satellite-endpoint satellite-endpoint
             :key-file           (get-env "WMS_KEY_FILE" (str "credentials/" wms-eori ".pem"))
             :chain-file         (get-env "WMS_CHAIN_FILE" (str "credentials/" wms-eori ".crt"))}}))

(defonce server-atom (atom nil))

(defn start-webserver [{config :jetty} app]
  (run-jetty app config))

(defn stop! []
  (when-let [server @server-atom]
    (.stop server)
    (reset! server-atom nil)))

(defn start! [config]
  (stop!)
  (reset! server-atom
          (start-webserver config (web/make-app config))))

(defn -main []
  (let [config (->config)]
    (start-webserver config (web/make-app config))))
