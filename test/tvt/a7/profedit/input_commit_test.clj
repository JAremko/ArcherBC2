(ns tvt.a7.profedit.input-commit-test
  (:require [clojure.test :refer [deftest is testing]]
            [seesaw.core :as sc]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.widgets :as w])
  (:import [java.util Locale]))


(deftest pending-formatted-input-is-committed-synchronously
  (let [*state (atom {:profile {:c-muzzle-velocity 100.0}})
        panel (w/input-num *state
                           [:c-muzzle-velocity]
                           ::prof/c-muzzle-velocity)
        input (sc/select panel [:#input])]
    (sc/text! input "987.6")
    (is (= 100.0 (prof/get-in-prof* *state [:c-muzzle-velocity]))
        "editing text alone has not emitted focus-lost yet")
    (is (true? (w/commit-inputs! panel)))
    (is (= 987.6 (prof/get-in-prof* *state [:c-muzzle-velocity]))
        "Next can synchronously capture the pending editor value")))


(deftest invalid-pending-input-blocks-navigation-and-preserves-state
  (let [original-status @prof/*status
        *state (atom {:profile {:c-muzzle-velocity 100.0}})
        panel (w/input-num *state
                           [:c-muzzle-velocity]
                           ::prof/c-muzzle-velocity)
        input (sc/select panel [:#input])]
    (try
      (prof/status-ok! "")
      (sc/text! input "not-a-number")
      (is (false? (w/commit-inputs! panel)))
      (is (= 100.0 (prof/get-in-prof* *state [:c-muzzle-velocity])))
      (is (prof/status-err?))
      (finally
        (reset! prof/*status original-status)))))


(deftest numeric-parsing-follows-the-active-locale
  (let [original (Locale/getDefault)]
    (try
      (testing "decimal point"
        (Locale/setDefault Locale/US)
        (is (= 12.5 (w/parse-input-str "12.5" 1))))
      (testing "decimal comma"
        (Locale/setDefault Locale/GERMANY)
        (is (= 12.5 (w/parse-input-str "12,5" 1))))
      (finally
        (Locale/setDefault original)))))
