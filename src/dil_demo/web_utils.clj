(ns dil-demo.web-utils
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [hiccup2.core :as hiccup]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]])
  (:import (java.text SimpleDateFormat)
           (java.util UUID)))

(def locations {"Intel, Schiphol"
                "Capronilaan 37\n1119 NG  Schiphol-Rijk\nNederland"

                "Nokia, Espoo"
                "Karakaari 7\n02610 Espoo\nFinland"

                "Bol, Waalwijk"
                "Mechie Trommelenweg 1\n5145 ND  Waalwijk\nNederland"

                "AH, Pijnacker"
                "Ackershof 53-60\n2641 DZ  Pijnacker\nNederland"

                "Jumbo, Tilburg"
                "Stappegoorweg 175\n5022 DD  Tilburg\nNederland"

                "AH Winkel 23, Drachten"
                "Kiryat Onoplein 87\n9203 KS  Drachten\nNederland"})

(def goods #{"Toiletpapier"
             "Bananen"
             "Smartphones"
             "Cola"
             "T-shirts"})

(def carriers {"NL0000000000" "De Vries Transport"})

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

(defn field [{:keys [name label type value list] :as opts}]
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
            (let [[k v] option]
              [:option {:value k, :selected (= k value)} v])
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

(defmethod ishare-interaction-summary :default
  [_]
  [:span "Oeps.."])

(defmethod ishare-interaction-summary :access-token
  [{{:ishare/keys [server-id]} :request}]
  [:span "Ophalen access token voor " [:q server-id]])

(defmethod ishare-interaction-summary :parties
  [{{:ishare/keys [server-id]} :request}]
  [:span "Partijen opvragen van " [:q server-id]])

(defmethod ishare-interaction-summary :party
  [{{:ishare/keys [server-id party-id]} :request}]
  [:span "Partij " [:q party-id] " opvragen van satelliet " [:q server-id]])

(defmethod ishare-interaction-summary :ishare/policy
  [{{:ishare/keys [server-id]} :request}]
  [:span "Delegation Evidence aanmaken in iSHARE Authorisatie Register op " [:q server-id]])

(defmethod ishare-interaction-summary :poort8/delete-policy
  [{{:ishare/keys [server-id]} :request}]
  [:span "Policy verwijderen in Poort8 Authorisatie Register op " [:q server-id]])

(defmethod ishare-interaction-summary :poort8/policy
  [{{:ishare/keys [server-id]} :request}]
  [:span "Policy aanmaken in Poort8 Authorisatie Register op " [:q server-id]])

(defmethod ishare-interaction-summary :delegation
  [{{:ishare/keys [server-id]} :request}]
  [:span "Delegation Mask opvragen in Authorisatie Register " [:q server-id]])

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
