;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.data)

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
