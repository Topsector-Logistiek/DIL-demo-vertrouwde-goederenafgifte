(ns dil-demo.web
  (:require [compojure.core :refer [routes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.util.response :refer [content-type not-found]]
            [dil-demo.erp :as erp]
            [dil-demo.tms :as tms]
            [dil-demo.wms :as wms]
            [dil-demo.store :as store])
  (:import (java.util.regex Pattern)))

(defn rewrite-relative-redirect [res url-prefix]
  (let [loc (get-in res [:headers "Location"])]
    (if (and loc (not (re-matches #"^(/|\w*://).*" loc)))
      (update-in res [:headers "Location"] #(str url-prefix "/" %))
      res)))

(defn wrap-with-prefix [app url-prefix handler]
  (let [url-prefix-re (Pattern/compile (str "^" url-prefix "(/.*)"))]
    (fn [{:keys [uri] :as req}]
      (if-let [res (when-let [path (last (re-find url-prefix-re uri))]
                     (handler (assoc req :uri path)))]
        res ;; (rewrite-relative-redirect res url-prefix)
        (app req)))))

(def not-found-handler
  (constantly (-> "Not found"
                  (not-found)
                  (content-type "text/html"))))

(def handler
  (routes
   (resources "/")
   not-found-handler))

(defn make-app [config]
  (-> handler
      (wrap-with-prefix "/erp" (erp/make-handler (config :erp)))
      (wrap-with-prefix "/tms" (tms/make-handler (config :tms)))
      (wrap-with-prefix "/wms" (wms/make-handler (config :wms)))
      (store/wrap (config :store))
      (wrap-defaults site-defaults)))
