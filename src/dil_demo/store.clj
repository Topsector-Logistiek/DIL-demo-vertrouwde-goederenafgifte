(ns dil-demo.store
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defmulti commit (fn [_ [cmd & _]] cmd))

(defmethod commit :put!
  [store-atom [_ table-key {:keys [id] :as value}]]
  (swap! store-atom assoc-in [table-key id] value))

(defmethod commit :delete!
  [store-atom [_ table-key id]]
  (swap! store-atom update table-key dissoc id))

(def store-file (io/file "/tmp/dil-demo.edn"))

(defn load-store []
  (let [file store-file]
    (if (.exists file)
      (edn/read-string (slurp file))
      {})))

(def ^:private store-atom (atom (load-store)))

(defn save-store [store]
  (spit store-file (pr-str store)))

(defn wrap
  [app]
  (fn wrap-store [req]
    (let [req         (assoc req :store @store-atom)
          {:keys [store-commands]
           :as   res} (app req)]
      (when (seq store-commands)
        (doseq [cmd store-commands]
          (commit store-atom cmd))
        (future (save-store @store-atom)))
      res)))
