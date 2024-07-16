;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.events.web
  (:require [clojure.string :as string]
            [compojure.core :refer [routes routing GET]]
            [dil-demo.web-utils :as w]))

(defn- event-list [events]
  [:main
   (when-not (seq events)
     [:article.empty
      [:p "Nog geen events geregistreerd.."]])
   (for [{:keys [topic published-at payload]}
         (->> events
              (map (fn [[_msgId [topic {:keys [:publishTime :payload]}]]]
                     {:topic        (string/join "/" (take 2 topic))
                      :published-at publishTime
                      :payload      payload}))
              (sort-by :published-at)
              reverse)]
     [:article
      [:header
       [:div.status published-at]
       [:div.topic topic]]
      [:pre (w/to-json payload)]])])

(defn- make-handler
  "Handler on /events"
  [{:keys [id site-name]}]
  (routes
   (GET "/events" {:keys [events flash]}
        (w/render (name id)
                  (event-list events)
                  :flash flash
                  :title "Events"
                  :site-name site-name))))

(defn wrap
  "Add route /events serving basic screen for viewing received events."
  [app config]
  (let [handler (make-handler config)]
    (fn [req]
      (routing req handler app))))
