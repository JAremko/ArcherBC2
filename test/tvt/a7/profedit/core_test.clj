(ns tvt.a7.profedit.core-test
  "Unit tests for the pure core (doc/code-analysis-report.md §8 item 6.1).
   The rosetta round-trip is the file-format compatibility contract — the
   highest-value test in the repo."
  (:require [clojure.test :refer [deftest is testing]]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.rosetta :as ros]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.fio :as fio]
            [clojure.spec.alpha :as s]))


(def ^:private example-pld (select-keys prof/example [:profile]))


;; --- rosetta: binary round-trip (the .a7p contract) -------------------------

(deftest proto-bin-roundtrip
  (let [bytes (ros/expr! ros/proto-bin-ser example-pld)
        back (ros/impr! ros/proto-bin-deser bytes)]
    (is (some? back) "round-trip must deserialize")
    (is (s/valid? ::prof/profile (:profile back)))
    (testing "active bc-table, scalars and distances survive exactly"
      (let [p0 (:profile example-pld) p1 (:profile back)]
        (doseq [k [:profile-name :cartridge-name :bullet-name :caliber
                   :short-name-top :short-name-bot :user-note :device-uuid
                   :zero-x :zero-y :sc-height :r-twist :twist-dir
                   :c-muzzle-velocity :c-zero-temperature :c-t-coeff
                   :c-zero-distance-idx :b-diameter :b-weight :b-length
                   :bc-type :distances :switches]]
          (is (= (get p0 k) (get p1 k)) (str "field " k)))
        (is (= (:coef-g1 p0) (:coef-g1 p1)) "active g1 table")))))


(deftest proto-bin-checksum-tamper
  (let [^bytes bytes (ros/expr! ros/proto-bin-ser example-pld)]
    ;; flip a payload byte after the 32-byte md5 header -> checksum mismatch
    (aset-byte bytes 40 (unchecked-byte (bit-xor (aget bytes 40) 0x1)))
    (is (thrown? Exception (ros/proto-bin-deser bytes)))))


(deftest json-roundtrip
  (let [s (ros/expr! ros/json-ser example-pld)
        back (ros/impr! ros/json-deser s)]
    (is (= (:bc-type (:profile example-pld)) (:bc-type (:profile back)))
        "enum keywords survive the {{^keyword}} marker round-trip")
    (is (= (:distances (:profile example-pld)) (:distances (:profile back))))))


;; --- profile specs -----------------------------------------------------------

(deftest spec-conform-unform-roundtrip
  (testing "fixed-point double conform/unform is stable"
    (let [conformed (s/conform ::prof/distance 123.45)]
      (is (= 12345 conformed) "2 fraction digits -> x100 long")
      (is (= 123.45 (s/unform ::prof/distance conformed)))))
  (testing "range limits"
    (is (not (s/valid? ::prof/distance 0.5)))
    (is (not (s/valid? ::prof/distance 3000.5)))
    (is (s/valid? ::prof/distance 3000.0))))


;; --- distances index arithmetic ---------------------------------------------

(deftest move-list-item-invariants
  (let [mv (deref #'w/move-list-item)]
    (is (= [:b :a :c] (mv [:a :b :c] 0 1)))
    (is (= [:c :a :b] (mv [:a :b :c] 2 0)))
    (is (= [:a :b :c] (mv [:a :b :c] 1 1)))))


(deftest dnd-zeroing-index-invariants
  ;; dl-box-dnd-import-handler must keep :c-zero-distance-idx pointing at the
  ;; SAME distance value across every drag/drop case.
  (let [handler (deref #'w/dl-box-dnd-import-handler)
        base {:profile {:distances [10.0 20.0 30.0 40.0 50.0]
                        :c-zero-distance-idx 2}}   ; zero value = 30.0
        zero-val-after (fn [src drop]
                         (let [*s (atom base)
                               _ (handler *s src drop)
                               {:keys [distances c-zero-distance-idx]}
                               (:profile @*s)]
                           (nth distances c-zero-distance-idx)))]
    (doseq [[src drop] [[0 4] [4 0] [2 0] [2 4] [0 2] [4 2] [1 3] [3 1]]]
      (is (= 30.0 (zero-val-after src drop))
          (format "zero distance preserved for move %s->%s" src drop)))))


(deftest resize-vector-behavior
  (is (= [1 2] (w/resize-vector [1 2 3] 2 (constantly 0))))
  (is (= [1 2 3 0 0] (w/resize-vector [1 2 3] 5 (constantly 0))))
  (is (= [] (w/resize-vector [1 2 3] 0 (constantly 0)))))


;; --- firmware selection against a synthetic tree ----------------------------

(deftest get-newest-firmware-synthetic-tree
  (let [tree {"Archer TSA-9" {"0.85" [(java.net.URI. "jar:file:/x!/firmware/a/b/CS10.upg")]
                              "0.39" [(java.net.URI. "file:/y/firmware/a/c/CS10.upg")]
                              "0.90" [(java.net.URI. "file:/y/firmware/a/d/EVIL.bin")]}}]
    (with-redefs-fn {#'fio/firmware-tree tree}
      (fn []
        (let [fw ((deref #'fio/get-newest-firmware) "Archer TSA-9")]
          ;; 0.90 is newest but is NOT a CS10.upg -> rejected entirely
          ;; (selection picks the highest version first, then validates)
          (is (nil? fw)
              "non-CS10 newest version must be rejected, not shipped"))
        (let [tree2 {"D" {"0.85" [(java.net.URI. "jar:file:/x!/a/CS10.upg")]
                          "0.39" [(java.net.URI. "file:/y/c/CS10.upg")]}}]
          (with-redefs-fn {#'fio/firmware-tree tree2}
            (fn []
              (let [fw ((deref #'fio/get-newest-firmware) "D")]
                (is (= "0.85" (:version fw)))
                (is (vector? (:path fw)) ":path stays a vector of URIs")))))))))
