(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.external-data :as ed]
   [blaze.elm.compiler.external-data-spec]
   [blaze.elm.compiler.spec]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.spec]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.cql.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef cql/evaluate-expression-1
  :args (s/cat :context ::expr/context
               :subject (s/nilable ed/resource?)
               :name string?
               :expression ::c/expression)
  :ret ac/completable-future?)

(s/fdef cql/evaluate-expression
  :args (s/cat :context ::cql/evaluate-expression-context :name string?
               :subject-type :fhir.resource/type)
  :ret ac/completable-future?)

(s/fdef cql/evaluate-individual-expression
  :args (s/cat :context ::cql/evaluate-expression-context
               :subject ed/resource?
               :name string?)
  :ret ac/completable-future?)

(s/fdef cql/stratum-expression-evaluator
  :args (s/cat :context ::cql/stratum-expression-evaluator-context
               :name string?)
  :ret (s/or :evaluator fn? :anomaly ::anom/anomaly))

(s/fdef cql/stratum-expression-evaluators
  :args (s/cat :context ::cql/stratum-expression-evaluator-context
               :name (s/coll-of string?))
  :ret (s/or :evaluators (s/coll-of fn?) :anomaly ::anom/anomaly))
