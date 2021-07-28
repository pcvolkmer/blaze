(ns blaze.interaction.history.type
  "FHIR history interaction on the whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- link [base-url match query-params t relation resource-handle]
  {:fhir/type :fhir.Bundle/link
   :relation relation
   :url (type/->Uri (history-util/nav-url base-url match query-params t
                                          (:t resource-handle)
                                          (:id resource-handle)))})


(defn- build-response
  [context db base-url router match query-params t total version-handles]
  (let [page-size (fhir-util/page-size query-params)
        paged-version-handles (into [] (take (inc page-size)) version-handles)
        self-link #(link base-url match query-params t "self" %)
        next-link #(link base-url match query-params t "next" %)]
    ;; we need take here again because we take page-size + 1 above
    (-> (d/pull-many db (take page-size paged-version-handles))
        (ac/then-apply
          (fn [paged-versions]
            (ring/response
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (iu/luid context)
                 :type #fhir/code"history"
                 :total (type/->UnsignedInt total)
                 :entry
                 (mapv #(history-util/build-entry base-url router %)
                       paged-versions)}

                (seq paged-version-handles)
                (update :link (fnil conj []) (self-link (first paged-version-handles)))

                (< page-size (count paged-version-handles))
                (update :link (fnil conj []) (next-link (peek paged-version-handles))))))))))


(defn- handle
  [context
   db
   {:blaze/keys [base-url]
    ::reitit/keys [router match] :keys [query-params]
    {{:fhir.resource/keys [type]} :data} ::reitit/match}]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        page-t (history-util/page-t query-params)
        page-id (when page-t (fhir-util/page-id query-params))
        since (history-util/since query-params)
        total (d/total-num-of-type-changes db type since)
        version-handles (d/type-history db type page-t page-id since)]
    (build-response context db base-url router match query-params t total
                    version-handles)))


(defn- handler [{:keys [node] :as context}]
  (fn [{:keys [query-params] :as request}]
    (-> (handler-util/db node (fhir-util/t query-params))
        (ac/then-compose #(handle context % request)))))


(defmethod ig/pre-init-spec :blaze.interaction.history/type [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]))


(defmethod ig/init-key :blaze.interaction.history/type [_ context]
  (log/info "Init FHIR history type interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "history-type")))
