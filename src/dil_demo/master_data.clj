;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.master-data)

(def owners [:erp])
(def warehouses [:wms])
(def carriers [:tms-1 :tms-2])

(def goods #{"Toiletpapier"
             "Bananen"
             "Smartphones"
             "Cola"
             "T-shirts"})

(def locations
  {"AH Winkel 23, Drachten"
   "Kiryat Onoplein 87\n9203 KS  Drachten\nNederland"

   "AH, Pijnacker"
   "Ackershof 53-60\n2641 DZ  Pijnacker\nNederland"

   "Bol, Waalwijk"
   "Mechie Trommelenweg 1\n5145 ND  Waalwijk\nNederland"

   "Intel, Schiphol"
   "Capronilaan 37\n1119 NG  Schiphol-Rijk\nNederland"

   "Jumbo, Tilburg"
   "Stappegoorweg 175\n5022 DD  Tilburg\nNederland"

   "Nokia, Espoo"
   "Karakaari 7\n02610 Espoo\nFinland"})

(def warehouse-address
  "Kerkstraat 1\n1234 AZ  Nergenshuizen\nNederland")

(defn wrap [app config]
  (let [eori->name (->> (concat owners carriers warehouses)
                        (map #(vector (get-in config [% :eori])
                                      (get-in config [% :site-name])))
                        (into {}))
        carriers   (->> carriers
                        (map #(vector (get-in config [% :eori])
                                      (get-in config [% :site-name])))
                        (into {}))
        warehouses (->> warehouses
                        (map #(vector (get-in config [% :eori])
                                      (get-in config [% :site-name])))
                        (into {}))]
    (fn carriers-wrapper [req]
      (app (assoc req
                  :master-data
                  {:carriers carriers
                   :warehouses warehouses
                   :warehouse-addresses (constantly warehouse-address)
                   :eori->name eori->name})))))
