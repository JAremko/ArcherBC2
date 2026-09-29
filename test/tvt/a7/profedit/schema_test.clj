(ns tvt.a7.profedit.schema-test
  "Cross-validator, mutation, boundary, and generated persistence tests for
   profile data. Malli is an independent oracle for the production Spec."
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.profile-schema :as schema]
            [tvt.a7.profedit.rosetta :as ros]))


(defn- spec-valid? [profile]
  (s/valid? ::prof/profile profile))


(defn- generated-profile [seed]
  ;; Generate every field through Malli, then satisfy the one cross-field
  ;; invariant that a field-local generator cannot know about.
  (let [profile (mg/generate schema/ProfileFields {:seed seed :size 35})]
    (assoc profile :c-zero-distance-idx
           (mod (:c-zero-distance-idx profile)
                (count (:distances profile))))))


(defn- serializable-profile [seed]
  ;; The file format persists only the selected coefficient table and rejects
  ;; empty/zero/repeated active rows. Keep all tables meaningful while varying
  ;; the selected model across generated cases.
  (let [profile (generated-profile seed)
        bc-type (nth [:g1 :g7 :custom] (mod seed 3))]
    (assoc profile
           :bc-type bc-type
           :coef-g1 [{:bc 0.251 :mv 801.0}]
           :coef-g7 [{:bc 0.252 :mv 802.0}]
           :coef-custom [{:cd 0.253 :ma 0.803}])))


(deftest independent-schema-accepts-reference-profile
  (is (schema/valid-payload? prof/example))
  (is (spec-valid? (:profile prof/example))))


(deftest required-zero-distance-index-has-a-real-spec
  (is (some? (s/get-spec ::prof/c-zero-distance-idx))
      "a :req-un key without a registered spec only checks presence")
  (let [profile (:profile prof/example)
        two-hundred-distances (assoc profile :distances
                                     (vec (repeat 200 100.0)))]
    (testing "field boundaries"
      (is (s/valid? ::prof/c-zero-distance-idx 0))
      (is (s/valid? ::prof/c-zero-distance-idx 199))
      (is (not (s/valid? ::prof/c-zero-distance-idx -1)))
      (is (not (s/valid? ::prof/c-zero-distance-idx 200)))
      (is (not (s/valid? ::prof/c-zero-distance-idx 1.0))))
    (testing "the index must point inside the actual distance vector"
      (is (spec-valid? (assoc two-hundred-distances
                              :c-zero-distance-idx 199)))
      (is (schema/valid-profile? (assoc two-hundred-distances
                                        :c-zero-distance-idx 199)))
      (is (not (spec-valid? (assoc profile :c-zero-distance-idx
                                   (count (:distances profile))))))
      (is (not (schema/valid-profile?
                (assoc profile :c-zero-distance-idx
                       (count (:distances profile)))))))))


(deftest mutation-corpus-is-rejected-by-both-validators
  (let [profile (:profile prof/example)
        row {:bc 0.25 :mv 800.0}
        mutations
        [["missing :mv in G7 row (reported wizard regression)"
          (assoc profile :coef-g7 [{:bc 0.25}])]
         ["missing required top-level key" (dissoc profile :profile-name)]
         ["blank required string" (assoc profile :profile-name " \t")]
         ["overlong short name" (assoc profile :short-name-top "123456789")]
         ["NaN numeric input" (assoc profile :zero-x Double/NaN)]
         ["infinite numeric input" (assoc profile :zero-y Double/POSITIVE_INFINITY)]
         ["out-of-range air pressure" (assoc profile :c-zero-air-pressure 1500.1)]
         ["empty distance table" (assoc profile :distances [])]
         ["too few switch positions" (assoc profile :switches
                                             (subvec (:switches profile) 0 3))]
         ["invalid switch enum" (assoc-in profile [:switches 0 :distance-from]
                                           :somewhere)]
         ["too many G7 rows" (assoc profile :coef-g7 (vec (repeat 6 row)))]
         ["invalid ballistic model" (assoc profile :bc-type :g8)]
         ["zero index outside its field range"
          (assoc profile :c-zero-distance-idx 200)]
         ["zero index outside the current distance vector"
          (assoc profile :c-zero-distance-idx (count (:distances profile)))]]]
    (doseq [[description mutated] mutations]
      (testing description
        (is (not (spec-valid? mutated)) "clojure.spec must reject mutation")
        (is (not (schema/valid-profile? mutated))
            "independent Malli schema must reject mutation")))))


(deftest generated-malli-profiles-satisfy-production-spec
  (doseq [seed (range 200)]
    (let [profile (generated-profile seed)]
      (is (schema/valid-profile? profile)
          (str "Malli rejected its generated profile at seed " seed))
      (is (spec-valid? profile)
          (str "Spec disagreed with Malli at seed " seed)))))


(deftest generated-profiles-survive-binary-and-json-persistence
  (doseq [seed (range 45)]
    (let [payload {:profile (serializable-profile seed)}
          proto-back (-> payload ros/proto-bin-ser ros/proto-bin-deser)
          json-back (-> payload ros/json-ser ros/json-deser)]
      (testing (str "seed " seed)
        (doseq [[format back] [[:protobuf proto-back] [:json json-back]]]
          (is (schema/valid-payload? back)
              (str format " round-trip must satisfy Malli"))
          (is (spec-valid? (:profile back))
              (str format " round-trip must satisfy production Spec")))
        (let [profile (:profile proto-back)
              selected (ros/profile->bc-type-sel profile)]
          (is (= 1 (count (get profile selected)))
              "the selected coefficient table survives binary persistence")
          (is (every? #(if (= :custom (:bc-type profile))
                         (every? (set (keys %)) [:cd :ma])
                         (every? (set (keys %)) [:bc :mv]))
                      (get profile selected))
              "active rows retain every required coefficient key"))))))
