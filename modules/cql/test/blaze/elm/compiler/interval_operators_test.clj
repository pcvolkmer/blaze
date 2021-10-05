(ns blaze.elm.compiler.interval-operators-test
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.interval-operators]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.interval :refer [interval]]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.fhir.spec.type.system :as system]
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


(def interval-zero #elm/interval [#elm/integer"0" #elm/integer"0"])

;; 19.1. Interval
;;
;; The Interval selector defines an interval value. An interval must be defined
;; using a point type that supports comparison, as well as Successor and
;; Predecessor operations, and Minimum and Maximum Value operations.
;;
;; The low and high bounds of the interval may each be defined as open or
;; closed. Following standard terminology usage in interval mathematics, an open
;; interval is defined to exclude the specified point, whereas a closed interval
;; includes the point. The default is closed, indicating an inclusive interval.
;;
;; The low and high elements are both optional. If the low element is not
;; specified, the low bound of the resulting interval is null. If the high
;; element is not specified, the high bound of the resulting interval is null.
;;
;; The static type of the low bound determines the type of the interval, and the
;; high bound must be of the same type.
;;
;; If the low bound of the interval is null and open, the low bound of the
;; interval is interpreted as unknown, and computations involving the low
;; boundary will result in null.
;;
;; If the low bound of the interval is null and closed, the interval is
;; interpreted to start at the minimum value of the point type, and computations
;; involving the low boundary will be performed with that value.
;;
;; If the high bound of the interval is null and open, the high bound of the
;; interval is unknown, and computations involving the high boundary will result
;; in null.
;;
;; If the high bound of the interval is null and closed, the interval is
;; interpreted to end at the maximum value of the point type, and computations
;; involving the high boundary will be performed with that interpretation.
(deftest compile-interval-test
  (testing "Static"
    (are [elm res] (= res (c/compile {} elm))
      #elm/interval [#elm/integer"1" #elm/integer"2"] (interval 1 2)
      #elm/interval [#elm/decimal"1" #elm/decimal"2"] (interval 1M 2M)

      #elm/interval [:< #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
                     #elm/integer"1"]
      (interval nil 1)

      #elm/interval [#elm/integer"1"
                     #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}] :>]
      (interval 1 nil)

      #elm/interval [:< #elm/integer"1" #elm/integer"2"] (interval 2 2)
      #elm/interval [#elm/integer"1" #elm/integer"2" :>] (interval 1 1)
      #elm/interval [:< #elm/integer"1" #elm/integer"3" :>] (interval 2 2)))

  (testing "Dynamic"
    (are [elm res] (= res (tu/dynamic-compile-eval elm))
      (elm/interval [:< (elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/parameter-ref"nil"])
                     #elm/integer"1"])
      (interval nil 1)

      (elm/interval [#elm/integer"1"
                     (elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/parameter-ref"nil"]) :>])
      (interval 1 nil)))

  (testing "Invalid interval"
    (are [elm] (thrown? Exception (core/-eval (c/compile {} elm) {} nil nil))
      #elm/interval [#elm/integer"5" #elm/integer"3"])))


;; 19.2. After
;;
;; The After operator is defined for Intervals, as well as Date, DateTime, and
;; Time values.
;;
;; For the Interval overload, the After operator returns true if the first
;; interval starts after the second one ends. In other words, if the starting
;; point of the first interval is greater than the ending point of the second
;; interval using the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; For the Date, DateTime, and Time overloads, the After operator returns true
;; if the first datetime is after the second datetime at the specified level of
;; precision. The comparison is performed by considering each precision in
;; order, beginning with years (or hours for time values). If the values are the
;; same, comparison proceeds to the next precision; if the first value is
;; greater than the second, the result is true; if the first value is less than
;; the second, the result is false; if either input has no value for the
;; precision, the comparison stops and the result is null; if the specified
;; precision has been reached, the comparison stops and the result is false.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; As with all date and time calculations, comparisons are performed respecting
;; the timezone offset.
;;
;; If either argument is null, the result is null.
(deftest compile-after-test
  (testing "Interval"
    (testing "null arguments result in null"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/after [x y])) {} nil nil))
        interval-zero {:type "Null"} nil
        {:type "Null"} interval-zero nil))

    (testing "if both intervals are closed, the start of the first (3) has to be greater then the end of the second (2)"
      (are [x y res] (= res (tu/compile-binop elm/after elm/interval x y))
        [#elm/integer"3" #elm/integer"4"]
        [#elm/integer"1" #elm/integer"2"] true
        [#elm/integer"2" #elm/integer"3"]
        [#elm/integer"1" #elm/integer"2"] false))

    (testing "if one of the intervals is open, start and end can be the same (2)"
      (are [x y res] (= res (tu/compile-binop elm/after elm/interval x y))
        [#elm/integer"2" #elm/integer"3"]
        [#elm/integer"1" #elm/integer"2" :>] true
        [:< #elm/integer"2" #elm/integer"3"]
        [#elm/integer"1" #elm/integer"2"] true
        [:< #elm/integer"2" #elm/integer"3"]
        [#elm/integer"1" #elm/integer"2" :>] true))

    (testing "if both intervals are open, start and end can overlap slightly"
      (are [x y res] (= res (tu/compile-binop elm/after elm/interval x y))
        [:< #elm/integer"2" #elm/integer"4"]
        [#elm/integer"1" #elm/integer"3" :>] true))

    (testing "if one of the relevant bounds is infinity, the result is false"
      (are [x y res] (= res (tu/compile-binop elm/after elm/interval x y))
        [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer"3"]
        [#elm/integer"1" #elm/integer"2"] false
        [#elm/integer"2" #elm/integer"3"]
        [#elm/integer"1" {:type "Null"}] false))

    (testing "if the second interval has an unknown high bound, the result is null"
      (are [x y res] (= res (tu/compile-binop elm/after elm/interval x y))
        [#elm/integer"2" #elm/integer"3"]
        [#elm/integer"1" {:type "Null"} :>] nil)))

  (testing "Date"
    (are [x y res] (= res (tu/compile-binop elm/after elm/date x y))
      "2019" "2018" true
      "2019" "2019" false
      "2019" "2020" false
      "2019-04" "2019-03" true
      "2019-04" "2019-04" false
      "2019-04" "2019-05" false
      "2019-04-17" "2019-04-16" true
      "2019-04-17" "2019-04-17" false
      "2019-04-17" "2019-04-18" false)

    (tu/testing-binary-null elm/after #elm/date"2019")
    (tu/testing-binary-null elm/after #elm/date"2019-04")
    (tu/testing-binary-null elm/after #elm/date"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/after elm/date x y "year"))
        "2019" "2018" true
        "2019" "2019" false
        "2019" "2020" false
        "2019-04" "2019-03" false
        "2019-04" "2019-04" false
        "2019-04" "2019-05" false
        "2019-04-17" "2019-04-16" false
        "2019-04-17" "2019-04-17" false
        "2019-04-17" "2019-04-18" false)))

  (testing "DateTime"
    (are [x y res] (= res (tu/compile-binop elm/after elm/date-time x y))
      "2019" "2018" true
      "2019" "2019" false
      "2019" "2020" false
      "2019-04" "2019-03" true
      "2019-04" "2019-04" false
      "2019-04" "2019-05" false
      "2019-04-17" "2019-04-16" true
      "2019-04-17" "2019-04-17" false
      "2019-04-17" "2019-04-18" false)

    (tu/testing-binary-null elm/after #elm/date-time"2019")
    (tu/testing-binary-null elm/after #elm/date-time"2019-04")
    (tu/testing-binary-null elm/after #elm/date-time"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/after elm/date-time
                                                        x y "year"))
        "2019" "2018" true
        "2019" "2019" false
        "2019" "2020" false
        "2019-04" "2019-03" false
        "2019-04" "2019-04" false
        "2019-04" "2019-05" false
        "2019-04-17" "2019-04-16" false
        "2019-04-17" "2019-04-17" false
        "2019-04-17" "2019-04-18" false))))


;; 19.3. Before
;;
;; The Before operator is defined for Intervals, as well as Date, DateTime, and
;; Time values.
;;
;; For the Interval overload, the Before operator returns true if the first
;; interval ends before the second one starts. In other words, if the ending
;; point of the first interval is less than the starting point of the second
;; interval, using the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; For the Date, DateTime, and Time overloads, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is less than the second, the result is true; if
;; the first value is greater than the second, the result is false; if either
;; input has no value for the precision, the comparison stops and the result is
;; null; if the specified precision has been reached, the comparison stops and
;; the result is false.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; As with all date and time calculations, comparisons are performed respecting
;; the timezone offset.
;;
;; If either argument is null, the result is null.
(deftest compile-before-test
  (testing "Interval"
    (testing "null arguments result in null"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/before [x y])) {} nil nil))
        interval-zero {:type "Null"} nil
        {:type "Null"} interval-zero nil))

    (testing "if both intervals are closed, the end of the first (2) has to be less then the start of the second (3)"
      (are [x y res] (= res (tu/compile-binop elm/before elm/interval x y))
        [#elm/integer"1" #elm/integer"2"]
        [#elm/integer"3" #elm/integer"4"] true
        [#elm/integer"1" #elm/integer"2"]
        [#elm/integer"2" #elm/integer"3"] false))

    (testing "if one of the intervals is open, start and end can be the same (2)"
      (are [x y res] (= res (tu/compile-binop elm/before elm/interval x y))
        [#elm/integer"1" #elm/integer"2" :>]
        [#elm/integer"2" #elm/integer"3"] true
        [#elm/integer"1" #elm/integer"2"]
        [:< #elm/integer"2" #elm/integer"3"] true
        [#elm/integer"1" #elm/integer"2" :>]
        [:< #elm/integer"2" #elm/integer"3"] true))

    (testing "if both intervals are open, start and end can overlap slightly"
      (are [x y res] (= res (tu/compile-binop elm/before elm/interval x y))
        [#elm/integer"1" #elm/integer"3" :>]
        [:< #elm/integer"2" #elm/integer"4"] true))

    (testing "if one of the relevant bounds is infinity, the result is false"
      (are [x y res] (= res (tu/compile-binop elm/before elm/interval x y))
        [#elm/integer"1" {:type "Null"}]
        [#elm/integer"2" #elm/integer"3"] false
        [#elm/integer"1" #elm/integer"2"]
        [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer"3"] false))

    (testing "if the second interval has an unknown low bound, the result is null"
      (are [x y res] (= res (tu/compile-binop elm/before elm/interval x y))
        [#elm/integer"1" #elm/integer"2"]
        [:< {:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer"3"] nil)))

  (testing "Date"
    (are [x y res] (= res (tu/compile-binop elm/before elm/date x y))
      "2019" "2020" true
      "2019" "2019" false
      "2019" "2018" false
      "2019-04" "2019-05" true
      "2019-04" "2019-04" false
      "2019-04" "2019-03" false
      "2019-04-17" "2019-04-18" true
      "2019-04-17" "2019-04-17" false
      "2019-04-17" "2019-04-16" false)

    (tu/testing-binary-null elm/before #elm/date"2019")
    (tu/testing-binary-null elm/before #elm/date"2019-04")
    (tu/testing-binary-null elm/before #elm/date"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/before elm/date x y
                                                        "year"))
        "2019" "2020" true
        "2019" "2019" false
        "2019" "2018" false
        "2019-04" "2019-05" false
        "2019-04" "2019-04" false
        "2019-04" "2019-03" false
        "2019-04-17" "2019-04-18" false
        "2019-04-17" "2019-04-17" false
        "2019-04-17" "2019-04-16" false)))

  (testing "DateTime"
    (are [x y res] (= res (tu/compile-binop elm/before elm/date-time x y))
      "2019" "2020" true
      "2019" "2019" false
      "2019" "2018" false
      "2019-04" "2019-05" true
      "2019-04" "2019-04" false
      "2019-04" "2019-03" false
      "2019-04-17" "2019-04-18" true
      "2019-04-17" "2019-04-17" false
      "2019-04-17" "2019-04-16" false)

    (tu/testing-binary-null elm/before #elm/date-time"2019")
    (tu/testing-binary-null elm/before #elm/date-time"2019-04")
    (tu/testing-binary-null elm/before #elm/date-time"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/before elm/date-time
                                                        x y "year"))
        "2019" "2020" true
        "2019" "2019" false
        "2019" "2018" false
        "2019-04" "2019-05" false
        "2019-04" "2019-04" false
        "2019-04" "2019-03" false
        "2019-04-17" "2019-04-18" false
        "2019-04-17" "2019-04-17" false
        "2019-04-17" "2019-04-16" false))))


;; 19.4. Collapse
;;
;; The Collapse operator returns the unique set of intervals that completely
;; covers the ranges present in the given list of intervals.
;;
;; The operation is performed by combining successive intervals in the input
;; that either overlap or meet, using the semantics defined for the Overlaps and
;; Meets operators. Note that because those operators are themselves defined in
;; terms of interval successor and predecessor operators, sets of Date-,
;; DateTime-, and Time-based intervals that are only defined to a particular
;; precision will calculate meets and overlaps at that precision. For example, a
;; list of DateTime-based intervals where the boundaries are all specified to
;; the hour will collapse at the hour precision, unless the collapse precision
;; is overridden with the per argument.
;;
;; The per argument determines the precision at which the collapse is computed
;; and must be a quantity-valued expression compatible with the interval point
;; type. For numeric intervals, this means a default unit ('1'), for Date-,
;; DateTime-, and Time-valued intervals, this means a temporal duration.
;;
;; If the per argument is null, the default unit interval for the point type of
;; the intervals involved will be used (i.e. an interval with the same starting
;; and ending boundary).
;;
;; If the list of intervals is empty, the result is empty. If the list of
;; intervals contains a single interval, the result is a list with that
;; interval. If the list of intervals contains nulls, they will be excluded from
;; the resulting list.
;;
;; If the source argument is null, the result is null.
(deftest compile-collapse-test
  (testing "Integer"
    (are [source per res] (= res (core/-eval (c/compile {} (elm/collapse [source per])) {} nil nil))
      #elm/list [#elm/interval [#elm/integer"1" #elm/integer"2"]]
      {:type "Null"}
      [(interval 1 2)]

      #elm/list [#elm/interval [#elm/integer"1" #elm/integer"2"]
                 #elm/interval [#elm/integer"2" #elm/integer"3"]]
      {:type "Null"}
      [(interval 1 3)]

      #elm/list [{:type "Null"}] {:type "Null"} []
      #elm/list [{:type "Null"} {:type "Null"}] {:type "Null"} []
      #elm/list [] {:type "Null"} []

      {:type "Null"} {:type "Null"} nil))

  (testing "DateTime"
    (are [source per res] (= res (core/-eval (c/compile {} (elm/collapse [source per])) {} nil nil))
      #elm/list [#elm/interval [#elm/date-time"2012-01-01" #elm/date-time"2012-01-15"]
                 #elm/interval [#elm/date-time"2012-01-16" #elm/date-time"2012-05-25"]]
      {:type "Null"}
      [(interval (system/date-time 2012 1 1) (system/date-time 2012 5 25))])))


;; 19.5. Contains
;;
;; The Contains operator returns true if the first operand contains the second.
;;
;; There are two overloads of this operator: 1. List, T : The type of T must be
;; the same as the element type of the list. 2. Interval, T : The type of T must
;; be the same as the point type of the interval.
;;
;; For the List, T overload, this operator returns true if the given element is
;; in the list, using equality semantics.
;;
;; For the Interval, T overload, this operator returns true if the given point
;; is greater than or equal to the starting point of the interval, and less than
;; or equal to the ending point of the interval. For open interval boundaries,
;; exclusive comparison operators are used. For closed interval boundaries, if
;; the interval boundary is null, the result of the boundary comparison is
;; considered true. If precision is specified and the point type is a Date,
;; DateTime, or Time type, comparisons used in the operation are performed at
;; the specified precision.
;;
;; If either argument is null, the result is null.
(deftest compile-contains-test
  (testing "Interval"
    (testing "Null"
      (are [interval x res] (= res (core/-eval (c/compile {} (elm/contains [interval x])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [interval x res] (= res (core/-eval (c/compile {} (elm/contains [interval x])) {} nil nil))
        #elm/interval [#elm/integer"1" #elm/integer"1"] #elm/integer"1" true
        #elm/interval [#elm/integer"1" #elm/integer"1"] #elm/integer"2" false)))

  (testing "List"
    (are [list x res] (= res (core/-eval (c/compile {} (elm/contains [list x])) {} nil nil))
      #elm/list [] #elm/integer"1" false

      #elm/list [#elm/integer"1"] #elm/integer"1" true
      #elm/list [#elm/integer"1"] #elm/integer"2" false

      #elm/list [#elm/quantity[1 "m"]] #elm/quantity[100 "cm"] true

      #elm/list [#elm/date"2019"] #elm/date"2019-01" false

      #elm/list [] {:type "Null"} nil)))


;; 19.6. End
;;
;; The End operator returns the ending point of an interval.
;;
;; If the high boundary of the interval is open, this operator returns the
;; Predecessor of the high value of the interval. Note that if the high value of
;; the interval is null, the result is null.
;;
;; If the high boundary of the interval is closed and the high value of the
;; interval is not null, this operator returns the high value of the interval.
;; Otherwise, the result is the maximum value of the point type of the interval.
;;
;; If the argument is null, the result is null.
(deftest compile-end-test
  (testing "Null"
    (are [x res] (= res (core/-eval (c/compile {} {:type "End" :operand x}) {} nil nil))
      {:type "Null"} nil))

  (testing "Integer"
    (are [x res] (= res (core/-eval (c/compile {} {:type "End" :operand x}) {} nil nil))
      #elm/interval [#elm/integer"1" #elm/integer"2"] 2
      #elm/interval [#elm/integer"1" #elm/integer"2" :>] 1
      #elm/interval [#elm/integer"1" {:type "Null"}] Integer/MAX_VALUE))

  (testing "Decimal"
    (are [x res] (= res (core/-eval (c/compile {} {:type "End" :operand x}) {} nil nil))
      #elm/interval [#elm/decimal"1" #elm/decimal"2.1"] 2.1M
      #elm/interval [#elm/decimal"1" #elm/decimal"2.1" :>] 2.09999999M
      #elm/interval [#elm/decimal"1" {:type "Null"}] decimal/max)))


;; 19.7. Ends
;;
;; The Ends operator returns true if the first interval ends the second. In
;; other words, if the starting point of the first interval is greater than or
;; equal to the starting point of the second, and the ending point of the first
;; interval is equal to the ending point of the second.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-ends-test
  (testing "Null"
    (are [x y res] (= res (core/-eval (c/compile {} {:type "Ends" :operand [x y]}) {} nil nil))
      {:type "Null"} interval-zero nil
      interval-zero {:type "Null"} nil))

  (testing "Integer"
    (are [x y res] (= res (core/-eval (c/compile {} {:type "Ends" :operand [x y]}) {} nil nil))
      #elm/interval [#elm/integer"1" #elm/integer"3"]
      #elm/interval [#elm/integer"1" #elm/integer"3"] true
      #elm/interval [#elm/integer"2" #elm/integer"3"]
      #elm/interval [#elm/integer"1" #elm/integer"3"] true
      #elm/interval [#elm/integer"1" #elm/integer"3"]
      #elm/interval [#elm/integer"2" #elm/integer"3"] false)))


;; 19.10. Except
;;
;; The Except operator returns the set difference of the two arguments.
;;
;; This operator has two overloads: 1. List, List 2. Interval, Interval
;;
;; For the list overload, this operator returns a list with the elements that
;; appear in the first operand, that do not appear in the second operand, using
;; equality semantics. The operator is defined with set semantics, meaning that
;; each element will appear in the result at most once, and that there is no
;; expectation that the order of the inputs will be preserved in the results.
;;
;; For the interval overload, this operator returns the portion of the first
;; interval that does not overlap with the second. If the second argument is
;; properly contained within the first and does not start or end it, this
;; operator returns null.
;;
;; If either argument is null, the result is null.
(deftest compile-except-test
  (testing "Null"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/except [x y])) {} nil nil))
      {:type "Null"} {:type "Null"} nil))

  (testing "List"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/except [x y])) {} nil nil))
      #elm/list [] #elm/list [] []
      #elm/list [] #elm/list [#elm/integer"1"] []
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"1"] []
      #elm/list [#elm/integer"1"] #elm/list [] [1]
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"2"] [1]
      #elm/list [#elm/integer"1" #elm/integer"2"] #elm/list [#elm/integer"2"] [1]
      #elm/list [#elm/integer"1" #elm/integer"2"] #elm/list [#elm/integer"1"] [2]

      #elm/list [] {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/except [x y])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/except [x y])) {} nil nil))
        #elm/interval [#elm/integer"1" #elm/integer"3"]
        #elm/interval [#elm/integer"3" #elm/integer"4"]
        (interval 1 2)

        #elm/interval [#elm/integer"3" #elm/integer"5"]
        #elm/interval [#elm/integer"1" #elm/integer"3"]
        (interval 4 5)))))


;; 19.12. In
;;
;; Normalized to Contains
(deftest compile-in-test
  (tu/unsupported-binary-operand "In"))


;; 19.13. Includes
;;
;; The Includes operator returns true if the first operand completely includes
;; the second.
;;
;; There are two overloads of this operator: 1. List, List : The element type of
;; both lists must be the same. 2. Interval, Interval : The point type of both
;; intervals must be the same.
;;
;; For the List, List overload, this operator returns true if the first operand
;; includes every element of the second operand, using equality semantics.
;;
;; For the Interval, Interval overload, this operator returns true if starting
;; point of the first interval is less than or equal to the starting point of
;; the second interval, and the ending point of the first interval is greater
;; than or equal to the ending point of the second interval. If precision is
;; specified and the point type is a Date, DateTime, or Time type, comparisons
;; used in the operation are performed at the specified precision.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If either argument is null, the result is null.
(deftest compile-includes-test
  (testing "Null"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/includes [x y])) {} nil nil))
      {:type "Null"} {:type "Null"} nil))

  (testing "List"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/includes [x y])) {} nil nil))
      #elm/list [] #elm/list [] true
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"1"] true
      #elm/list [#elm/integer"1" #elm/integer"2"] #elm/list [#elm/integer"1"] true

      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] false

      #elm/list [] {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/includes [x y])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/includes [x y])) {} nil nil))
        #elm/interval [#elm/integer"1" #elm/integer"2"]
        #elm/interval [#elm/integer"1" #elm/integer"2"] true
        #elm/interval [#elm/integer"1" #elm/integer"2"]
        #elm/interval [#elm/integer"1" #elm/integer"3"] false))))


;; 19.14. IncludedIn
;;
;; Normalized to Includes
(deftest compile-included-in-test
  (tu/unsupported-binary-operand "IncludedIn"))


;; 19.15. Intersect
;;
;; The Intersect operator returns the intersection of its arguments.
;;
;; This operator has two overloads: List Interval
;;
;; For the list overload, this operator returns a list with the elements that
;; appear in both lists, using equality semantics. The operator is defined with
;; set semantics, meaning that each element will appear in the result at most
;; once, and that there is no expectation that the order of the inputs will be
;; preserved in the results.
;;
;; For the interval overload, this operator returns the interval that defines
;; the overlapping portion of both arguments. If the arguments do not overlap,
;; this operator returns null.
;;
;; If either argument is null, the result is null.
;;
;; TODO: only implemented as binary operator because it's binary in CQL.
(deftest compile-intersect-test
  (testing "List"
    (are [x y res] (= res (tu/compile-binop elm/intersect elm/list x y))
      [#elm/integer"1"] [#elm/integer"1"] [1]
      [#elm/integer"1"] [#elm/integer"2"] []

      [#elm/integer"1"] [#elm/integer"1" #elm/integer"2"] [1]

      [#elm/integer"1" #elm/integer"2"] [#elm/integer"1"] [1])

    (tu/testing-binary-null elm/intersect #elm/list[]))

  (testing "Interval"
    (are [x y res] (= res (tu/compile-binop elm/intersect elm/interval x y))
      [#elm/integer"1" #elm/integer"2"]
      [#elm/integer"2" #elm/integer"3"]
      (interval 2 2)

      [#elm/integer"2" #elm/integer"3"]
      [#elm/integer"1" #elm/integer"2"]
      (interval 2 2)

      [#elm/integer"1" #elm/integer"10"]
      [#elm/integer"5" #elm/integer"8"]
      (interval 5 8)

      [#elm/integer"1" #elm/integer"10"]
      [#elm/integer"5" {:type "Null"} :>]
      nil

      [#elm/integer"1" #elm/integer"2"]
      [#elm/integer"3" #elm/integer"4"]
      nil)

    (tu/testing-binary-null elm/intersect interval-zero)))


;; 19.16. Meets
;;
;; Normalized to MeetsBefore or MeetsAfter
(deftest compile-meets-test
  (tu/unsupported-binary-operand "Meets"))


;; 19.17. MeetsBefore
;;
;; The MeetsBefore operator returns true if the first interval ends immediately
;; before the second interval starts. In other words, if the ending point of the
;; first interval is equal to the predecessor of the starting point of the
;; second.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-meets-before-test
  (testing "Null"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/meets-before [x y])) {} nil nil))
      interval-zero {:type "Null"} nil
      {:type "Null"} interval-zero nil))

  (testing "Integer"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/meets-before [x y])) {} nil nil))
      #elm/interval [#elm/integer"1" #elm/integer"2"]
      #elm/interval [#elm/integer"3" #elm/integer"4"] true
      #elm/interval [#elm/integer"1" #elm/integer"2"]
      #elm/interval [#elm/integer"4" #elm/integer"5"] false)))


;; 19.18. MeetsAfter
;;
;; The MeetsAfter operator returns true if the first interval starts immediately
;; after the second interval ends. In other words, if the starting point of the
;; first interval is equal to the successor of the ending point of the second.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-meets-after-test
  (testing "Null"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/meets-after [x y])) {} nil nil))
      interval-zero {:type "Null"} nil
      {:type "Null"} interval-zero nil))

  (testing "Integer"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/meets-after [x y])) {} nil nil))
      #elm/interval [#elm/integer"3" #elm/integer"4"]
      #elm/interval [#elm/integer"1" #elm/integer"2"] true
      #elm/interval [#elm/integer"4" #elm/integer"5"]
      #elm/interval [#elm/integer"1" #elm/integer"2"] false)))


;; 19.20. Overlaps
;;
;; Normalized to OverlapsBefore or OverlapsAfter
(deftest compile-overlaps-test
  (tu/unsupported-binary-operand "Overlaps"))


;; 19.21. OverlapsBefore
;;
;; Normalized to ProperContains Start
(deftest compile-overlaps-before-test
  (tu/unsupported-binary-operand "OverlapsBefore"))


;; 19.22. OverlapsAfter
;;
;; Normalized to ProperContains End
(deftest compile-overlaps-after-test
  (tu/unsupported-binary-operand "OverlapsAfter"))


;; 19.23. PointFrom
;;
;; The PointFrom expression extracts the single point from the source interval.
;; The source interval must be a unit interval (meaning an interval with the
;; same starting and ending boundary), otherwise, a run-time error is thrown.
;;
;; If the source interval is null, the result is null.
(deftest compile-point-from-test
  (are [x res] (= res (core/-eval (c/compile {} {:type "PointFrom" :operand x}) {} nil nil))
    #elm/interval [#elm/integer"1" #elm/integer"1"] 1
    {:type "Null"} nil))


;; 19.24. ProperContains
;;
;; The ProperContains operator returns true if the first operand properly
;; contains the second.
;;
;; There are two overloads of this operator: List, T: The type of T must be the
;; same as the element type of the list. Interval, T : The type of T must be the
;; same as the point type of the interval.
;;
;; For the List, T overload, this operator returns true if the given element is
;; in the list, and it is not the only element in the list, using equality
;; semantics.
;;
;; For the Interval, T overload, this operator returns true if the given point
;; is greater than the starting point of the interval, and less than the ending
;; point of the interval, as determined by the Start and End operators. If
;; precision is specified and the point type is a Date, DateTime, or Time type,
;; comparisons used in the operation are performed at the specified precision.
;;
;; If either argument is null, the result is null.
(deftest compile-proper-contains-test
  (testing "Interval"
    (testing "Null"
      (are [interval x res] (= res (core/-eval (c/compile {} (elm/proper-contains [interval x])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [interval x res] (= res (core/-eval (c/compile {} (elm/proper-contains [interval x])) {} nil nil))
        #elm/interval [#elm/integer"1" #elm/integer"3"] #elm/integer"2" true
        #elm/interval [#elm/integer"1" #elm/integer"1"] #elm/integer"1" false
        #elm/interval [#elm/integer"1" #elm/integer"1"] #elm/integer"2" false))))


;; 19.25. ProperIn
;;
;; Normalized to ProperContains
(deftest compile-proper-in-test
  (tu/unsupported-binary-operand "ProperIn"))


;; 19.26. ProperIncludes
;;
;; The ProperIncludes operator returns true if the first operand includes the
;; second, and is strictly larger.
;;
;; There are two overloads of this operator: List, List : The element type of
;; both lists must be the same. Interval, Interval : The point type of both
;; intervals must be the same.
;;
;; For the List, List overload, this operator returns true if the first list
;; includes every element of the second list, using equality semantics, and the
;; first list is strictly larger.
;;
;; For the Interval, Interval overload, this operator returns true if the first
;; interval includes the second interval, and the intervals are not equal. If
;; precision is specified and the point type is a Date, DateTime, or Time type,
;; comparisons used in the operation are performed at the specified precision.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If either argument is null, the result is null.
(deftest compile-proper-includes-test
  (testing "Null"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/proper-includes [x y])) {} nil nil))
      {:type "Null"} {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/proper-includes [x y])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/proper-includes [x y])) {} nil nil))
        #elm/interval [#elm/integer"1" #elm/integer"3"]
        #elm/interval [#elm/integer"1" #elm/integer"2"] true
        #elm/interval [#elm/integer"1" #elm/integer"2"]
        #elm/interval [#elm/integer"1" #elm/integer"2"] false))))


;; 19.27. ProperIncludedIn
;;
;; Normalized to ProperIncludes
(deftest compile-proper-included-in-test
  (tu/unsupported-binary-operand "ProperIncludedIn"))


;; 19.28. Size
;;
;; The Size operator returns the size of an interval.
;;
;; The result of this operator is equivalent to invoking: End(i) - Start(i) +
;; point-size, where the point-size for the point type of the interval is
;; determined by: Successor(Minimum_T) - Minimum_T.
;;
;; Note that this operator is not defined for intervals of type Date, DateTime,
;; and Time.
;;
;; If the argument is null, the result is null.
;;
;; TODO: I don't get it


;; 19.29. Start
;;
;; The Start operator returns the starting point of an interval.
;;
;; If the low boundary of the interval is open, this operator returns the
;; Successor of the low value of the interval. Note that if the low value of
;; the interval is null, the result is null.
;;
;; If the low boundary of the interval is closed and the low value of the
;; interval is not null, this operator returns the low value of the interval.
;; Otherwise, the result is the minimum value of the point type of the interval.
;;
;; If the argument is null, the result is null.
(deftest compile-start-test
  (testing "Null"
    (are [x res] (= res (core/-eval (c/compile {} {:type "Start" :operand x}) {} nil nil))
      {:type "Null"} nil))

  (testing "Integer"
    (are [x res] (= res (core/-eval (c/compile {} {:type "Start" :operand x}) {} nil nil))
      #elm/interval [#elm/integer"1" #elm/integer"2"] 1
      #elm/interval [:< #elm/integer"1" #elm/integer"2"] 2
      #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer"2"] Integer/MIN_VALUE))

  (testing "Decimal"
    (are [x res] (= res (core/-eval (c/compile {} {:type "Start" :operand x}) {} nil nil))
      #elm/interval [#elm/decimal"1.1" #elm/decimal"2"] 1.1M
      #elm/interval [:< #elm/decimal"1.1" #elm/decimal"2"] 1.10000001M
      #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"} #elm/decimal"2"] decimal/min)))


;; 19.30. Starts
;;
;; The Starts operator returns true if the first interval starts the second. In
;; other words, if the starting point of the first is equal to the starting
;; point of the second interval and the ending point of the first interval is
;; less than or equal to the ending point of the second interval.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-starts-test
  (testing "Null"
    (are [x y res] (= res (core/-eval (c/compile {} {:type "Starts" :operand [x y]}) {} nil nil))
      {:type "Null"} #elm/interval [#elm/integer"1" #elm/integer"2"] nil
      #elm/interval [#elm/integer"1" #elm/integer"2"] {:type "Null"} nil))

  (testing "Integer"
    (are [x y res] (= res (core/-eval (c/compile {} {:type "Starts" :operand [x y]}) {} nil nil))
      #elm/interval [#elm/integer"1" #elm/integer"3"]
      #elm/interval [#elm/integer"1" #elm/integer"3"] true
      #elm/interval [#elm/integer"1" #elm/integer"2"]
      #elm/interval [#elm/integer"1" #elm/integer"3"] true
      #elm/interval [#elm/integer"2" #elm/integer"3"]
      #elm/interval [#elm/integer"1" #elm/integer"3"] false)))


;; 19.31. Union
;;
;; The Union operator returns the union of its arguments.
;;
;; This operator has two overloads: List Interval
;;
;; For the list overload, this operator returns a list with all unique elements
;; from both arguments.
;;
;; For the interval overload, this operator returns the interval that starts at
;; the earliest starting point in either argument, and ends at the latest
;; starting point in either argument. If the arguments do not overlap or meet,
;; this operator returns null.
;;
;; If either argument is null, the result is null.
;;
;; TODO: only implemented as binary operator because it's binary in CQL.
(deftest compile-union-test
  (testing "List"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/union [x y])) {} nil nil))
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] [nil nil]
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"1"] [1]
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"2"] [1 2]
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"1" #elm/integer"2"] [1 2]

      {:type "Null"} {:type "Null"} nil))

  (testing "Interval"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/union [x y])) {} nil nil))
      #elm/interval [#elm/integer"1" #elm/integer"2"]
      #elm/interval [#elm/integer"3" #elm/integer"4"]
      (interval 1 4)

      {:type "Null"} {:type "Null"} nil)))


;; 19.32. Width
;;
;; The Width operator returns the width of an interval. The result of this
;; operator is equivalent to invoking: End(i) - Start(i).
;;
;; Note that this operator is not defined for intervals of type Date, DateTime,
;; and Time.
;;
;; If the argument is null, the result is null.
(deftest compile-width-test
  (testing "Null"
    (are [x res] (= res (core/-eval (c/compile {} {:type "Width" :operand x}) {} nil nil))
      {:type "Null"} nil))

  (testing "Integer"
    (are [x res] (= res (core/-eval (c/compile {} {:type "Width" :operand x}) {} nil nil))
      #elm/interval [#elm/integer"1" #elm/integer"2"] 1)))