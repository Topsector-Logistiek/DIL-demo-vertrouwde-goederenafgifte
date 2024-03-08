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

(defn load-store [filename]
  (let [file (io/file filename)]
    (if (.exists file)
      (edn/read-string (slurp file))
      {})))

(defn save-store [store filename]
  (spit (io/file filename) (pr-str store)))

(defn wrap
  [app {:keys [file]}]
  (let [store-atom (atom (load-store file))]
    (fn wrap-store [req]
      (let [req         (assoc req :store @store-atom)
            {:keys [store-commands]
             :as   res} (app req)]
        (when (seq store-commands)
          (doseq [cmd store-commands]
            (commit store-atom cmd))
          (future (save-store @store-atom file)))
        res))))
