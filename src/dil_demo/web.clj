(ns dil-demo.web
  (:require [compojure.core :refer [routes]]
            [compojure.route :refer [resources]]
            [dil-demo.erp.web :as erp-web]
            [dil-demo.tms.web :as tms-web]
            [dil-demo.wms.web :as wms-web])
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

(def not-found-handler (constantly {:status 404}))

(def handler
  (routes
   (resources "/")
   not-found-handler))

(defn make-app [_]
  (-> handler
      (wrap-with-prefix "/erp" erp-web/handler)
      (wrap-with-prefix "/tms" tms-web/handler)
      (wrap-with-prefix "/wms" wms-web/handler)))
