;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.web-utils
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [dil-demo.sites :refer [sites]]
            [ring.util.response :as response]
            [hiccup2.core :as hiccup])
  (:import (java.text SimpleDateFormat)
           (java.util UUID)))

(defn template [site main & {:keys [flash title site-name]}]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport", :content "width=device-width,initial-scale=1.0"}]

    [:title (str title " — " site-name)]

    [:link {:rel "stylesheet", :href "/assets/base.css"}]
    [:link {:rel "stylesheet", :href (str "/assets/" site ".css")}]]

   [:body
    [:nav.top
     [:ul
      [:li [:strong site-name]]]
     [:ul
      (for [{:keys [slug path title]} sites]
        [:li [:a {:href path, :class (when (= slug site) "current")} title]])]]

    [:header.container [:h1 title]]
    [:main.container
     (for [[type message] (select-keys flash [:error :success :warning])]
       [:article.flash {:class (str "flash-" (name type))} message])
     main]
    [:footer.container
     [:img {:src   "/assets/bdi-logo.png"
            :title "Powered by BDI — Basic Data Infrastructure"
            :alt   "Powered by BDI — Basic Data Infrastructure"}]]]])

(defn qr-code [text]
  (let [id (str "qrcode-" (UUID/randomUUID))]
    [:div.qr-code-container
     [:script {:src "/assets/qrcode.js"}] ;; https://davidshimjs.github.io/qrcodejs/

     [:div.qr-code {:id id}]
     [:script (hiccup/raw
               (str "new QRCode(document.getElementById("
                    (json/json-str id)
                    "), "
                    (json/json-str text)
                    ")"))]]))

(defn format-date [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd") date))

(defn or-em-dash [val]
  (if (string/blank? val)
    "—"
    val))

(defn render-body [site main & opts]
  {:pre [(string? site) (coll? main)]}
  (str "<!DOCTYPE HTML>" (hiccup/html (apply template site main opts))))

(defn render [& args]
  (-> (apply render-body args)
      (response/response)
      (response/header "Content-Type" "text/html; charset=utf-8")))

(defn camelize
  "Convert key `s` from lispy to jsony style."
  [s]
  (let [words (string/split s #"-")]
    (string/join (into [(first words)] (map string/capitalize (rest words))))))

(defn to-json
  "Transform `val` from lispy value to jsony string."
  [val & {:keys [depth pad key-fn]
          :or   {depth  0
                 pad    "  "
                 key-fn name}
          :as   opts}]
  (let [padding (-> depth (repeat pad) (string/join))]
    (str
     padding
     (cond
       (map? val)
       (if (empty? val)
         "{}"
         (str "{\n"
              (string/join ",\n"
                           (for [[k v] val]
                             (str
                              padding pad
                              (to-json (key-fn k) (assoc opts :depth 0))
                              ": "
                              (string/trim (to-json v (assoc opts :depth (inc depth)))))))
              "\n"
              padding
              "}"))

       (coll? val)
       (if (empty? val)
         "[]"
         (str "[\n"
              (string/join ",\n"
                           (for [v val]
                             (to-json v (assoc opts :depth (inc depth)))))
              "\n"
              padding
              "]"))

       (instance? java.net.URI val)
       (json/write-str (str val) :escape-slash false)

       :else
       (json/write-str val :escape-slash false)))))

(defn otm-to-json [val]
  (to-json val :key-fn (comp camelize name)))

(defn server-description
  [{:ishare/keys [server-id server-name]}]
  (if server-name
    [:span [:q server-id] " (" server-name ")"]
    [:q server-id]))

(defmulti ishare-interaction-summary #(-> % :request :ishare/message-type))

(defmethod ishare-interaction-summary :default
  [_]
  [:span "Oeps.."])

(defmethod ishare-interaction-summary :access-token
  [{:keys [request]}]
  [:span "Ophalen access token voor " (server-description request)])

(defmethod ishare-interaction-summary :parties
  [{:keys [request]}]
  [:span "Partijen opvragen van " (server-description request)])

(defmethod ishare-interaction-summary :party
  [{{:ishare/keys [party-id] :as request} :request}]
  [:span "Partij " [:q party-id] " opvragen van satelliet " (server-description request)])

(defmethod ishare-interaction-summary :ishare/policy
  [{:keys [request]}]
  [:span "Policy aanmaken in iSHARE Authorisatie Register op " (server-description request)])

(defmethod ishare-interaction-summary :poort8/delete-policy
  [{:keys [request]}]
  [:span "Policy verwijderen in Poort8 Authorisatie Register op " (server-description request)])

(defmethod ishare-interaction-summary :poort8/policy
  [{:keys [request]}]
  [:span "Policy aanmaken in Poort8 Authorisatie Register op " (server-description request)])

(defmethod ishare-interaction-summary :delegation
  [{:keys [request]}]
  [:span "Delegation Evidence opvragen in Authorisatie Register " (server-description request)])

(defn ishare-log-intercept-to-hiccup [logs]
  [:ol
   (for [interaction logs]
     [:li.interaction
      [:details
       [:summary (ishare-interaction-summary interaction)]
       (when (:request interaction)
         [:div.request
          [:p "Request:"]
          [:pre (to-json (-> interaction
                             :request
                             (select-keys [:method :uri :params :form-params :json-params :headers])))]])
       (when (:status interaction)
         [:div.response
          [:p "Response:"]
          [:pre (to-json (select-keys interaction [:status :headers :body]))]])]])])

(defn explanation [explanation]
  (when (seq explanation)
    [:details.explanation
     [:summary.button.secondary "Uitleg"]
     [:ol
      (for [[title {:keys [otm-object ishare-log event]}] explanation]
        [:li
         [:h3 title]
         (when otm-object
           [:details
            [:summary "Bekijk OTM object"]
            [:pre (otm-to-json otm-object)]])
         (when ishare-log
           (ishare-log-intercept-to-hiccup ishare-log))
         (when event
           [:details
            [:summary "Bekijk event"]
            [:pre (to-json event)]])])]]))

(defn append-explanation [res & explanation]
  (update-in res [:flash :explanation] (fnil into [])
             explanation))
