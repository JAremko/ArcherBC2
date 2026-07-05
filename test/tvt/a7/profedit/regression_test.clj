(ns tvt.a7.profedit.regression-test
  "Regression tests for the fix campaign (doc/code-analysis-report.md §8).
   Each test names the plan item it locks in."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.cdf :as cdf]
            [tvt.a7.profedit.rosetta :as ros]
            [tvt.a7.profedit.fio :as fio]
            [tvt.a7.profedit.widgets]
            [seesaw.core :as sc])
  (:import [java.io File]))


(defn- temp-file ^File [prefix suffix]
  (doto (File/createTempFile prefix suffix)
    (.deleteOnExit)))


(defmacro silencing-alerts
  "Runs body with seesaw alerts stubbed out (safe-exec! pops modal dialogs)."
  [& body]
  `(with-redefs [sc/alert (fn [& ~'_] nil)]
     ~@body))


;; --- 0.1 / C1: DRG import must never nil the state atom -------------------

(deftest drg-import-failure-preserves-state
  (testing "nonexistent .drg file"
    (let [*state (atom prof/example)]
      (silencing-alerts
       (cdf/apply-drg-file-to-state! *state "/nonexistent/nowhere.drg"))
      (is (some? @*state) "state atom must never become nil")
      (is (= prof/example @*state) "state must be unchanged on failure")))
  (testing "malformed .drg file"
    (let [f (temp-file "bad" ".drg")
          *state (atom prof/example)]
      (spit f "this is not a drag table {{{")
      (silencing-alerts
       (cdf/apply-drg-file-to-state! *state (.getAbsolutePath f)))
      (is (some? @*state))
      (is (= prof/example @*state)))))


;; --- 0.2 / C6: JSON import tolerates null values ---------------------------

(deftest json-deser-tolerates-nulls
  (is (= {:a nil} (ros/json-deser "{\"a\": null}")))
  (is (= {:k :left} (ros/json-deser "{\"k\": \"{{^keyword}}left\"}"))
      "keyword marker path must stay intact")
  (is (= {:n 1.5 :s "x" :v [1 nil "y"]}
         (ros/json-deser "{\"n\":1.5,\"s\":\"x\",\"v\":[1,null,\"y\"]}"))))

(deftest json-deser-friendly-parse-error
  (let [msg (try (ros/json-deser "{not json")
                 nil
                 (catch Exception e (.getMessage e)))]
    (is (some? msg))
    (is (not (re-find #"(?i)NullPointer|Object\.toString" (str msg)))
        "must not leak a JVM-internal error message")))


;; --- 0.5 / P5: file read robustness ----------------------------------------

(deftest read-byte-array-robustness
  (testing "empty file -> localized bad-profile error"
    (let [f (temp-file "empty" ".a7p")]
      (is (thrown? Exception
                   (fio/read-byte-array-from-file (.getAbsolutePath f))))))
  (testing "missing file -> localized error, not IO stack leak"
    (is (thrown? Exception
                 (fio/read-byte-array-from-file "/nonexistent/x.a7p"))))
  (testing "round-trips content"
    (let [f (temp-file "data" ".bin")]
      (spit f "hello")
      (is (= "hello"
             (String. ^bytes (fio/read-byte-array-from-file
                              (.getAbsolutePath f))))))))


;; --- 0.4 / C2: extension-less save round-trips cleanly ---------------------

(deftest save-without-extension-roundtrips
  (let [dir (doto (File. (System/getProperty "java.io.tmpdir")
                         (str "a7p-test-" (System/nanoTime)))
              (.mkdirs))
        base (.getAbsolutePath (File. dir "profile_no_ext"))
        *state (atom prof/example)]
    (silencing-alerts
     (let [ret (fio/save! *state base)]
       (is (= (str base ".a7p") ret) "save! must return the full path")
       (is (.exists (File. (str base ".a7p"))))
       (is (= (str base ".a7p") (fio/get-cur-fp))
           "*current-fp must point at the extension-full path")
       ;; The .a7p format persists only the ACTIVE bc-type's coefficient
       ;; table (here :g1); inactive tables come back as dummies by design.
       (is (= (dissoc (:profile prof/example) :coef-g7 :coef-custom)
              (dissoc (:profile @*state) :coef-g7 :coef-custom))
           "round-trip re-load must preserve the profile (active tables)")
       (is (prof/status-ok?) "no spurious bad-profile error")))))


;; --- 0.6 / C4: version comparator + firmware selection hardening -----------

(deftest version-comparator-hardening
  (let [cmp (deref #'fio/version-comparator)]
    (is (= "v1.2" (first (sort cmp ["0.39" "0.85" "v1.2"])))
        "highest version sorts first, v-prefix tolerated")
    (is (integer? (cmp nil "1.0")) "nil version must not throw")
    (is (integer? (cmp "garbage" "1.0")) "non-numeric must not throw")))

(deftest get-newest-firmware-nil-safe
  ;; dev checkouts have no resources/firmware -> empty tree -> nil, no throw
  (is (nil? (#'fio/get-newest-firmware "No Such Device"))))


;; --- 3.1 / C3: atomic writes ------------------------------------------------

(deftest atomic-write-replaces-content
  (let [dir (doto (File. (System/getProperty "java.io.tmpdir")
                         (str "atomic-test-" (System/nanoTime)))
              (.mkdirs))
        f (File. dir "target.a7p")]
    (spit f "old-content")
    (fio/write-byte-array-to-file (.getAbsolutePath f) (.getBytes "new-content"))
    (is (= "new-content" (slurp f)) "content replaced")
    (is (empty? (filter #(.endsWith (.getName ^File %) ".tmp")
                        (.listFiles dir)))
        "no temp file litter after a successful write")))


;; --- 3.2 / P3: in-memory dirty check ---------------------------------------

(deftest dirty-check-in-memory
  (let [dir (doto (File. (System/getProperty "java.io.tmpdir")
                         (str "dirty-test-" (System/nanoTime)))
              (.mkdirs))
        *state (atom prof/example)
        unsaved? (deref #'tvt.a7.profedit.widgets/state-unsaved?)]
    (silencing-alerts
     (fio/save! *state (.getAbsolutePath (File. dir "d1")))
     (is (false? (unsaved? *state)) "clean right after save round-trip")
     (swap! *state assoc-in [:profile :profile-name] "edited")
     (is (true? (unsaved? *state)) "dirty after an edit")
     (fio/save! *state (fio/get-cur-fp))
     (is (false? (unsaved? *state)) "clean again after re-save"))))


;; --- 5.6 / C8: powder-sensitivity determinism -------------------------------

(deftest sensitivity-calculator-guards
  (let [calc (fn [rows]
               (powder.SensitivityCalculator/calculateSensitivity
                (into-array (map double-array rows))))]
    (is (== 0.0 (calc [[15.0 800.0]]))
        "single row (reference only) yields 0.0, never NaN/Infinity")
    (is (== 0.0 (calc [[15.0 800.0] [15.0 850.0]]))
        "duplicate-of-reference temperature is skipped, not divided by zero")
    (let [rows [[15.0 800.0] [25.0 810.0] [35.0 820.0] [45.0 830.0]
                [55.0 840.0] [-5.0 790.0] [-15.0 780.0] [65.0 850.0]
                [75.0 860.0] [85.0 870.0]]
          base (calc rows)
          ;; same NON-reference rows in shuffled order (row 0 fixed = zero ref)
          shuffled (cons (first rows) (shuffle (rest rows)))]
      (is (< (Math/abs (- base (calc shuffled))) 1e-9)
          "coefficient independent of non-reference row order"))))
