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
  {:jetty {:port (Integer/parseInt (get-env "PORT" "8080"))}
   :store {:file (get-env "STORE_FILE" "/tmp/dil-demo.edn")}
   :erp   {:eori       (get-env "ERP_EORI")
           :key-file   (get-env "ERP_KEY_FILE")
           :chain-file (get-env "ERP_CHAIN_FILE")}
   :tms   {:eori       (get-env "TMS_EORI")
           :key-file   (get-env "TMS_KEY_FILE")
           :chain-file (get-env "TMS_CHAIN_FILE")}
   :wms   {:eori       (get-env "WMS_EORI")
           :key-file   (get-env "WMS_KEY_FILE")
           :chain-file (get-env "WMS_CHAIN_FILE")}})

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
