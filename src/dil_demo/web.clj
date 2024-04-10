(ns dil-demo.web
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :refer [content-type not-found redirect]]
            [nl.jomco.ring-session-ttl-memory :refer [ttl-memory-store]]
            [dil-demo.erp :as erp]
            [dil-demo.tms :as tms]
            [dil-demo.wms :as wms]
            [dil-demo.web-utils :as w]
            [dil-demo.store :as store])
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
          (handler (assoc req :uri path))

          (= uri url-prefix)
          (redirect (str uri "/"))

          :else
          (app req))))))

(def not-found-handler
  (constantly (-> "Not found"
                  (not-found)
                  (content-type "text/html"))))

(defn list-apps []
  [:nav
   [:p "Lorem ipsum.."]
   [:ul
    (for [[path name] [["/erp/" "ERP"]
                       ["/tms/" "TMS"]
                       ["/wms/" "WMS"]]]
      [:li [:a {:href path} name]])]])

(def handler
  (routes
   (GET "/" {}
     (w/render-body "dil"
                    "DIL — Demo Vertrouwde Goederenafgifte"
                    (list-apps)))
   (resources "/")
   not-found-handler))

(defn wrap-carriers [app {{carrier-eori :eori} :tms}]
  (let [carriers {carrier-eori "Precious goods movement BV"}]
    (fn carriers-wrapper [req]
      (app (assoc req :carriers carriers)))))

(defn make-app [config]
  (-> handler
      (wrap-with-prefix "/erp" (erp/make-handler (config :erp)))
      (wrap-with-prefix "/tms" (tms/make-handler (config :tms)))
      (wrap-with-prefix "/wms" (wms/make-handler (config :wms)))
      (wrap-carriers config)
      (store/wrap (config :store))
      (wrap-defaults (assoc-in site-defaults
                               [:session :store] (ttl-memory-store)))
      (wrap-stacktrace)))
