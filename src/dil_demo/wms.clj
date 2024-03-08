(ns dil-demo.wms
  (:require [dil-demo.wms.web :as web]))

(defn make-handler [_config]
  web/handler)
