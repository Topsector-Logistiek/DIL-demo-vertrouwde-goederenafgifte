(ns dil-demo.store
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as log]))

(defmulti commit (fn [_store-atom _user-number [cmd & _args]] cmd))

(defmethod commit :put!
  [store-atom user-number [_cmd table-key {:keys [id] :as value}]]
  (swap! store-atom assoc-in [user-number table-key id] value))

(defmethod commit :delete!
  [store-atom user-number [_cmd table-key id]]
  (swap! store-atom update-in [user-number table-key] dissoc id))

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
    (fn store-wrapper [{:keys [user-number] :as request}]
      (let [{::keys [commands]
             :as    response} (-> request
                                  (assoc ::store (get @store-atom user-number))
                                  (app))]
        (when (seq commands)
          (doseq [cmd commands]
            (log/debug "committing" cmd)
            (commit store-atom user-number cmd))
          (future (save-store @store-atom file)))
        response))))
