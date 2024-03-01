(ns dil-demo.core
  (:require [dil-demo.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(def config {:jetty {:port 8080}})

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
