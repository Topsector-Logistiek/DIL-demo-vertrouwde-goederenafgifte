(ns dil-demo.store
  (:require [clojure.tools.logging.readable :as log]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defmulti commit (fn [_store-atom _env-key [cmd & _args]] cmd))

(defmethod commit :put!
  [store-atom env-key [_cmd table-key {:keys [id] :as value}]]
  (swap! store-atom assoc-in [env-key table-key id] value))

(defmethod commit :delete!
  [store-atom env-key [_cmd table-key id]]
  (swap! store-atom update-in [env-key table-key] dissoc id))

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
  [app {:keys [file env-key-fn]}]
  (let [store-atom (atom (load-store file))]
    (fn store-wrapper [request]
      (let [env-key (env-key-fn request)
            {::keys [commands]
             :as    response} (-> request
                                  (assoc ::store (get @store-atom env-key))
                                  (app))]
        (when (seq commands)
          (doseq [cmd commands]
            (log/debug "committing" cmd)
            (commit store-atom env-key cmd))
          (future (save-store @store-atom file)))
        response))))
