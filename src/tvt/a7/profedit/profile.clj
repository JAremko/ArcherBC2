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
  `(with-meta (s/int-in ~start ~(inc end))
     {:min-v ~start :max-v ~end :units ~units}))


(defmacro double-in-range?
  [min max units]
  `(with-meta (s/double-in :infinite? false
                           :NaN? false
                           :min ~min
                           :max ~max)
     {:min-v ~min :max-v ~max :units ~units}))


(defmacro string-shorter-than?
  [max-length]
  `(with-meta (s/and ::non-empty-string #(<= (count %) ~max-length))
     {:max-length ~max-length}))


(s/def ::distance (int-in-range? 1 3000 ::units-distance))
(s/def ::c-idx (int-in-range? -1 200 nil))
(s/def ::reticle-idx (int-in-range? 0 255 nil))
(s/def ::zoom (int-in-range? 1 4 nil))
(s/def ::profile-name (string-shorter-than? 50))
(s/def ::cartridge-name (string-shorter-than? 50))
(s/def ::bullet-name (string-shorter-than? 50))
(s/def ::short-name-top (string-shorter-than? 8))
(s/def ::short-name-bot (string-shorter-than? 8))
(s/def ::user-note string?)
(s/def ::zero-x (double-in-range? -200.0 200.0 ::units-click))
(s/def ::zero-y (double-in-range? -200.0 200.0 ::units-click))
(s/def ::sc-height (int-in-range? -5000 5000 ::units-mm))
(s/def ::r-twist (double-in-range? 0.0 50.0 ::units-inches-per-turn))
(s/def ::c-muzzle-velocity (int-in-range? 100 1000 ::units-m-per-sec))
(s/def ::c-zero-temperature (int-in-range? -100 100 ::units-C))
(s/def ::c-t-coeff (double-in-range? 0.0 5.0 ::units-percent-per-15C))
(s/def ::c-zero-air-temperature (int-in-range? -100 100 ::units-C))
(s/def ::c-zero-air-pressure (int-in-range? 300 1500 ::units-hPa))
(s/def ::c-zero-air-humidity (int-in-range? 0 100 ::units-percent))
(s/def ::c-zero-w-pitch (int-in-range? -90 90 ::units-degrees))
(s/def ::c-zero-p-temperature (int-in-range? -100 100 ::units-C))
(s/def ::b-diameter (double-in-range? 0.001 15.0 ::units-inches))
(s/def ::b-weight (double-in-range? 1.0 6553.5 ::units-grains))
(s/def ::b-length (double-in-range? 0.01 60.0 ::units-inches))
(s/def ::bc (double-in-range? 0.0 10.0 ::units-lb-per-in-squared))
(s/def ::mv (int-in-range? 0 3000 ::units-m-per-sec))
(s/def ::cd (double-in-range? 0.0 10.0 ::units-lb-per-in-squared))
(s/def ::ma (double-in-range? 0.0 10.0 ::units-Ma))


(s/def ::distances (s/coll-of ::distance
                              :kind vector
                              :min-count 1
                              :max-count 200))


(s/def ::sw-pos (s/keys :req-un [::c-idx
                                 ::distance
                                 ::reticle-idx
                                 ::zoom]))


(s/def ::sw-pos-a ::sw-pos)


(s/def ::sw-pos-b ::sw-pos)


(s/def ::sw-pos-c ::sw-pos)


(s/def ::sw-pos-d ::sw-pos)


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
                                  ::short-name-top
                                  ::short-name-bot
                                  ::user-note
                                  ::zero-x
                                  ::zero-y
                                  ::distances
                                  ::sw-pos-a
                                  ::sw-pos-b
                                  ::sw-pos-c
                                  ::sw-pos-d
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


(defn selected-profile-exists? [state]
  (let [selected (get state :selected-profile)
        profiles (get state :profiles)]
    (and (integer? selected)
         (<= 0 selected (- (count profiles) 1)))))


(s/def ::valid-selected-profile selected-profile-exists?)


(s/def ::profiles (s/coll-of ::profile :kind vector :min-count 1))


(s/def ::state (s/and (s/keys :req-un [::selected-profile
                                       ::profiles])
                      ::valid-selected-profile))


(def example
  {:profiles
   [{:profile-name "Savage 110A"
     :cartridge-name "UKROP 338LM 250GRN"
     :bullet-name "SMK 250GRN HPBT"
     :short-name-top "338LM"
     :short-name-bot "250GRN"
     :user-note "Add your profile specific notes here"
     :zero-x -12.1
     :zero-y 10.01
     :distances [100 100 120 130 140 150 160 170 180 190
                 200 210 220 250 300 1000 1500 1600 1700 2000 3000]
     :sw-pos-a {:c-idx -1
                :distance 150
                :reticle-idx  4
                :zoom 1}
     :sw-pos-b {:c-idx -1
                :distance 150
                :reticle-idx 1
                :zoom 1}
     :sw-pos-c {:c-idx 1
                :distance 150
                :reticle-idx 3
                :zoom 2}
     :sw-pos-d {:c-idx 8
                :distance 120
                :reticle-idx 4
                :zoom 4}
     :sc-height 90
     :r-twist 9.45
     :twist-dir :right
     :c-muzzle-velocity 890
     :c-zero-temperature 25
     :c-t-coeff 1.03
     :c-zero-distance-idx 0
     :c-zero-air-temperature 20
     :c-zero-air-pressure 990
     :c-zero-air-humidity 51
     :c-zero-w-pitch 0
     :c-zero-p-temperature 20
     :b-diameter 0.338
     :b-weight 250.0
     :b-length 1.55
     :coef-g1 [{:bc 0.343 :mv 850}
               {:bc 0.335 :mv 600}
               {:bc 0.325 :mv 400}
               {:bc 0.327 :mv 0}
               {:bc 0.001 :mv 0}]
     :coef-g7 [{:bc 0.343 :mv 850}
               {:bc 0.327 :mv 0}
               {:bc 0.001 :mv 0}]
     :coef-custom [{:cd 0.8 :ma 1.0}
                   {:cd 0.3 :ma 0.6}
                   {:cd 0.1 :ma 0.4}]
     :bc-type :g1}
    {:profile-name "UAR-10M"
     :cartridge-name "220GRN Subsonic"
     :bullet-name "Hornady 220GRN ELD-X"
     :short-name-top "308W"
     :short-name-bot "220GRN"
     :user-note ""
     :zero-x 10.22
     :zero-y 20.3
     :distances [25 50 75 100 110 120 130 140 150 160 170 180 190
                 200 210 220 230 240 250 260 270 280 290 300]
     :sw-pos-a {:c-idx 0
                :distance 50
                :reticle-idx 1
                :zoom 1}
     :sw-pos-b {:c-idx 1
                :distance 150
                :reticle-idx 1
                :zoom 1}
     :sw-pos-c {:c-idx 1
                :distance 150
                :reticle-idx 1
                :zoom 2}
     :sw-pos-d {:c-idx -1
                :distance 150
                :reticle-idx 1
                :zoom 4}
     :sc-height 90
     :r-twist 10.4
     :twist-dir :right
     :c-muzzle-velocity 320
     :c-zero-temperature 25
     :c-t-coeff 1.2
     :c-zero-distance-idx 0
     :c-zero-air-temperature 20
     :c-zero-air-pressure 995
     :c-zero-air-humidity 51
     :c-zero-w-pitch 0
     :c-zero-p-temperature 20
     :b-diameter 0.308
     :b-weight 220.0
     :b-length 1.624
     :coef-g1 [{:bc 0.343 :mv 850}
               {:bc 0.335 :mv 600}
               {:bc 0.325 :mv 400}
               {:bc 0.327 :mv 0}
               {:bc 0.001 :mv 0}]
     :coef-g7 [{:bc 0.343 :mv 850}
               {:bc 0.327 :mv 0}
               {:bc 0.001 :mv 0}]
     :coef-custom [{:cd 0.8 :ma 1.0}
                   {:cd 0.3 :ma 0.6}
                   {:cd 0.1 :ma 0.4}
                   {:cd 0.8 :ma 1.0}
                   {:cd 0.3 :ma 0.6}
                   {:cd 0.1 :ma 0.4}]
     :bc-type :g7}]
   :selected-profile 1})


(s/def ::status-ok boolean?)

(s/def ::status-text string?)

(s/def ::status (s/and (s/keys :req-un [::status-ok ::status-text])))

(def *status (atom {:status-ok true
                    :status-text (j18n/resource ::status-ready)}))


(def state-valid? (partial s/valid? ::state))


(def state-explain (partial expound/expound-str ::state))


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


(defn status-err? []
  (complement status-ok?))


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
      (let [profile-idx (get-in state [:selected-profile])
            selector (into [:profiles profile-idx] vpath)
            old-val (get-in state selector)]
        (if (= old-val v)
          state
          (do (status-ok! ::status-ready)
              (assoc-in state selector v)))))))
  ([*state vpath val-spec v]
   (swap!
    *state
    (fn [state]
      (let [profile-idx (get-in state [:selected-profile])
            selector (into [:profiles profile-idx] vpath)
            old-val (get-in state selector)]
        (if (= old-val v)
          state
          (if (s/valid? val-spec v)
            (do (status-ok! ::status-ready)
                (assoc-in state selector v))
            (do (status-err! (format-spec-err val-spec v))
                state))))))))


(defn update-in-prof!
  ([*state vpath f]
   (swap!
    *state
    (fn [state]
      (let [profile-idx (get-in state [:selected-profile])
            selector (into [:profiles profile-idx] vpath)
            old-val (get-in state selector)
            new-val (f old-val)]
        (if (= old-val new-val)
          state
          (do (status-ok! ::status-ready)
              (assoc-in state selector new-val)))))))
  ([*state vpath val-spec f]
   (swap!
    *state
    (fn [state]
      (let [profile-idx (get-in state [:selected-profile])
            selector (into [:profiles profile-idx] vpath)
            old-val (get-in state selector)
            new-val (f old-val)]
        (if (= old-val new-val)
          state
          (if (s/valid? val-spec new-val)
            (do (status-ok! ::status-ready)
                (assoc-in state selector new-val))
            (do (status-err! (format-spec-err val-spec new-val))
                state))))))))


(defn get-in-prof [state vpath]
  (let [profile-idx (get-in state [:selected-profile])]
    (get-in state (into [:profiles profile-idx] vpath))))


(defn get-in-prof* [*state vpath]
  (get-in-prof @*state vpath))


(defn assoc-in! [*state vpath v]
  (swap! *state
         (fn [state]
           (if (= (get-in state vpath) v)
             state
             (let [new-state (assoc-in state vpath v)]
               (if (state-valid? new-state)
                 (do (status-ok! ::status-ready)
                     new-state)
                 (do (status-err! ::status-really-wrong-err)
                     state)))))))


(defn state->cur-prof [state]
  (get-in state [:profiles (:selected-profile state)]))
