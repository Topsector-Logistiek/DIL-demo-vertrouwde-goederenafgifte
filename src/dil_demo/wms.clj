(ns dil-demo.wms
  (:require [dil-demo.wms.web :as web]
            [dil-demo.store :as store]))

(defn make-handler [_config]
  (store/wrap web/handler))
