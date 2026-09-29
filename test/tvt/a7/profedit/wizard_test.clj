(ns tvt.a7.profedit.wizard-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [tvt.a7.profedit.profile :as profile]
            [tvt.a7.profedit.wizard-state :as wizard-state]))


(deftest single-bc-row-muzzle-velocity-is-inside-profile-test
  (let [state (assoc-in profile/example [:profile :coef-g7] [{:bc 0.25}])
        result (wizard-state/ensure-single-bc-mv state :coef-g7)]
    (is (= {:bc 0.25 :mv 0.0}
           (get-in result [:profile :coef-g7 0])))
    (is (not (contains? result :coef-g7)))
    (is (s/valid? ::profile/profile (:profile result)))))


(deftest single-bc-row-keeps-an-existing-muzzle-velocity-test
  (testing "reopening the wizard step does not overwrite an existing boundary"
    (let [state {:profile {:coef-g1 [{:bc 0.3 :mv 850.0}]}}
          result (wizard-state/ensure-single-bc-mv state :coef-g1)]
      (is (= {:bc 0.3 :mv 850.0}
             (get-in result [:profile :coef-g1 0]))))))


(deftest single-bc-row-repair-is-idempotent-and-non-destructive-test
  (let [state {:profile {:coef-g7 [{:bc 0.25}]}}
        repaired (wizard-state/ensure-single-bc-mv state :coef-g7)]
    (is (= repaired
           (wizard-state/ensure-single-bc-mv repaired :coef-g7)))
    (is (= {:bc 0.25} (get-in state [:profile :coef-g7 0]))
        "the pure helper does not mutate its input")
    (is (= #{:profile} (set (keys repaired)))
        "no coefficient table escapes to the wizard atom root")))


(deftest single-bc-row-can-be-created-from-an-empty-table-test
  (let [result (wizard-state/ensure-single-bc-mv
                {:profile {:coef-g1 []}}
                :coef-g1)]
    (is (= {:mv 0.0} (get-in result [:profile :coef-g1 0])))))


(deftest wizard-log-summary-omits-user-text-test
  (let [summary (wizard-state/state-summary
                 {:profile {:profile-name "private profile"
                            :user-note "private note"
                            :bc-type :g7
                            :coef-g7 [{:bc 0.25 :mv 0.0}]}})]
    (is (= :g7 (:bc-type summary)))
    (is (= [[:bc :mv]] (get-in summary [:coefficient-rows :coef-g7])))
    (is (not (some #{"private profile" "private note"}
                   (tree-seq coll? seq summary))))))
