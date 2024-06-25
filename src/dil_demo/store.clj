;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns dil-demo.store
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as log]
            [dil-demo.otm :as otm]))

(defmulti commit (fn [_store-atom _user-number _own-eori [cmd & _args]] cmd))

(defmethod commit :put! ;; put data in own database
  [store-atom user-number own-eori [_cmd table-key {:keys [id] :as value}]]
  (otm/check! table-key value)
  (swap! store-atom assoc-in [user-number own-eori table-key id] value))

(defmethod commit :publish! ;; put data in other database
  [store-atom user-number _own-eori [_cmd table-key target-eori {:keys [id] :as value}]]
  (otm/check! table-key value)
  (swap! store-atom assoc-in [user-number target-eori table-key id] value))

(defmethod commit :delete!
  [store-atom user-number own-eori [_cmd table-key id]]
  (swap! store-atom update-in [user-number own-eori table-key] dissoc id))

(defn load-store [filename]
  (let [file (io/file filename)]
    (if (.exists file)
      (edn/read-string (slurp file))
      {})))

;; TODO: race condition with `load-store`.  Make this an atomic file
;; write + move
(defn save-store [store filename]
  (spit (io/file filename) (pr-str store)))

(defn get-store-atom
  "Return a store atom loaded with data from `filename` (EDN format) and
  automatically stored to that file on changes."
  [filename]
  (let [store-atom (atom (load-store filename))]
    (add-watch store-atom nil
               (fn [_ _ old-store new-store]
                 (when (not= old-store new-store)
                   (future (save-store new-store filename)))))))

(defn wrap
  "Middleware providing storage.

  Provides :dil-demo.store/store key in request, containing the
  current state of store (read-only).

  When :dil-demo.store/commands key in response provides a collection
  of commands, those will be committed to the storage."
  [app store-atom own-eori]
  (fn store-wrapper [{:keys [user-number] :as request}]
    (let [{::keys [commands]
           :as    response} (-> request
                                (assoc ::store (get-in @store-atom [user-number own-eori]))
                                (app))]
      (when (seq commands)
        (doseq [cmd commands]
          (log/debug "committing" cmd)
          (commit store-atom user-number own-eori cmd)))
      response)))
