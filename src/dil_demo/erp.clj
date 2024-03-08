(ns dil-demo.erp
  (:require [dil-demo.erp.web :as web]))

(defn make-handler [_config]
  web/handler)
