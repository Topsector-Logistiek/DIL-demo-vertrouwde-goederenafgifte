(ns dil-demo.data)

(def wms-name "Secure Storage Warehousing")
(def tms-name "Precious Goods Transport")
(def erp-name "Smartphone Shop")

;; NOTE: these have a WMS as demoed here
(def warehouses [wms-name])

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

   wms-name
   "Kerkstraat 1\n1234 AZ  Nergenshuizen\nNederland"

   "Intel, Schiphol"
   "Capronilaan 37\n1119 NG  Schiphol-Rijk\nNederland"

   "Jumbo, Tilburg"
   "Stappegoorweg 175\n5022 DD  Tilburg\nNederland"

   "Nokia, Espoo"
   "Karakaari 7\n02610 Espoo\nFinland"})
