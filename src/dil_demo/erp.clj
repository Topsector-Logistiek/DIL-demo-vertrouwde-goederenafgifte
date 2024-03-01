(ns dil-demo.erp
  (:require [dil-demo.erp.web :as web]
            [dil-demo.store :as store]))

(defn make-handler [_config]
  (store/wrap web/handler))
