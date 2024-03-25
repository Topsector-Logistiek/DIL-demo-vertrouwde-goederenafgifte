(in-ns 'clojure.core)

(defn pk
  "Peek value for debugging."
  ([v] (prn v) v)
  ([k v] (prn k v) v))

(ns user
  (:require [dil-demo.core :as core]))

(defn start! []
  (core/start! (assoc-in core/config [:jetty :join?] false)))

(defn stop! []
  (core/stop!))

(defn restart! []
  (stop!)
  (start!))

