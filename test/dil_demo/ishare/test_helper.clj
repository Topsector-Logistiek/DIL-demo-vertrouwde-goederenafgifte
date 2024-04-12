(ns dil-demo.ishare.test-helper
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [dil-demo.ishare.client :as ishare-client])
  (:import (java.io StringReader)
           (java.net URI)
           (java.net.http HttpClient HttpClient$Version HttpHeaders HttpResponse HttpResponse$BodySubscribers)
           (java.nio.charset StandardCharsets)
           (java.util List)
           (java.util.concurrent Flow$Subscriber)
           (java.util.function BiPredicate)))

(defn- map->http-response
  "Transform ring like response map into HttpResponse."
  [{:keys [status uri body headers]}]
  (proxy [HttpResponse] []
    (statusCode [] status)
    (body [] (when body (StringReader. body)))
    (version [] HttpClient$Version/HTTP_1_1)
    (uri [] (URI. uri))

    (headers []
      (HttpHeaders/of (reduce (fn [m [k v]]
                                (assoc m
                                       (if (keyword? k) (name k) k)
                                       (if (coll? v) v [v])))
                              {}
                              headers)
                      (proxy [BiPredicate] [] (test [& _] true))))))

(defn- body-publisher->str [pb]
  (let [bs (HttpResponse$BodySubscribers/ofString StandardCharsets/UTF_8)
        fs (proxy [Flow$Subscriber] []
             (onSubscribe [v] (.onSubscribe bs v))
             (onNext [v] (.onNext bs (List/of v)))
             (onComplete [] (.onComplete bs)))]
    (.subscribe (.get pb) fs)
    (-> bs (.getBody) (.toCompletableFuture) (.join))))

(defn- http-request->map
  "Transform HttpRequest into ring like request map."
  [req]
  {:method  (.method req)
   :uri     (str (.uri req))
   :headers (reduce (fn [m [k v]]
                      (assoc m k (if (= 1 (count v)) (first v) (vec v))))
                    {}
                    (.map (.headers req)))
   :body    (let [pb (.bodyPublisher req)]
              (if (.isEmpty pb)
                nil
                (body-publisher->str pb)))})

(defn build-client
  "Create a client useable with babashka.http-client/request.

  This client deliveries ring like request map to the given
  bi-directional channel and expects ring like response maps as
  response on the same channel."
  [c]
  (proxy [HttpClient] []
    (send [req _]
      (>!! c (http-request->map req))
      (map->http-response (<!! c)))))

(defn run-exec
  "Run ishare client exec asynchronously returning a channel and a result future.

  The returns channel is bi-directional it delivers ring like request
  map and expects ring like response maps."
  [req]
  (let [c (async/chan)]
    [c (binding [ishare-client/http-client (build-client c)]
         (future
           (try
             (let [res (ishare-client/exec req)]
               (async/close! c)
               res)
             (catch Throwable e
               (>!! c {:exception e})
               (async/close! c)))))]))
