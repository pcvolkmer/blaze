(ns blaze.middleware.fhir.db-test
  "White box mocking is used here because it's difficult to differentiate
  between the database value acquisition methods if one only sees the database
  value as result."
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.middleware.fhir.db :as db]
    [blaze.middleware.fhir.db-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [java.util.concurrent TimeUnit]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (st/unstrument `db/wrap-db)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def handler (comp ac/completed-future :blaze/db))


(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally ex-cause))))


(deftest wrap-db-test
  (testing "uses existing database value"
    (is (= ::db @((db/wrap-db handler ::node) {:blaze/db ::db}))))

  (testing "with invalid vid"
    (with-redefs
      [d/sync
       (fn [node]
         (assert (= ::node node))
         (ac/completed-future ::db))]

      (is (= ::db @((db/wrap-db handler ::node) {:path-params {:vid "a"}})))))

  (testing "uses the vid for database value acquisition"
    (with-redefs
      [d/sync
       (fn [node t]
         (assert (= ::node node))
         (assert (= 114418 t))
         (ac/completed-future ::db))
       d/as-of
       (fn [db t]
         (assert (= ::db db))
         (assert (= 114418 t))
         ::as-of-db)]

      (is (= ::as-of-db @((db/wrap-db handler ::node) {:path-params {:vid "114418"}})))))

  (testing "uses __t for database value acquisition"
    (with-redefs
      [d/sync
       (fn [node t]
         (assert (= ::node node))
         (assert (= 114429 t))
         (ac/completed-future ::db))]

      (is (= ::db @((db/wrap-db handler ::node) {:query-params {"__t" "114429"}})))))

  (testing "uses sync for database value acquisition"
    (with-redefs
      [d/sync
       (fn [node]
         (assert (= ::node node))
         (ac/completed-future ::db))]

      (is (= ::db @((db/wrap-db handler ::node) {}))))

    (testing "fails on timeout"
      (with-redefs
        [d/sync
         (fn [node]
           (assert (= ::node node))
           (ac/supply-async (constantly ::db) (ac/delayed-executor 3 TimeUnit/SECONDS)))]

        (given @((-> handler (db/wrap-db ::node) wrap-error) {})
          [ex-data ::anom/category] := ::anom/busy)))

    (testing "returns other errors"
      (with-redefs
        [d/sync
         (fn [node]
           (assert (= ::node node))
           (ac/completed-future ::db))]

        (given @((-> (fn [_] (ac/failed-future (Exception. "msg-114913")))
                  (db/wrap-db ::node)
                  wrap-error) {})
          ex-message := "msg-114913")))))
