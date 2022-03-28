(ns blaze.interaction.util
  (:require
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.luid :as luid]
    [clojure.string :as str]))


(defn etag->t [etag]
  (when etag
    (let [[_ t] (re-find #"W/\"(\d+)\"" etag)]
      (some-> t parse-long))))


(defn- remove-query-param? [[k]]
  (and (str/starts-with? k "_")
       (not (#{"_id" "_list" "_profile" "_lastUpdated"} k))
       (not (str/starts-with? k "_has"))))


(defn clauses [query-params]
  (into
    []
    (comp
      (remove remove-query-param?)
      (mapcat
        (fn [[k v]]
          (map
            #(into [k] (map str/trim) (str/split % #","))
            (fhir-util/to-seq v)))))
    query-params))


(defn luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))


(defn successive-luids [{:keys [clock rng-fn]}]
  (luid/successive-luids clock (rng-fn)))


(defn t [db]
  (or (d/as-of-t db) (d/basis-t db)))
