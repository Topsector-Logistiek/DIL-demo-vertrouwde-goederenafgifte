;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.web
  (:require [clojure.string :refer [re-quote-replacement]]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET routes]]
            [compojure.route :refer [resources]]
            [dil-demo.erp :as erp]
            [dil-demo.events :as events]
            [dil-demo.ishare.client :as ishare-client]
            [dil-demo.master-data :as master-data]
            [dil-demo.sites :refer [sites]]
            [dil-demo.store :as store]
            [dil-demo.tms :as tms]
            [dil-demo.web-utils :as w]
            [dil-demo.wms :as wms]
            [nl.jomco.ring-session-ttl-memory :refer [ttl-memory-store]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :refer [content-type not-found redirect]])
  (:import (java.util.regex Pattern)))

(defn rewrite-relative-redirect [res url-prefix]
  (let [loc (get-in res [:headers "Location"])]
    (if (and loc (not (re-matches #"^(/|\w*://).*" loc)))
      (update-in res [:headers "Location"] #(str url-prefix "/" %))
      res)))

(defn wrap-with-prefix [app url-prefix handler]
  (let [url-prefix-re (Pattern/compile (str "^" url-prefix "(/.*)"))]
    (fn prefix-wrapper [{:keys [uri] :as req}]
      (let [path (last (re-find url-prefix-re uri))]
        (cond
          path
          (or (handler (assoc req :uri path))
              (app req))

          (= uri url-prefix)
          (redirect (str uri "/"))

          :else
          (app req))))))

(def not-found-handler
  (constantly (-> (w/render-body "dil"
                                 [:main
                                  [:p "Deze pagina bestaat niet."]
                                  [:a.button {:href "/"} "Terug naar het startscherm"]]
                                 :title "Niet gevonden"
                                 :site-name "Dil-Demo")
                  (not-found)
                  (content-type "text/html; charset=utf-8"))))

(defn list-apps []
  [:main
   [:ul
    (for [{:keys [path title]} sites]
      [:li [:a {:href path} title]])]])

(def handler
  (routes
   (GET "/" {}
     (w/render "dil"
               (list-apps)
               :title "Demo Vertrouwde Goederenafgifte"
               :site-name "DIL-Demo"))
   (resources "/")
   not-found-handler))

(defn wrap-log
  [handler]
  (fn [request]
    (let [response (handler request)]
      (log/info (str (:status response) " " (:request-method request) " " (:uri request)))
      response)))

(defn ->authenticate
  "Make authentication function.

  This function accepts `user` named with `user-prefix` and number
  between 1 and `max-accounts`.  When `passwd` matches the number
  multiplied by `pass-multi` it returns the user number."
  [{:keys [user-prefix pass-multi max-accounts]}]
  (let [user-re (re-pattern (str "^" (re-quote-replacement user-prefix) "(\\d+)$"))]
    (fn authenticate [user passwd]
      (when-let [[_ n-str] (re-matches user-re user)]
        (let [n (parse-long n-str)]
          (and (<= 1 n max-accounts)
               (= (str (* n pass-multi)) passwd)
               n))))))

(defn wrap-user-number
  "Moves `basic-authentication` request key to `user-number` for
  clarity.  Needs to be wrapped by `wrap-basic-authentication`
  middleware."
  [app]
  (fn user-number-wrapper [req]
    (let [{:keys [basic-authentication]} req]
      (app (if basic-authentication
             (-> req
                 (dissoc :basic-authentication)
                 (assoc :user-number basic-authentication))
             req)))))

(defn wrap-app [app id {:keys [pulsar store-atom] :as config} make-handler]
  (let [config  (get config id)
        config  (assoc config
                       :id          id
                       :pulsar      pulsar
                       :store-atom  store-atom
                       :client-data (ishare-client/->client-data config))]
    (wrap-with-prefix app
                      (str "/" (name id))
                      (-> config
                          (make-handler)
                          (events/wrap config)
                          (store/wrap config)))))

(defn make-app [config]
  (let [;; NOTE: single atom to keep store because of publication among apps
        store-atom (store/get-store-atom (-> config :store :file))
        config     (assoc config :store-atom store-atom)]
    (-> handler
        (wrap-app :erp config erp/make-handler)
        (wrap-app :wms config wms/make-handler)
        (wrap-app :tms-1 config tms/make-handler)
        (wrap-app :tms-2 config tms/make-handler)

        (master-data/wrap config)

        (wrap-user-number)
        (wrap-basic-authentication (->authenticate (config :auth)))

        (wrap-defaults (assoc-in site-defaults
                                 [:session :store] (ttl-memory-store)))
        (wrap-stacktrace)

        (wrap-log))))
