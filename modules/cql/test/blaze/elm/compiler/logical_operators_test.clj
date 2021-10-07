(ns blaze.elm.compiler.logical-operators-test
  "13. Logical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.logical-operators]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 13. Logical Operators

;; 13.1. And
;;
;; The And operator returns the logical conjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is false, the result is false; if both arguments are true,
;; the result is true; otherwise, the result is null. Note also that ELM does
;; not prescribe short-circuit evaluation.
(deftest compile-and-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/and [x y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" false
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" false
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} false

      {:type "Null"} #elm/boolean"true" nil
      {:type "Null"} #elm/boolean"false" false
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (are [x y res] (= res (tu/dynamic-compile-eval (elm/and [x y])))
      #elm/boolean"true" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" #elm/boolean"true" true
      #elm/parameter-ref"true" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref"true" nil

      #elm/boolean"true" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" #elm/boolean"true" false
      #elm/parameter-ref"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" {:type "Null"} false
      {:type "Null"} #elm/parameter-ref"false" false

      #elm/boolean"false" #elm/parameter-ref"nil" false
      #elm/parameter-ref"nil" #elm/boolean"false" false
      #elm/boolean"true" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"true" nil
      #elm/parameter-ref"nil" #elm/parameter-ref"nil" nil)))


;; 13.2. Implies
;;
;; The Implies operator returns the logical implication of its arguments. Note
;; that this operator is defined using 3-valued logic semantics. This means that
;; if the left operand evaluates to true, this operator returns the boolean
;; evaluation of the right operand. If the left operand evaluates to false, this
;; operator returns true. Otherwise, this operator returns true if the right
;; operand evaluates to true, and null otherwise.
;;
;; Note that implies may use short-circuit evaluation in the case that the first
;; operand evaluates to false.
(deftest compile-implies-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/or [(elm/not x) y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" false
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" true
      #elm/boolean"false" {:type "Null"} true

      {:type "Null"} #elm/boolean"true" true
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil)))


;; 13.3. Not
;;
;; The Not operator returns the logical negation of its argument. If the
;; argument is true, the result is false; if the argument is false, the result
;; is true; otherwise, the result is null.
(deftest compile-not-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/not x)))
      #elm/boolean"true" false
      #elm/boolean"false" true
      {:type "Null"} nil))

  (testing "Dynamic"
    (are [x res] (= res (tu/dynamic-compile-eval (elm/not x)))
      #elm/parameter-ref"true" false
      #elm/parameter-ref"false" true
      #elm/parameter-ref"nil" nil)))


;; 13.4. Or
;;
;; The Or operator returns the logical disjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is true, the result is true; if both arguments are false, the
;; result is false; otherwise, the result is null. Note also that ELM does not
;; prescribe short-circuit evaluation.
(deftest compile-or-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/or [x y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" true
      #elm/boolean"true" {:type "Null"} true

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} nil

      {:type "Null"} #elm/boolean"true" true
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (are [x y res] (= res (tu/dynamic-compile-eval (elm/or [x y])))
      #elm/boolean"false" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" #elm/boolean"false" true
      #elm/parameter-ref"true" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" {:type "Null"} true
      {:type "Null"} #elm/parameter-ref"true" true

      #elm/boolean"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" #elm/boolean"false" false
      #elm/parameter-ref"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref"false" nil

      #elm/boolean"true" #elm/parameter-ref"nil" true
      #elm/parameter-ref"nil" #elm/boolean"true" true
      #elm/boolean"false" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"false" nil
      #elm/parameter-ref"nil" #elm/parameter-ref"nil" nil)))


;; 13.5. Xor
;;
;; The Xor operator returns the exclusive or of its arguments. Note that this
;; operator is defined using 3-valued logic semantics. This means that the
;; result is true if and only if one argument is true and the other is false,
;; and that the result is false if and only if both arguments are true or both
;; arguments are false. If either or both arguments are null, the result is
;; null.
(deftest compile-xor-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/xor [x y])))
      #elm/boolean"true" #elm/boolean"true" false
      #elm/boolean"true" #elm/boolean"false" true
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} nil

      {:type "Null"} #elm/boolean"true" nil
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (are [x y res] (= res (tu/dynamic-compile-eval (elm/xor [x y])))
      #elm/boolean"true" #elm/parameter-ref"true" false
      #elm/parameter-ref"true" #elm/boolean"true" false
      #elm/boolean"false" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" #elm/boolean"false" true
      #elm/parameter-ref"true" #elm/parameter-ref"true" false

      #elm/boolean"true" #elm/parameter-ref"false" true
      #elm/parameter-ref"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" #elm/boolean"false" false
      #elm/parameter-ref"false" #elm/parameter-ref"false" false

      #elm/boolean"true" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"true" nil
      #elm/boolean"false" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"false" nil
      {:type "Null"} #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" {:type "Null"} nil
      #elm/parameter-ref"nil" #elm/parameter-ref"nil" nil)))
