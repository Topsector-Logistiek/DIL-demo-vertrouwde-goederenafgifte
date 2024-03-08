(ns dil-demo.tms
  (:require [dil-demo.tms.web :as web]))

(defn make-handler [_config]
  web/handler)
