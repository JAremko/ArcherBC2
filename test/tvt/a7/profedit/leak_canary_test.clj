(ns tvt.a7.profedit.leak-canary-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [tvt.a7.profedit.fio :as fio]
            [tvt.a7.profedit.leak-canary :as canary]
            [tvt.a7.profedit.profile :as prof])
  (:import [java.nio.file Files]))


(deftest canary-fixtures-actually-save-and-reload
  (let [dir (.toFile (Files/createTempDirectory
                     "archerbc2-canary-test-"
                     (make-array java.nio.file.attribute.FileAttribute 0)))
        state (atom prof/example)]
    (try
      (let [[g1 custom] (canary/prepare-profiles! dir state fio/save!)]
        (is (.isFile (io/file g1)))
        (is (.isFile (io/file custom)))
        (canary/load-profile! state g1 fio/load!)
        (is (= :g1 (get-in @state [:profile :bc-type])))
        (canary/load-profile! state custom fio/load!)
        (is (= :custom (get-in @state [:profile :bc-type])))
        (is (= 50 (count (get-in @state [:profile :coef-custom])))))
      (finally
        (doseq [file (.listFiles dir)] (.delete ^java.io.File file))
        (.delete dir)))))


(deftest canary-fails-when-profile-io-fails
  (is (thrown? clojure.lang.ExceptionInfo
               (canary/prepare-profiles! (io/file ".") (atom prof/example)
                                         (fn [& _] nil))))
  (is (thrown? clojure.lang.ExceptionInfo
               (canary/load-profile! (atom prof/example) "missing.a7p"
                                    (fn [& _] nil)))))
