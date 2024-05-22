;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.wms
  (:require [dil-demo.ishare.client :as ishare-client]
            [dil-demo.web-utils :as web-utils]
            [dil-demo.wms.web :as web]))

(defn make-handler [config]
  (-> web/handler
      (web-utils/wrap-config config)
      (ishare-client/wrap-client-data config)))
