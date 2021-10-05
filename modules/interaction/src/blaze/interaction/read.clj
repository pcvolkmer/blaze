(ns blaze.interaction.read
  "FHIR read interaction.

  https://www.hl7.org/fhir/http.html#read"
  (:require
    [blaze.anomaly :as ba :refer [if-ok]]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time ZonedDateTime ZoneId]
    [java.time.format DateTimeFormatter]))


(set! *warn-on-reflection* true)


(def ^:private gmt (ZoneId/of "GMT"))


(defn- last-modified [{:blaze.db.tx/keys [instant]}]
  (->> (ZonedDateTime/ofInstant instant gmt)
       (.format DateTimeFormatter/RFC_1123_DATE_TIME)))


(defn- etag [{:blaze.db/keys [t]}]
  (str "W/\"" t "\""))


(defn- resource-handle [db type id]
  (if-let [{:keys [op t] :as handle} (d/resource-handle db type id)]
    (if (identical? :delete op)
      (let [tx (d/tx db t)]
        (ba/not-found
          (format "Resource `%s/%s` was deleted." type id)
          :http/status 410
          :http/headers
          [["Last-Modified" (last-modified tx)]
           ["ETag" (etag tx)]]
          :fhir/issue "deleted"))
      handle)
    (ba/not-found
      (format "Resource `%s/%s` was not found." type id)
      :fhir/issue "not-found")))


(defn- pull [db type id]
  (if-ok [resource-handle (resource-handle db type id)]
    (d/pull db resource-handle)
    ac/completed-future))


(defn- response [resource]
  (let [{:blaze.db/keys [tx]} (meta resource)]
    (-> (ring/response resource)
        (ring/header "Last-Modified" (last-modified tx))
        (ring/header "ETag" (etag tx)))))


(def ^:private handler
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params :blaze/keys [db]}]
    (do-sync [resource (pull db type id)]
      (response resource))))


(defn- wrap-invalid-vid [handler]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id vid]} :path-params :as request}]
    (if (and vid (not (re-matches #"\d+" vid)))
      (ac/completed-future
        (ba/not-found
          (format "Resource `%s/%s` with versionId `%s` was not found." type id vid)
          :fhir/issue "not-found"))
      (handler request))))


(defn- wrap-interaction-name [handler]
  (fn [{{:keys [vid]} :path-params :as request}]
    (do-sync [response (handler request)]
      (assoc response :fhir/interaction-name (if vid "vread" "read")))))


(defmethod ig/init-key :blaze.interaction/read [_ _]
  (log/info "Init FHIR read interaction handler")
  (-> handler
      wrap-invalid-vid
      wrap-interaction-name
      wrap-observe-request-duration))