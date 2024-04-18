(ns dil-demo.store
  (:require [clojure.tools.logging.readable :as log]
            [clojure.java.io :as io]
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

;; TODO: race condition with `load-store`.  Make this an atomic file
;; write + move
(defn save-store [store filename]
  (spit (io/file filename) (pr-str store)))

(defn wrap
  "Middleware providing storage.

  Provides :dil-demo.store/store key in request, containing the
  current state of store (read-only).

  When :dil-demo.store/commands key in response provides a colleciton
  of commands, those will be committed to the storage."
  [app {:keys [file]}]
  (let [store-atom (atom (load-store file))]
    (fn store-wrapper [request]
      (let [{::keys [commands]
             :as    response} (-> request
                                  (assoc ::store @store-atom)
                                  (app))]
        (when (seq commands)
          (doseq [cmd commands]
            (log/debug "committing" cmd)
            (commit store-atom cmd))
          (future (save-store @store-atom file)))
        response))))
