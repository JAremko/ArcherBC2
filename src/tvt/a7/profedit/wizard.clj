(ns tvt.a7.profedit.wizard
  (:require
   [tvt.a7.profedit.nullableinp :as ni]
   [seesaw.core :as sc]
   [seesaw.bind :as sb]
   [seesaw.forms :as sf]
   [seesaw.event :as se]
   [tvt.a7.profedit.calc :as calc]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.rosetta :as ros]
   [seesaw.border :refer [empty-border]]
   [j18n.core :as j18n]
   [clojure.spec.alpha :as s])
  (:import [numericutil CustomNumberFormatter]
           [javax.swing.text
            DefaultFormatterFactory
            DefaultFormatter]
           [javax.swing JFormattedTextField]))


(defn- mk-number-fmt
  [saved-val fraction-digits]
  (proxy [CustomNumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (w/str->double s fraction-digits)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (if-let [v (or value (saved-val))]
         (w/val->str (double v) fraction-digits)
         "")))))


(defn input-num [& args]
  (apply ni/create-input mk-number-fmt args))


(defn- fmt-str
  ^java.lang.String [^clojure.lang.Numbers max-len ^java.lang.String s]
  (w/truncate-with-ellipsis (max max-len 3) s))


(defn- mk-str-fmt-default
  [max-len saved-val]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len (or s (saved-val)))))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len (or value (saved-val)))))))


(defn- mk-str-fmt-null
  [max-len saved-val]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (or (fmt-str max-len (or s (saved-val) "")) "")))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (or (fmt-str max-len (or value (saved-val) "")) "")))))


(defn- input-str
  [*state vpath spec & opts]
  (let [max-length (:max-length (meta (s/get-spec spec)))
        saved-val (partial prof/get-in-prof* *state vpath)
        formatter (new DefaultFormatterFactory
                       (w/wrap-formatter (mk-str-fmt-default max-length
                                                             saved-val))
                       (w/wrap-formatter (mk-str-fmt-default max-length
                                                             saved-val))
                       (w/wrap-formatter (mk-str-fmt-default max-length
                                                             saved-val))
                       (w/wrap-formatter (mk-str-fmt-null max-length
                                                          saved-val)))
        jf (sc/construct JFormattedTextField formatter)]
    (sb/bind *state
             (sb/transform #(prof/get-in-prof % vpath))
             (sb/value jf))

    (doto jf
      (w/add-tooltip (format (j18n/resource ::w/str-input-tooltip-text)
                             (str max-length)))
      (se/listen
       :focus-lost (partial w/sync-and-commit *state vpath spec)
       :key-pressed #(when (w/commit-key-pressed? %)
                       (w/sync-and-commit *state vpath spec %)))

      (w/opts-on-nonempty-input opts))))


(def template
  {:wizard {:maximized? false
            :content-idx 0}
   :profile
   {:profile-name nil
    :cartridge-name nil
    :bullet-name nil
    :short-name-top ""
    :short-name-bot ""
    :user-note ""
    :caliber nil
    :device-uuid ""
    :zero-x 0.0
    :zero-y 0.0
    :distances nil
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
    :sc-height nil
    :r-twist nil
    :twist-dir :right
    :c-muzzle-velocity nil
    :c-zero-temperature nil
    :c-t-coeff nil
    :c-zero-distance-idx nil
    :c-zero-air-temperature 15.0
    :c-zero-air-pressure 1000.0
    :c-zero-air-humidity 40.0
    :c-zero-w-pitch 0.0
    :c-zero-p-temperature 15.0
    :b-diameter nil
    :b-weight nil
    :b-length nil
    :coef-g1 []
    :coef-g7 []
    :coef-custom []
    :bc-type nil}})


(def ^:private *w-state (atom nil))


(alias 'app 'tvt.a7.profedit.app)


(defn- make-description-panel [*state]
  (sf/forms-panel
   "pref,4dlu,pref,40dlu,pref,4dlu,pref,100dlu,pref"
   :items
   [(sc/label :text ::app/general-section-profile :class :fat) (sf/next-line)
    (sc/label ::app/general-section-profile-name)
    (sf/span (input-str *state [:profile-name] ::prof/profile-name) 7)
    (sf/separator ::app/general-section-round) (sf/next-line)
    (sc/label ::app/general-section-round-cartridge)
    (sf/span (input-str *state [:cartridge-name] ::prof/cartridge-name) 7)
    (sc/label ::app/general-section-round-bullet)
    (sf/span (input-str *state [:bullet-name] ::prof/bullet-name) 7)]))


(defn- make-description-frame []
  (make-description-panel *w-state))


(def ^:private calibers-unsorted
  {".17 Hornet" 0.172
   ".17 HMR" 0.172
   ".17 Remington" 0.172
   "5.45×39mm" 0.215
   ".22 Long Rifle" 0.223
   "5.56×45mm NATO" 0.224
   "5.6mm Gw Pat 90" 0.224
   ".22 WMR" 0.224
   ".22-250 Remington" 0.224
   ".222 Remington" 0.224
   ".223 Remington" 0.224
   ".223 WSSM" 0.224
   ".224 Weatherby Magnum" 0.224
   "6×57mm Mauser" 0.236
   "6×62mm Freres" 0.243
   "6mm BR Norma" 0.243
   "6mm XC" 0.243
   ".243 Winchester" 0.243
   ".243 WSSM" 0.243
   ".25-06 Remington" 0.257
   "6.5mm Creedmoor" 0.264
   "6.5×47mm Lapua" 0.264
   "6.5 Grendel" 0.264
   "6.5×54mm Mauser" 0.264
   "6.5-300 Weatherby Magnum" 0.264
   "6.5×57mm Mauser" 0.264
   ".260 Remington" 0.264
   ".26 Nosler" 0.264
   ".270 Winchester" 0.277
   ".270 WSM" 0.277
   "7mm BR Remington" 0.284
   "7mm Remington Magnum" 0.284
   "7mm WSM" 0.284
   "7×57mm Mauser" 0.284
   "7.62×51mm NATO" 0.308
   ".30-06 Springfield" 0.308
   ".300 AAC Blackout" 0.308
   ".300 Weatherby Magnum" 0.308
   ".300 Whisper" 0.308
   ".300 Winchester Magnum" 0.308
   ".300 WSM" 0.308
   ".300 Norma Magnum" 0.308
   ".308 Norma Magnum" 0.308
   ".308 Winchester" 0.308
   "7.62×54mmR" 0.311
   "7.62×39mm" 0.312
   "8×57 I" 0.318
   "8×57 IS" 0.323
   ".338 Lapua Magnum" 0.338
   ".338 Norma Magnum" 0.338
   ".338 Remington Ultra Magnum" 0.338
   ".338 Winchester Magnum" 0.338
   "9.3×74mmR" 0.366
   ".375 Cheyenne Tactical" 0.408
   ".408 Cheyenne Tactical" 0.408
   ".416 Barrett" 0.416
   ".50 BMG" 0.510
   "12.7×108mm" 0.511
   "14.5×114mm" 0.586})


(def ^:private calibers (mapv #(vector (first %) (last %))
                              (sort-by second calibers-unsorted)))


(defn- make-rifle-panel [*pa]
  (let [pick (fn [frame] (sc/input frame
                                   (j18n/resource ::select-caliber-title)
                                   :title ::select-caliber-frame-title
                                   :choices calibers
                                   :to-string first))
        p-btn (sc/button
               :text ::select-caliber-btn
               :listen
               [:action
                (fn [e]
                  (prof/status-ok! ::select-caliber-title)
                  (when-let [picked-val (pick (sc/to-root e))]
                    (let [[p-key p-value] picked-val]
                      (prof/assoc-in-prof! *pa [:caliber] p-key)
                      (prof/assoc-in-prof! *pa [:b-diameter] p-value))))])]
    (sc/scrollable
     (sf/forms-panel
      "pref,4dlu,pref"
      :items [(sc/label :text ::app/rifle-title :class :fat) (sf/next-line)
              (sc/label ::app/rifle-caliber)
              (sc/horizontal-panel
               :items [(input-str *pa [:caliber] ::prof/caliber :columns 18)
                       p-btn])
              (sc/label ::app/rifle-twist-rate)
              (input-num *pa
                         [:r-twist]
                         ::prof/r-twist :columns 4)
              (sc/label ::app/rifle-twist-direction)
              (w/input-sel *pa
                           [:twist-dir]
                           {:right (j18n/resource ::app/rifle-twist-right)
                            :left (j18n/resource ::app/rifle-twist-left)}
                           ::prof/twist-dir)
              (sc/label ::app/rifle-scope-offset)
              (input-num *pa
                         [:sc-height]
                         ::prof/sc-height
                         :columns 4)]))))


(defn- make-rifle-frame []
  (make-rifle-panel *w-state))


(defn- make-cartridge-panel [*pa]
  (sc/scrollable
   (sf/forms-panel
    "pref,4dlu,pref,4dlu,pref"
    :items [(sc/label :text ::app/rifle-cartridge-title :class :fat)
            (sf/next-line)
            (sc/label ::app/rifle-muzzle-velocity)
            (input-num *pa
                       [:c-muzzle-velocity]
                       ::prof/c-muzzle-velocity :columns 4)
            (sf/next-line)
            (sc/label ::app/rifle-powder-temperature)
            (input-num *pa
                       [:c-zero-temperature]
                       ::prof/c-zero-temperature :columns 4)
            (sf/next-line)
            (sc/label ::app/rifle-ratio)
            (input-num *pa [:c-t-coeff] ::prof/c-t-coeff :columns 4)
            (sc/button :text ::calculate-c-t-coeff
                       :listen
                       [:action (fn [e]
                                  (calc/show-pwdr-sens-calc-frame
                                   *pa
                                   (sc/to-root e)))])])))


(defn- make-bullet-panel [*pa]
  (let [spec ::prof/b-diameter
        {:keys [fraction-digits]} (meta (s/get-spec spec))
        i-bd (input-num *pa [:b-diameter] spec :columns 4)
        i-bd-text (sc/select i-bd [:#input])
        pan (sf/forms-panel
             "pref,4dlu,pref"
             :items [(sc/label :text ::app/bullet-bullet :class :fat)
                     (sf/next-line)
                     (sc/label ::app/bullet-diameter)
                     i-bd
                     (sc/label ::app/bullet-weight)
                     (input-num *pa [:b-weight] ::prof/b-weight
                                :columns 4)
                     (sc/label ::app/bullet-length)
                     (input-num *pa [:b-length] ::prof/b-length
                                :columns 4)])]
    (when-let [bd (prof/get-in-prof* *pa [:b-diameter])]
      (sc/text! i-bd-text (w/val->str bd fraction-digits))
      (sc/value! i-bd-text bd))
    pan))


(defn- make-bullet-frame []
  (make-bullet-panel *w-state))


(defn- profile->act-coef-rows [profile]
  (get profile (ros/profile->bc-type-sel profile)))


(defn- make-g1-g7-singe-bc-row [*state]
  (let [profile (:profile (deref *state))
        bc-c-key (ball/bc-type->coef-key (or (:bc-type profile) :g1))]
    (swap! *state #(assoc-in % [bc-c-key 0 :mv] 0.0))
    (sc/border-panel
     :north (sc/horizontal-panel
             :items
             [(sc/label :text (str (j18n/resource ::ball/bc) "  "))
              (input-num *state [bc-c-key 0 :bc] ::prof/bc :columns 5)]))))


(defn- set-bc-row-count! [cnt]
  (let [prof-sel (fn [suf] [:profile suf])
        state (deref *w-state)
        bc-t (get-in state (or (prof-sel :bc-type) :g1))
        bc-sel-key (->> bc-t name (str "coef-") keyword)
        bc-sel (prof-sel bc-sel-key)
        cur-rows (get-in state bc-sel [])
        c-f (constantly {:bc 0.0 :mv 0.0})
        upd-fn #(assoc-in %
                          bc-sel
                          (if (not= cnt (count cur-rows))
                            (w/resize-vector [] cnt c-f)
                            cur-rows))]
    (swap! *w-state upd-fn)))


(defn- make-coef-fragment [c-w]
  (sc/invoke-later
   (sc/config! c-w
               :items [(if (= (:bc-row-count-saved @*w-state) :single)
                         (do
                           (set-bc-row-count! 1)
                           (make-g1-g7-singe-bc-row *w-state))
                         (do
                           (set-bc-row-count! 5)
                           (ball/make-func-panel *w-state)))])))


(defn- make-bc-type-preset-fragment [coef-wrapper]
  (let [group (sc/button-group)
        selected (sc/radio :id :g1
                           :text ::coef-func-preset-g1
                           :margin 20
                           :group group)
        cont (sc/border-panel
              :border 20
              :vgap 20
              :north (sc/label :text ::coef-func-preset-headder :class :fat)
              :center (sc/vertical-panel
                       :items [selected
                               (sc/radio :id :g7
                                         :text ::coef-func-preset-g7
                                         :margin 20
                                         :group group)]))]
    (sc/listen group
               :selection
               (fn [_]
                 (when-let [selection (sc/selection group)]
                   (let [selected-id (sc/config selection :id)]
                     (prof/assoc-in-prof! *w-state [:bc-type] selected-id))
                   (make-coef-fragment coef-wrapper))))
    (sc/invoke-later (sc/selection!
                      group
                      (if-let [saved-selection (prof/get-in-prof* *w-state
                                                                  [:bc-type])]
                        (sc/select cont [(->> saved-selection
                                              name
                                              (str "#")
                                              keyword)])
                        selected)))
    cont))


(defn- make-bc-row-count-preset-fragment [coef-wrapper]
  (let [group (sc/button-group)
        selected (sc/radio :id :single
                           :text ::coef-type-preset-single
                           :margin 20
                           :group group)
        cont (sc/border-panel
              :border 20
              :vgap 20
              :north (sc/label :text ::coef-type-preset-headder :class :fat)
              :center (sc/vertical-panel
                       :items [selected
                               (sc/radio :id :multy
                                         :text ::coef-type-preset-multy
                                         :margin 20
                                         :group group)]))]
    (sc/listen group
               :selection
               (fn [_]
                 (when-let [selection (sc/selection group)]
                   (let [selected-id (sc/config selection :id)]
                     (swap! *w-state #(assoc % :bc-row-count-saved selected-id))
                     (make-coef-fragment coef-wrapper)))))
    (sc/invoke-later (sc/selection!
                      group
                      (if-let [saved-rc-id (:bc-row-count-saved @*w-state)]
                        (sc/select cont [(->> saved-rc-id
                                              name
                                              (str "#")
                                              keyword)])
                        selected)))
    cont))


(defn make-bc-frame []
  (let [coef-wrapper (sc/vertical-panel
                      :items [(make-g1-g7-singe-bc-row *w-state)])]
    (sc/vertical-panel
     :items [(sc/horizontal-panel
              :items [(make-bc-type-preset-fragment coef-wrapper)
                      (make-bc-row-count-preset-fragment coef-wrapper)])
             (sc/border-panel :border (empty-border :left 20 :top 20)
                              :center coef-wrapper)])))


(defn- maybe-finalize-coef-frame! [_]
  (letfn [(c-up-state [st] (update-in st [:profile] ros/remove-zero-coef-rows))]
    (let [profile (:profile (swap! *w-state c-up-state))]
      (if (seq (profile->act-coef-rows profile))
        true
        (do (when (prof/status-ok?)
              (prof/status-err!
               ::ros/profile-bc-table-err))
            false)))))


(defn- truncate ^java.lang.String
  [^clojure.lang.Numbers max-len ^java.lang.String s]
  (if (> (count s) max-len)
    (str (subs s 0 max-len))
    s))


(defn- finalize-rifle-frame! [_]
  (swap! *w-state
         #(update % :profile
                  (fn [profile]
                    (let [m-l (->> ::prof/short-name-top
                                   s/get-spec
                                   meta
                                   :max-length)
                          caliber (:caliber profile)]
                      (assoc profile :short-name-top
                             (truncate m-l caliber))))))
  true)


(defn- finalize-bullet-frame! [_]
  (swap! *w-state
         #(update % :profile
                  (fn [profile]
                    (let [m-l (->> ::prof/short-name-bot
                                   s/get-spec
                                   meta
                                   :max-length)
                          bw (:b-weight profile)
                          formatted-bw (if (zero? (mod bw 1))
                                         (str (int bw))
                                         (str bw))]
                      (assoc profile :short-name-bot
                             (str (truncate (- m-l 3) formatted-bw) "GRN"))))))
  true)


(defn- make-cartridge-frame []
  (make-cartridge-panel *w-state))


(def ^:private distance-presets
  {:subsonic
   {:distances
    (mapv double
          [25 50 75 100 110 120 130 140 150 155 160 165 170 175 180 185 190 195
           200 205 210 215 220 225 230 235 240 245 250 255 260 265 270 275 280
           285 290 295 300 305 310 315 320 325 330 335 340 345 350 355 360 365
           370 375 380 385 390 395 400])
    :zeroing-idx 1}

   :short-range
   {:distances
    (mapv double
         [100 150 200 225 250 275 300 320 340 360 380 400 410 420 430 440 450
          460 470 480 490 500 505 510 515 520 525 530 535 540 545 550 555 560
          565 570 575 580 585 590 595 600 605 610 615 620 625 630 635 640 645
          650 655 660 665 670 675 680 685 690 695 700])
    :zeroing-idx 0}

   :middle-range
   {:distances
    (mapv double
          [100 200 250 300 325 350 375 400 420 440 460 480 500 520 540 560 580
           600 610 620 630 640 650 660 670 680 690 700 710 720 730 740 750 760
           770 780 790 800 805 810 815 820 825 830 835 840 845 850 855 860 865
           870 875 880 885 890 895 900 905 910 915 920 925 930 935 940 945 950
           955 960 965 970 975 980 985 990 995 1000])
    :zeroing-idx 0}

   :long-range
   {:distances
    (mapv double
          [100 200 250 300 350 400 420 440 460 480 500 520 540 560 580 600 610 620
           630 640 650 660 670 680 690 700 710 720 730 740 750 760 770 780 790 800
           810 820 830 840 850 860 870 880 890 900 910 920 930 940 950 960 970 980
           990 1000 1005 1010 1015 1020 1025 1030 1035 1040 1045 1050 1055 1060 1065
           1070 1075 1080 1085 1090 1095 1100 1105 1110 1115 1120 1125 1130 1135 1140
           1145 1150 1155 1160 1165 1170 1175 1180 1185 1190 1195 1200 1205 1210 1215
           1220 1225 1230 1235 1240 1245 1250 1255 1260 1265 1270 1275 1280 1285 1290
           1295 1300 1305 1310 1315 1320 1325 1330 1335 1340 1345 1350 1355 1360 1365
           1370 1375 1380 1385 1390 1395 1400 1405 1410 1415 1420 1425 1430 1435 1440
           1445 1450 1455 1460 1465 1470 1475 1480 1485 1490 1495 1500 1505 1510 1515
           1520 1525 1530 1535 1540 1545 1550 1555 1560 1565 1570 1575 1580 1585 1590
           1595 1600 1605 1610 1615 1620 1625 1630 1635 1640 1645 1650 1655 1660 1665
           1670 1675 1680 1685 1690 1695 1700])
    :zeroing-idx 0}})


(defn- make-dist-preset-frame []
  (let [group (sc/button-group)
        selected (sc/radio :id :subsonic
                           :text ::distance-preset-subsonic
                           :margin 20
                           :group group)
        cont (sc/border-panel
              :border 20
              :vgap 20
              :north (sc/label :text ::distance-preset-headder :class :fat)
              :center (sc/vertical-panel
                       :items [selected
                               (sc/radio :id :short-range
                                         :text ::distance-preset-short-range
                                         :margin 20
                                         :group group)
                               (sc/radio :id :middle-range
                                         :text ::distance-preset-middle-range
                                         :margin 20
                                         :group group)
                               (sc/radio :id :long-range
                                         :text ::distance-preset-long-gange
                                         :margin 20
                                         :group group)]))]
    (sc/listen group
               :selection
               (fn [_]
                 (when-let [selection (sc/selection group)]
                   (let [selected-id (sc/config selection :id)
                         {:keys [distances zeroing-idx]}
                         (get distance-presets selected-id)]
                     (swap! *w-state #(assoc % :dist-preset-saved-id
                                             selected-id))
                     (swap! *w-state
                            (fn [w-s] (-> w-s
                                          (assoc-in
                                           [:profile :distances]
                                           distances)
                                          (assoc-in
                                           [:profile :c-zero-distance-idx]
                                           zeroing-idx))))))))
    (sc/invoke-later (sc/selection!
                      group
                      (if-let [saved-dp-id (:dist-preset-saved-id @*w-state)]
                        (sc/select cont [(->> saved-dp-id
                                              name
                                              (str "#")
                                              keyword)])
                        selected)))
    cont))


(def ^:private content-vec
  [{:cons make-description-frame :finalizer (constantly true)}
   {:cons make-rifle-frame :finalizer finalize-rifle-frame!}
   {:cons make-cartridge-frame :finalizer (constantly true)}
   {:cons make-bullet-frame :finalizer finalize-bullet-frame!}
   {:cons make-dist-preset-frame :finalizer (constantly true)}
   {:cons make-bc-frame :finalizer  maybe-finalize-coef-frame!}])


(defn start-wizard! [main-frame-cons wizard-frame-cons *state]
  (reset! *w-state template)
  (wizard-frame-cons *state *w-state main-frame-cons content-vec))
