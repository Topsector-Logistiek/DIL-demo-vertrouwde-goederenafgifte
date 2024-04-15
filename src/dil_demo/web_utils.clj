(ns dil-demo.web-utils
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [hiccup2.core :as hiccup]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]])
  (:import (java.text SimpleDateFormat)
           (java.util UUID)))

(defn anti-forgery-input []
  [:input {:name "__anti-forgery-token", :value *anti-forgery-token*, :type "hidden"}])

(defn template [site title main & {:keys [flash]}]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport", :content "width=device-width,initial-scale=1.0"}]

    [:title title]

    [:link {:rel "stylesheet", :href (str "/assets/" site ".css")}]
    [:link {:rel "stylesheet", :href "/assets/base.css"}]]

   [:body
    [:header [:h1 title]]
    [:main
     (for [[type message] (select-keys flash [:error :success :warning])]
       [:div.flash {:class (str "flash-" (name type))} message])
     main]
    [:footer]]])

(defn field [{:keys [name label type value list value-fn]
              :as opts
              :or {value-fn val}}]
  (let [list-id (str "list-" (UUID/randomUUID))
        datalist [:datalist {:id list-id} (for [v list] [:option {:value v}])]
        opts (if list (assoc opts :list list-id) opts)]
    [:div.field
     [:label {:for name} label]
     (cond
       (= "textarea" type)
       [:textarea (dissoc opts :label :type) value]

       (= "select" type)
       [:select (dissoc opts :label :type :list :value)
        (for [option list]
          (if (vector? option)
            (let [[k _] option]
              [:option {:value k, :selected (= k value)} (value-fn option)])
            [:option {:value option, :selected (= option value)} option]))]

       :else
       [:input (-> opts
                   (dissoc :label)
                   (assoc :list list-id))])
     (when list
       datalist)]))

(defn delete-button [path]
  [:form.delete {:method "POST", :action path}
   (anti-forgery-input)
   [:input {:type "hidden", :name "_method", :value "DELETE"}]
   [:button {:type "submit", :onclick "return confirm('Zeker weten?')"} "Verwijderen"]])

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

(defn render-body [site title h & {:keys [flash]}]
  (str "<!DOCTYPE HTML>" (hiccup/html (template site title h :flash flash))))

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

(defmulti ishare-interaction-summary #(-> % :request :ishare/message-type))

(defn server-description
  [{:ishare/keys [server-id server-name]}]
  (if server-name
    [:span [:q server-id] " (" server-name ")"]
    [:q server-id]))

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
  [:span "Delegation Evidence aanmaken in iSHARE Authorisatie Register op " (server-description request)])

(defmethod ishare-interaction-summary :poort8/delete-policy
  [{:keys [request]}]
  [:span "Policy verwijderen in Poort8 Authorisatie Register op " (server-description request)])

(defmethod ishare-interaction-summary :poort8/policy
  [{:keys [request]}]
  [:span "Policy aanmaken in Poort8 Authorisatie Register op " (server-description request)])

(defmethod ishare-interaction-summary :delegation
  [{:keys [request]}]
  [:span "Delegation Mask opvragen in Authorisatie Register " (server-description request)])

(defn ishare-log-intercept-to-hiccup [logs]
  (for [interaction logs]
    [:li.interaction
     [:details
      [:summary (ishare-interaction-summary interaction)]
      [:p "Request:"]
      [:pre.request
       (to-json (-> interaction
                    :request
                    (select-keys [:method :uri :params :form-params :json-params :headers])))]
      [:p "Response:"]
      [:pre.response
       (to-json (select-keys interaction [:status :headers :body]))]]]))

(defn wrap-config [app config]
  (fn config-wrapper [req]
    (app (assoc req :config config))))
