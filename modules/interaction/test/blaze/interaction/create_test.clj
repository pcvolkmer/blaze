(ns blaze.interaction.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.executors :as ex]
    [blaze.fhir.response.create-spec]
    [blaze.fhir.spec.type]
    [blaze.interaction.create]
    [blaze.interaction.test-util :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock Instant ZoneId]
    [java.util Random]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def executor (ex/single-thread-executor))


(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(def ^:private base-url "base-url-134418")


(def fixed-random
  (proxy [Random] []
    (nextLong []
      0)))


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/create
         {:node node
          :executor executor
          :clock clock
          :rng-fn (constantly fixed-random)}})
      :blaze.interaction/create))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node)
        (assoc request
          :blaze/base-url base-url
          ::reitit/router router)))))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/create nil})
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/create {}})
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid executor"
    (given-thrown (ig/init {:blaze.interaction/create {:executor "foo"}})
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 3 :pred] := `ex/executor?
      [:explain ::s/problems 3 :val] := "foo")))


(deftest handler-test
  (testing "Returns Error on missing body"
    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invalid"
        [:issue 0 :diagnostics] := "Missing HTTP body.")))

  (testing "Returns Error on type mismatch"
    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/match {:data {:fhir.resource/type "Patient"}}
            :body {:fhir/type :fhir/Observation}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invariant"
        [:issue 0 :details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
        [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_TYPE_MISMATCH"
        [:issue 0 :diagnostics] := "Resource type `Observation` doesn't match the endpoint type `Patient`.")))

  (testing "Returns Error violated referential integrity"
    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/match {:data {:fhir.resource/type "Observation"}}
            :body {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}})]

      (is (= 409 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"conflict"
        [:issue 0 :diagnostics] :=
        "Referential integrity violated. Resource `Patient/0` doesn't exist.")))

  (testing "On newly created resource"
    (testing "with no Prefer header"
      (let [{:keys [status headers body]}
            ((handler-with [])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :body {:fhir/type :fhir/Patient}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "base-url-134418/Patient/AAAAAAAAAAAAAAAA/_history/1"
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Patient
          :id := "AAAAAAAAAAAAAAAA"
          [:meta :versionId] := #fhir/id"1"
          [:meta :lastUpdated] := Instant/EPOCH)))

    (testing "with return=minimal Prefer header"
      (let [{:keys [status headers body]}
            ((handler-with [])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :headers {"prefer" "return=minimal"}
              :body {:fhir/type :fhir/Patient}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "base-url-134418/Patient/AAAAAAAAAAAAAAAA/_history/1"
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (is (nil? body))))

    (testing "with return=representation Prefer header"
      (let [{:keys [status headers body]}
            ((handler-with [])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :headers {"prefer" "return=representation"}
              :body {:fhir/type :fhir/Patient}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "base-url-134418/Patient/AAAAAAAAAAAAAAAA/_history/1"
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Patient
          :id := "AAAAAAAAAAAAAAAA"
          [:meta :versionId] := #fhir/id"1"
          [:meta :lastUpdated] := Instant/EPOCH)))

    (testing "with return=OperationOutcome Prefer header"
      (let [{:keys [status headers body]}
            ((handler-with [])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :headers {"prefer" "return=OperationOutcome"}
              :body {:fhir/type :fhir/Patient}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "base-url-134418/Patient/AAAAAAAAAAAAAAAA/_history/1"
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (is (= :fhir/OperationOutcome (:fhir/type body))))))

  (testing "conditional create"
    (testing "with non-matching query"
      (testing "on empty database"
        (let [{:keys [status]}
              ((handler-with [])
               {::reitit/match {:data {:fhir.resource/type "Patient"}}
                :headers {"if-none-exist" "identifier=212154"}
                :body {:fhir/type :fhir/Patient}})]

          (testing "the patient is created"
            (is (= 201 status)))))

      (testing "on non-matching patient"
        (let [{:keys [status]}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"
                          :identifier
                          [#fhir/Identifier{:value "094808"}]}]]])
               {::reitit/match {:data {:fhir.resource/type "Patient"}}
                :headers {"if-none-exist" "identifier=212154"}
                :body {:fhir/type :fhir/Patient}})]

          (testing "the patient is created"
            (is (= 201 status))))))

    (testing "with matching patient"
      (let [{:keys [status body]}
            ((handler-with
               [[[:put {:fhir/type :fhir/Patient :id "0"
                        :identifier
                        [#fhir/Identifier{:value "095156"}]}]]])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :headers {"if-none-exist" "identifier=095156"}
              :body {:fhir/type :fhir/Patient}})]

        (testing "the existing patient is returned"
          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Patient
            :id := "0"))))

    (testing "with multiple matching patients"
      (let [{:keys [status body]}
            ((handler-with
               [[[:put {:fhir/type :fhir/Patient :id "0"
                        :birthDate #fhir/date"2020"}]
                 [:put {:fhir/type :fhir/Patient :id "1"
                        :birthDate #fhir/date"2020"}]]])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :headers {"if-none-exist" "birthdate=2020"}
              :body {:fhir/type :fhir/Patient}})]

        (testing "a precondition failure is returned"
          (is (= 412 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"conflict"
            [:issue 0 :diagnostics] := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))))
