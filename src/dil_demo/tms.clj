(ns dil-demo.tms
  (:require [dil-demo.tms.web :as web]
            [dil-demo.store :as store]))

(defn make-handler [_config]
  (store/wrap web/handler))
