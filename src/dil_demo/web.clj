(ns dil-demo.web
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :refer [resources]]
            [clojure.string :refer [re-quote-replacement]]
            [clojure.tools.logging :as log]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.util.response :refer [content-type not-found redirect]]
            [nl.jomco.ring-session-ttl-memory :refer [ttl-memory-store]]
            [dil-demo.data :as d]
            [dil-demo.erp :as erp]
            [dil-demo.tms :as tms]
            [dil-demo.wms :as wms]
            [dil-demo.web-utils :as w]
            [dil-demo.sites :refer [sites]]
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
  [:nav
   [:p "Lorem ipsum.."]
   [:ul
    (for [{:keys [path title]} sites]
      [:li [:a {:href path} title]])]])

(def handler
  (routes
   (GET "/" {}
     (w/render-body "dil"
                    (list-apps)
                    :title "Demo Vertrouwde Goederenafgifte"
                    :site-name "DIL-Demo"))
   (resources "/")
   not-found-handler))

(defn wrap-carriers [app {{carrier-eori :eori} :tms}]
  (let [carriers {carrier-eori d/tms-name}]
    (fn carriers-wrapper [req]
      (app (assoc req :carriers carriers)))))

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

(defn make-app [config]
  (-> handler
      (wrap-with-prefix "/erp" (erp/make-handler (config :erp)))
      (wrap-with-prefix "/tms" (tms/make-handler (config :tms)))
      (wrap-with-prefix "/wms" (wms/make-handler (config :wms)))
      (wrap-carriers config)

      ;; every basic auth user has its own store
      (store/wrap (config :store))
      (wrap-user-number)
      (wrap-basic-authentication (->authenticate (config :auth)))

      (wrap-defaults (assoc-in site-defaults
                               [:session :store] (ttl-memory-store)))
      (wrap-stacktrace)
      (wrap-log)))
