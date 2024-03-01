(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh-all]]
            [dil-demo.core :as core]))

(defn start! []
  (core/start! (assoc-in core/config [:jetty :join?] false)))

(defn stop! []
  (core/stop!))

(defn restart! []
  (stop!)
  (refresh-all :after 'user/start!))
