(ns blaze.db.resource-store.spec
  (:require
    [blaze.db.resource-store :as rs]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/resource-store
  #(satisfies? rs/ResourceStore %))
