(ns blaze.rest-api.spec
  (:require
   [blaze.db.spec]
   [blaze.executors :as ex]
   [blaze.rest-api :as-alias rest-api]
   [blaze.spec]
   [buddy.auth.protocols :as p]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]))

(set! *warn-on-reflection* true)

(s/def :blaze/rest-api
  fn?)

(s/def ::rest-api/admin-node
  :blaze.db/node)

(s/def ::rest-api/auth-backends
  (s/coll-of #(satisfies? p/IAuthentication %)))

(s/def ::rest-api/search-system-handler
  fn?)

(s/def ::rest-api/transaction-handler
  fn?)

(s/def ::rest-api/history-system-handler
  fn?)

(s/def ::rest-api/async-status-handler
  fn?)

(s/def ::rest-api/async-status-cancel-handler
  fn?)

(s/def ::rest-api/capabilities-handler
  fn?)

(s/def :blaze.rest-api.resource-pattern/type
  (s/or :name string? :default #{:default}))

(def interaction-code?
  #{:read
    :vread
    :update
    :patch
    :delete
    :history-instance
    :history-type
    :create
    :search-type})

(s/def :blaze.rest-api.interaction/handler
  (s/or :ref ig/ref? :handler fn?))

(s/def :blaze.rest-api.interaction/doc
  string?)

(s/def ::rest-api/interaction
  (s/keys
   :req
   [:blaze.rest-api.interaction/handler]
   :opt
   [:blaze.rest-api.interaction/doc]))

;; Interactions keyed there code
(s/def :blaze.rest-api.resource-pattern/interactions
  (s/map-of interaction-code? ::rest-api/interaction))

(s/def ::rest-api/resource-pattern
  (s/keys
   :req
   [:blaze.rest-api.resource-pattern/type
    :blaze.rest-api.resource-pattern/interactions]))

(s/def ::rest-api/resource-patterns
  (s/coll-of ::rest-api/resource-pattern))

(s/def :blaze.rest-api.compartment/search-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def ::rest-api/compartment
  (s/keys
   :req
   [:blaze.rest-api.compartment/code
    :blaze.rest-api.compartment/search-handler]))

(s/def ::rest-api/compartments
  (s/coll-of ::rest-api/compartment))

(s/def :blaze.rest-api.operation/code
  string?)

(s/def :blaze.rest-api.operation/def-uri
  string?)

(s/def :blaze.rest-api.operation/resource-types
  (s/coll-of string?))

(s/def :blaze.rest-api.operation/system-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def :blaze.rest-api.operation/type-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def :blaze.rest-api.operation/instance-handler
  (s/or :ref ig/ref? :handler fn?))

(s/def :blaze.rest-api.operation/documentation
  string?)

(s/def ::rest-api/operation
  (s/keys
   :req
   [:blaze.rest-api.operation/code
    :blaze.rest-api.operation/def-uri]
   :opt
   [:blaze.rest-api.operation/resource-types
    :blaze.rest-api.operation/system-handler
    :blaze.rest-api.operation/type-handler
    :blaze.rest-api.operation/instance-handler
    :blaze.rest-api.operation/documentation]))

(s/def ::rest-api/operations
  (s/coll-of ::rest-api/operation))

(s/def :blaze.rest-api.json-parse/executor
  ex/executor?)

(s/def ::rest-api/structure-definitions
  (s/coll-of map?))
