(ns dil-demo.core
  (:gen-class)
  (:require [dil-demo.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(def config
  {:jetty {:port (Integer/parseInt (or (System/getenv "PORT") "8080"))}
   :store {:file (or (System/getenv "STORE_FILE") "/tmp/dil-demo.edn")}})

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
  (start-webserver config (web/make-app config)))
