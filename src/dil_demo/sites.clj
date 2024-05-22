;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.sites)

(def sites
  [{:slug "erp", :path "/erp/", :title "ERP"}
   {:slug "tms", :path "/tms/", :title "TMS"}
   {:slug "tms-chauffeur", :path "/tms/chauffeur/", :title "TMS (chauffeur)"}
   {:slug "wms", :path "/wms/", :title "WMS"}])
