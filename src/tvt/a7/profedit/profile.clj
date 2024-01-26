(ns tvt.a7.profedit.profile
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [clojure.string :refer [blank?]]
            [j18n.core :as j18n]))


;; Basic specs
(s/def ::non-empty-string (s/and string? (complement blank?)))
(s/def ::g-type #{:g1 :g7 :custom})


(s/def ::twist-dir #{:right :left})


(defmacro int-in-range?
  [start end units]
  `(with-meta (s/and (s/int-in ~start ~(inc end))
                     (s/conformer identity identity))
     {:min-v ~start :max-v ~end :units ~units}))


(defn- round-to [num fraction-digits]
  (let [bd-num (bigdec num)]
    (-> bd-num
        (.setScale ^int fraction-digits java.math.RoundingMode/HALF_UP)
        ^double .doubleValue)))


(defmacro double-in-range?
  [min max fraction-digits units]
  `(with-meta (s/and (s/double-in :infinite? false
                                  :NaN? false
                                  :min ~min
                                  :max ~max)
                     (s/conformer #(-> %
                                       double
                                       (* ~(Math/pow 10 fraction-digits))
                                       Math/round
                                       long)
                                  #(-> %
                                       double
                                       (* ~(Math/pow 10 (* fraction-digits -1.0)))
                                       (round-to ~fraction-digits)
                                       double)))
     {:min-v ~min :max-v ~max :fraction-digits ~fraction-digits :units ~units}))


(defmacro string-shorter-than?
  [max-length]
  `(with-meta (s/and ::non-empty-string #(<= (count %) ~max-length))
     {:max-length ~max-length}))


(defmacro string-empty-or-shorter-than?
  [max-length]
  `(with-meta (s/and string? #(<= (count %) ~max-length))
     {:max-length ~max-length}))


(s/def ::distance (double-in-range? 1.0 3000.0 2 ::units-distance))
(s/def ::reticle-idx (int-in-range? 0 255 nil))
(s/def ::zoom (int-in-range? 0 4 nil))
(s/def ::profile-name (string-shorter-than? 50))
(s/def ::cartridge-name (string-shorter-than? 50))
(s/def ::caliber (string-shorter-than? 50))
(s/def ::device-uuid (string-empty-or-shorter-than? 50))
(s/def ::bullet-name (string-shorter-than? 50))
(s/def ::short-name-top (string-shorter-than? 8))
(s/def ::short-name-bot (string-shorter-than? 8))
(s/def ::user-note (string-empty-or-shorter-than? 1024))
(s/def ::zero-x (double-in-range? -200.0 200.0 3 ::units-click))
(s/def ::zero-y (double-in-range? -200.0 200.0 3 ::units-click))
(s/def ::sc-height (double-in-range? -5000.0 5000.0 0 ::units-mm))
(s/def ::r-twist (double-in-range? 0.0 100.0 2 ::units-inches-per-turn))
(s/def ::c-muzzle-velocity (double-in-range? 10.0 3000.0 1 ::units-m-per-sec))
(s/def ::c-zero-temperature (double-in-range? -100.0 100.0 0 ::units-C))
(s/def ::c-t-coeff (double-in-range? 0.0 5.0 3 ::units-percent-per-15C))
(s/def ::c-zero-air-temperature (double-in-range? -100.0 100.0 0 ::units-C))
(s/def ::c-zero-air-pressure (double-in-range? 300.0 1500.0 1 ::units-hPa))
(s/def ::c-zero-air-humidity (double-in-range? 0.0 100.0 0 ::units-percent))
(s/def ::c-zero-w-pitch (double-in-range? -90.0 90.0 0 ::units-degrees))
(s/def ::c-zero-p-temperature (double-in-range? -100.0 100.0 0 ::units-C))
(s/def ::b-diameter (double-in-range? 0.001 50.0 3 ::units-inches))
(s/def ::b-weight (double-in-range? 1.0 6553.5 1 ::units-grains))
(s/def ::b-length (double-in-range? 0.01 200.0 3 ::units-inches))

(s/def ::bc (double-in-range? 0.0 10.0 4 ::units-lb-per-in-squared))
(s/def ::mv (double-in-range? 0.0 3000.0 1 ::units-m-per-sec))
(s/def ::cd (double-in-range? 0.0 10.0 4 nil))
(s/def ::ma (double-in-range? 0.0 10.0 4 ::units-Ma))


(s/def ::distances (s/coll-of ::distance
                              :kind vector
                              :min-count 1
                              :max-count 200))


(s/def :tvt.a7.profedit.profile.sw/distance
  (s/and (s/or :distance ::distance
               :unused zero?)
         (s/conformer last
                      #(vector (if (zero? %)
                                 :unused
                                 :distance) %))))


(s/def ::c-idx
  (s/and (s/or :index (s/int-in 0 201) :unsuded #{255})
         (s/conformer last #(vector (if (= 255 %)
                                      :unsuded
                                      :index) %))))


(s/def ::distance-from #{:index :value})


(s/def ::sw-pos (s/keys :req-un [::c-idx
                                 :tvt.a7.profedit.profile.sw/distance
                                 ::distance-from
                                 ::reticle-idx
                                 ::zoom]))


(s/def ::switches (s/coll-of ::sw-pos :kind vector :min-count 4))


(s/def ::coef-g1 (s/coll-of (s/keys :req-un [::bc ::mv])
                            :max-count 5
                            :kind vector))


(s/def ::coef-g7 (s/coll-of (s/keys :req-un [::bc ::mv])
                            :max-count 5
                            :kind vector))


(s/def ::coef-custom (s/coll-of (s/keys :req-un [::cd ::ma])
                                :max-count 200
                                :kind vector))


(s/def ::bc-type (s/and keyword? ::g-type))


(s/def ::profile (s/keys :req-un [::profile-name
                                  ::cartridge-name
                                  ::bullet-name
                                  ::caliber
                                  ::device-uuid
                                  ::short-name-top
                                  ::short-name-bot
                                  ::user-note
                                  ::zero-x
                                  ::zero-y
                                  ::distances
                                  ::switches
                                  ::sc-height
                                  ::r-twist
                                  ::twist-dir
                                  ::c-muzzle-velocity
                                  ::c-zero-temperature
                                  ::c-t-coeff
                                  ::c-zero-distance-idx
                                  ::c-zero-air-temperature
                                  ::c-zero-air-pressure
                                  ::c-zero-air-humidity
                                  ::c-zero-w-pitch
                                  ::c-zero-p-temperature
                                  ::b-diameter
                                  ::b-weight
                                  ::b-length
                                  ::coef-g1
                                  ::coef-g7
                                  ::coef-custom
                                  ::bc-type]))


(def example
  {:profile
   {:profile-name "Savage 110A"
    :cartridge-name "UKROP 338LM 250GRN"
    :bullet-name "SMK 250GRN HPBT"
    :short-name-top "338LM"
    :short-name-bot "250GRN"
    :caliber "9mm"
    :device-uuid ""
    :user-note "Add your profile specific notes here"
    :zero-x 0.0
    :zero-y 0.0
    :distances [100.0 100.0 120.0 130.0 140.0
                150.0 160.0 170.0 180.0 190.0
                200.0 210.0 220.0 250.0 300.0
                1000.0 1500.0 1600.0 1700.0 2000.0 3000.0]
    :switches [{:c-idx 255
                :distance-from :value
                :distance 100.0
                :reticle-idx 0
                :zoom 1}
               {:c-idx 255
                :distance-from :value
                :distance 200.0
                :reticle-idx 0
                :zoom 2}
               {:c-idx 255
                :distance-from :value
                :distance 300.0
                :reticle-idx 0
                :zoom 3}
               {:c-idx 255
                :distance-from :value
                :distance 1000.0
                :reticle-idx 0
                :zoom 4}]
    :sc-height 90.0
    :r-twist 9.45
    :twist-dir :right
    :c-muzzle-velocity 890.0
    :c-zero-temperature 25.0
    :c-t-coeff 1.03
    :c-zero-distance-idx 0
    :c-zero-air-temperature 15.0
    :c-zero-air-pressure 1000.0
    :c-zero-air-humidity 40.0
    :c-zero-w-pitch 0.0
    :c-zero-p-temperature 15.0
    :b-diameter 0.338
    :b-weight 250.0
    :b-length 1.55
    :coef-g1 [{:bc 0.343 :mv 850.0}
              {:bc 0.335 :mv 600.0}
              {:bc 0.325 :mv 400.0}]
    :coef-g7 [{:bc 0.343 :mv 850.0}]
    :coef-custom [{:cd 0.8 :ma 1.0}
                  {:cd 0.3 :ma 0.6}
                  {:cd 0.1 :ma 0.4}]
    :bc-type :g1}})


(def *status (atom {:status-ok true :status-text ""}))


(defn val-explain [spec val]
  (expound/expound-str spec val))


(defn status-ok! [text-or-key]
  (let [text (if (keyword? text-or-key)
               (j18n/resource text-or-key)
               text-or-key)]
   (reset! *status {:status-ok true :status-text text})))


(defn status-err! [text-or-key]
  (let [text (if (keyword? text-or-key)
               (j18n/resource text-or-key)
               text-or-key)]
  (reset! *status {:status-ok false :status-text text})))


(defn status-ok? []
  (:status-ok @*status))


(defn status-text []
  (:status-text @*status))


(def status-err? (complement status-ok?))


(defn format-spec-err
  ([val-spec v val-fmt-fn]
   (let [{:keys [min-v max-v :max-length]} (meta (s/get-spec val-spec))
         formatted-val (if val-fmt-fn (val-fmt-fn v) v)]
     (cond
       (and min-v max-v) (format (j18n/resource ::range-err)
                                 (str min-v)
                                 (str max-v))
       (some? max-length) (format (j18n/resource ::length-err)
                                  (str max-length))
       :else (str (val-explain val-spec formatted-val)))))
  ([val-spec v] (format-spec-err val-spec v nil)))


(defn assoc-in-prof!
  ([*state vpath v]
   (swap!
    *state
    (fn [state]
      (let [selector (into [:profile] vpath)
            old-val (get-in state selector)]
        (if (= old-val v)
          state
          (do (status-ok! ::status-ready)
              (assoc-in state selector v)))))))
  ([*state vpath val-spec v]
   (swap!
    *state
    (fn [state]
      (let [ selector (into [:profile] vpath)
            old-val (get-in state selector)]
        (if (= old-val v)
          state
          (if (s/valid? val-spec v)
            (do (status-ok! ::status-ready)
                (assoc-in state selector v))
            (do (status-err! (format-spec-err val-spec v))
                state))))))))


(defn assoc-in-prof
  ([state vpath v]
   (let [selector (into [:profile] vpath)
         old-val (get-in state selector)]
     (if (= old-val v)
       state
       (assoc-in state selector v))))
  ([state vpath val-spec v]
   (let [selector (into [:profile] vpath)
         old-val (get-in state selector)]
     (if (= old-val v)
       state
       (if (s/valid? val-spec v)
         (assoc-in state selector v)
         state)))))


(defn get-in-prof [state vpath]
  (get-in state (into [:profile] vpath)))


(defn get-in-prof* [*state vpath]
  (get-in-prof @*state vpath))


(defn state->cur-prof [state]
  (get state :profile state))
