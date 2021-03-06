(ns mount.lite-test
  (:require [clojure.test :refer :all]
            [mount.lite :refer :all]
            [mount.lite-test.test-state-1 :refer (state-1)]
            [mount.lite-test.test-state-2 :refer (state-2)]
            [mount.lite-test.test-state-3 :refer (state-3)]
            [mount.lite-test.test-par :as par]))

;;; Helper functions.

(defn- statusses [& vars]
  (map (comp :mount.lite/status meta) vars))

(defmacro throws [& body]
  `(is (try ~@body false
            (catch Throwable t#
              t#))))

;;; Stop all states before and after every test, and reset on-reload.

(use-fixtures :each (fn [f] (stop) (on-reload nil) (f) (stop)))

;;; Tests

(deftest test-start-stop
  (is (= (start) [#'state-1 #'state-2 #'state-3 #'par/par]) "Start all states in correct order.")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :started]))
  (is (= state-3 "state-1 + state-2 + state-3") "States can use othes states correctly.")
  (is (= (stop) [#'par/par #'state-3 #'state-2 #'state-1]) "Stop all states in correct order.")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:stopped :stopped :stopped])))

(deftest test-only-one
  (is (= (start (only #'state-1)) [#'state-1]) "Start only state 1.")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :stopped :stopped]) "Only state-1 is started.")
  (is (= (stop (only #'state-2 #'state-3)) []) "Stopping states 2 and 3 does nothing.")
  (is (= (stop) [#'state-1]) "Stopping all states stops state 1."))

(deftest test-only-two
  (is (= (start (only #'state-2 #'state-1)) [#'state-1 #'state-2]) "Start only states 1 and 2 with one option map.")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :stopped]) "Only states 1 and 2 are started.")
  (is (= (stop) [#'state-2 #'state-1]) "Stopping all states stops state 1 and 2.")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:stopped :stopped :stopped]) "All states are stopped.")
  (is (= (start (only #'state-2) (only #'state-1)) [#'state-1 #'state-2]) "Start only states 1 and 2 with two option maps.")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :stopped]) "Only states 1 and 2 are started.")
  (is (= (stop) [#'state-2 #'state-1]) "Stopping all states stops states 1 and 2."))

(deftest test-except-one
  (is (= (start (except #'state-2 #'par/par)) [#'state-1 #'state-3]) "Start state 1 and 3.")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :stopped :started]) "State 1 and 3 are started.")
  (is (= (stop (except #'state-3)) [#'state-1]) "Only state 1 is stopped.")
  (is (= (stop) [#'state-3]) "Only state 3 is stopped"))

(deftest test-up-to
  (is (= (start (up-to #'state-2)) [#'state-1 #'state-2]) "Start state 1 and 2")
  (is (= (statusses #'state-1 #'state-2 #'state-3 #'par/par) [:started :started :stopped :stopped]))
  (is (= (start) [#'state-3 #'par/par]) "Start state 3 and par")
  (is (= (statusses #'state-1 #'state-2 #'state-3 #'par/par) [:started :started :started :started]))
  (is (= (stop (up-to #'state-2)) [#'state-3 #'state-2]) "Stop state 3 and 2")
  (is (= (statusses #'state-1 #'state-2 #'state-3 #'par/par) [:started :stopped :stopped :started]))
  (is (= (start (up-to #'state-3) (up-to #'state-2)) [#'state-2]) "Override up-to, start state 2")
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :stopped])))

(deftest test-substitute-state
  (start (substitute #'state-1 (state :start "sub-1")))
  (is (= state-3 "sub-1 + state-2 + state-3") "State 1 is substituted by anonymous state.")
  (stop)
  (start)
  (is (= state-3 "state-1 + state-2 + state-3") "State 1 is back to its original."))

(deftest test-substitute-map
  (start (substitute #'state-2 {:start (fn [] "sub-2")}))
  (is (= state-3 "sub-2 + state-3") "State 2 is substituted by map.")
  (stop)
  (start)
  (is (= state-3 "state-1 + state-2 + state-3") "State 2 is back to its original."))

(deftest test-on-cascade-skip
  (start)
  (require 'mount.lite-test.test-state-1 :reload)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:stopped :started :stopped]) "State 2 has :on-cascade :skip"))

(deftest test-on-reload-lifecycle
  (start)
  (require 'mount.lite-test.test-state-3 :reload)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :started]) "State 3 has :on-reload :lifecycle"))

(deftest test-on-reload-cascade
  (start)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :started]) "All states started")
  (require 'mount.lite-test.test-state-2 :reload)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :stopped :stopped]) "Both state 2 and 3 have stopped"))

(deftest test-on-reload-stop-override
  (start)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :started]) "All states started")
  (on-reload :stop)
  (require 'mount.lite-test.test-state-2 :reload)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :stopped :started]) "Only state 2 has stopped"))

(deftest test-on-reload-lifecycle-override
  (start)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :started]) "All states started")
  (on-reload :lifecycle)
  (in-ns 'mount.lite-test.test-state-2)
  (defstate state-2 :start "redef-2")
  (in-ns 'mount.lite-test)
  (is (= (statusses #'state-1 #'state-2 #'state-3) [:started :started :started]) "All states are still running")
  (is (= state-2 "state-1 + state-2") "State 2 still has original value")
  (stop)
  (start)
  (is (= state-2 "redef-2") "State 2 lifecycle was redefined")
  (require 'mount.lite-test.test-state-2 :reload))

(deftest test-parallel
  (par/set-latches 2)
  (start (parallel 2))
  (is (= (statusses #'state-1 #'par/par) [:started :started]) "At least state 1 and par have started.")
  (is (and state-1 par/par) "States par and 1 were started in parallel")
  (par/reset-latches))

(deftest test-missing-start
  (throws (start (only #'state-1) (substitute #'state-1 {}))))

(deftest test-start-error
  (in-ns 'mount.lite-test.test-state-1)
  (defstate state-1 :start (throw (ex-info "Boom!" {})))
  (in-ns 'mount.lite-test)
  (throws (start))
  (require 'mount.lite-test.test-state-1 :reload))

(deftest test-start-error-parallel
  (in-ns 'mount.lite-test.test-state-1)
  (defstate state-1 :start (throw (ex-info "Boom!" {})))
  (in-ns 'mount.lite-test)
  (throws (start (parallel 2)))
  (is (= (statusses #'state-1 #'par/par) [:stopped :started]))
  (require 'mount.lite-test.test-state-1 :reload))

(deftest test-up-to-already-started
  (start (only #'state-3))
  (is (= (start (up-to #'state-3)) [#'state-1 #'state-2])
      "Dependencies are started, even though state 1 was already started. State 1 is not started again."))

(deftest test-bindings
  (let [p (promise)]
    (start (bindings #'state-2 ['s " + BOUND" 'p p]))
    (is (= state-2 "state-1 + BOUND") "State 2 has been bound.")
    (is (= (-> #'state-2 meta :mount.lite/current :bindings (->> (apply hash-map)) (get 'p)) p)
        "Current bindings is set.")
    (stop)
    (is (realized? p) "State 2 binding also used in stop.")))
