(ns dil-demo.wms
  (:require [dil-demo.ishare.client :as ishare-client]
            [dil-demo.web-utils :as web-utils]
            [dil-demo.wms.web :as web]))

(defn make-handler [config]
  (-> web/handler
      (web-utils/wrap-config config)
      (ishare-client/wrap-client-data config)))
