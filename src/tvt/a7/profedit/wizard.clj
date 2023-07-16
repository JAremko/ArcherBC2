(ns tvt.a7.profedit.wizard
  (:require
   [seesaw.core :as sc]
   [seesaw.bind :as sb]
   [seesaw.forms :as sf]
   [seesaw.event :as se]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.rosetta :as ros]
   [tvt.a7.profedit.fio :as fio]
   [j18n.core :as j18n]
   [clojure.spec.alpha :as s])
  (:import
   [javax.swing.text
    DefaultFormatterFactory
    NumberFormatter
    DefaultFormatter]
   [javax.swing JFormattedTextField]))


(defn- mk-number-fmt
  [_ fraction-digits]
  (proxy [NumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (w/str->double s fraction-digits)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (if value (w/val->str (double value) fraction-digits) "")))))


(defn- input-num [& args]
  (apply w/create-input mk-number-fmt args))


(defn- fmt-str
  ^java.lang.String [^clojure.lang.Numbers max-len ^java.lang.String s]
  (w/truncate-with-ellipsis (max max-len 3) s))


(defn- mk-str-fmt-default
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn- mk-str-fmt-display
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn- mk-str-fmt-edit
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn- mk-str-fmt-null
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (or (fmt-str max-len (or s "")) "")))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (or (fmt-str max-len (or value "")) "")))))


(defn- input-str
  [*state vpath spec & opts]
  (let [max-length (:max-length (meta (s/get-spec spec)))
        formatter (new DefaultFormatterFactory
                       (w/wrap-formatter (mk-str-fmt-default max-length))
                       (w/wrap-formatter (mk-str-fmt-display max-length))
                       (w/wrap-formatter (mk-str-fmt-edit max-length))
                       (w/wrap-formatter (mk-str-fmt-null max-length)))
        jf (sc/construct JFormattedTextField formatter)]
    (sb/bind *state
             (sb/some (w/mk-debounced-transform #(or (prof/get-in-prof % vpath)
                                                     "")))
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
  {:wizard {:maximized? false}
   :profile
   {:profile-name nil
    :cartridge-name nil
    :bullet-name nil
    :short-name-top nil
    :short-name-bot nil
    :user-note ""
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


(defn- make-final-frame [*state main-frame-cons]
  (when-not (w/save-as-chooser *w-state)
    (reset! fio/*current-fp nil))
  (reset! *state (deref *w-state))
  (-> (main-frame-cons) sc/pack! sc/show!))


(defmacro ^:private chain-frames! [*state main-frame-cons w-frame-cons fns]
  (let [start `(partial make-final-frame ~*state ~main-frame-cons)]
    `(do (reset! *w-state (merge (deref ~*state) template))
         ~(rest (reduce (fn [acc fn] `(partial ~fn ~w-frame-cons ~acc))
                        start
                        (reverse fns))))))


(alias 'app 'tvt.a7.profedit.app)


(defn- make-description-panel [*state]
  (sf/forms-panel
   "pref,4dlu,pref,40dlu,pref,4dlu,pref,100dlu,pref"
   :items
   [(sc/label :text ::app/general-section-profile :class :fat) (sf/next-line)
    (sc/label ::app/general-section-profile-name)
    (sf/span (input-str *state [:profile-name] ::prof/profile-name) 7)
    (sc/label ::app/general-section-profile-top)
    (input-str *state [:short-name-top] ::prof/short-name-top :columns 8)
    (sf/next-line)
    (sc/label ::app/general-section-profile-bottom)
    (input-str *state [:short-name-bot] ::prof/short-name-bot :columns 8)
    (sf/next-line)
    (sf/separator ::app/general-section-round) (sf/next-line)
    (sc/label ::app/general-section-round-cartridge)
    (sf/span (input-str *state [:cartridge-name] ::prof/cartridge-name) 7)
    (sc/label ::app/general-section-round-bullet)
    (sf/span (input-str *state [:bullet-name] ::prof/bullet-name) 7)]))


(defn- make-description-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-description-panel *w-state) next-frame-fn))


(defn- make-rifle-panel [*pa]
  (sc/scrollable
   (sf/forms-panel
    "pref,4dlu,pref"
    :items [(sc/label :text ::app/rifle-title :class :fat) (sf/next-line)
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
                       :columns 4)])))


(defn- make-rifle-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-rifle-panel *w-state) next-frame-fn))


(defn- make-cartridge-panel [*pa]
  (sc/scrollable
   (sf/forms-panel
    "pref,4dlu,pref"
    :items [(sc/label :text ::app/rifle-cartridge-title :class :fat)
            (sf/next-line)
            (sc/label ::app/rifle-muzzle-velocity)
            (input-num *pa
                         [:c-muzzle-velocity]
                         ::prof/c-muzzle-velocity :columns 4)
            (sc/label ::app/rifle-powder-temperature)
            (input-num *pa
                         [:c-zero-temperature]
                         ::prof/c-zero-temperature :columns 4)
            (sc/label ::app/rifle-ratio)
            (input-num *pa [:c-t-coeff] ::prof/c-t-coeff :columns 4)])))


(defn- make-bullet-panel [*pa]
  (sf/forms-panel
   "pref,4dlu,pref"
   :items [(sc/label :text ::app/bullet-bullet :class :fat) (sf/next-line)
           (sc/label ::app/bullet-diameter)
           (input-num *pa [:b-diameter] ::prof/b-diameter
                        :columns 4)
           (sc/label ::app/bullet-weight)
           (input-num *pa [:b-weight] ::prof/b-weight
                        :columns 4)
           (sc/label ::app/bullet-length)
           (input-num *pa [:b-length] ::prof/b-length
                        :columns 4)]))


(defn- make-bullet-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-bullet-panel *w-state) next-frame-fn))


(defn- make-bc-type-preset-frame [frame-cons next-frame-fn]
  (let [group (sc/button-group)]
    (frame-cons *w-state
                (sc/border-panel
                 :border 20
                 :vgap 20
                 :north (sc/label :text ::coef-func-preset-headder :class :fat)
                 :center (sc/vertical-panel
                          :items [(sc/radio :id :g1
                                            :selected? true
                                            :text ::coef-func-preset-g1
                                            :margin 20
                                            :group group)
                                  (sc/radio :id :g7
                                            :text ::coef-func-preset-g7
                                            :margin 20
                                            :group group)]))
                #(let [selected-id (sc/config (sc/selection group) :id)]
                   (prof/assoc-in-prof! *w-state [:bc-type] selected-id)
                   (next-frame-fn)))))


(defn- set-bc-row-count! [count]
  (let [prof-sel (fn [suf] [:profile suf])
        bc-t (get-in (deref *w-state) (prof-sel :bc-type))
        bc-sel-key (->> bc-t name (str "coef-") keyword)
        c-f (constantly {:bc 0.0 :mv 0.0})
        upd-fn #(update-in %
                           (prof-sel bc-sel-key)
                           (fn [rows] (w/resize-vector rows count c-f)))]
    (swap! *w-state upd-fn)))


(defn- make-bc-row-count-preset-frame [frame-cons next-frame-fn]
  (let [group (sc/button-group)]
    (frame-cons *w-state
                (sc/border-panel
                 :border 20
                 :vgap 20
                 :north (sc/label :text ::coef-type-preset-headder :class :fat)
                 :center (sc/vertical-panel
                          :items [(sc/radio :id :single
                                            :selected? true
                                            :text ::coef-type-preset-single
                                            :margin 20
                                            :group group)
                                  (sc/radio :id :multy
                                            :text ::coef-type-preset-multy
                                            :margin 20
                                            :group group)]))
                #(let [selected-id (sc/config (sc/selection group) :id)]
                   (set-bc-row-count! (if (= selected-id :single) 1 5))
                   (next-frame-fn)))))


(defn- profile->act-coef-rows [profile]
  (get profile (ros/profile->bc-type-sel profile)))


(defn- make-g1-g7-singe-bc-row [*state]
  (println "entered")
  (let [profile (:profile (deref *state))
        _ (println "profile" profile)
        bc-c-key (ball/bc-type->coef-key (:bc-type profile))
        _ (println "bc-key" bc-c-key)]
    (println "Creating")
    (sc/horizontal-panel
     :items
     [(sc/label :text ::ball/bc)
      (input-num *state [bc-c-key 0 :bc] ::prof/bc :columns 5)])))


(defn- make-coef-frame [frame-cons next-frame-fn]
  (letfn [(c-up-state [st]
            (update-in st [:profile] ros/remove-zero-coef-rows))
          (maybe-next-frame! []
            (let [profile (:profile (swap! *w-state c-up-state))]
              (println "next with state\n" @*w-state)
              (if (seq (profile->act-coef-rows profile))
                (next-frame-fn)
                (do (make-coef-frame frame-cons next-frame-fn)
                    (when (prof/status-ok?)
                      (prof/status-err!
                       ::ros/profile-bc-table-err))))))]
    (frame-cons *w-state (if (->> *w-state
                                  deref
                                  :profile
                                  (profile->act-coef-rows)
                                  count
                                  (= 1))
                           (make-g1-g7-singe-bc-row *w-state)
                           (ball/make-func-panel *w-state)) maybe-next-frame!)))


(defn- make-cartridge-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-cartridge-panel *w-state) next-frame-fn))


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


(defn- make-dist-preset-frame [frame-cons next-frame-fn]
  (let [group (sc/button-group)]
    (frame-cons *w-state
                (sc/border-panel
                 :border 20
                 :vgap 20
                 :north (sc/label :text ::distance-preset-headder :class :fat)
                 :center (sc/vertical-panel
                          :items [(sc/radio :id :subsonic
                                            :text ::distance-preset-subsonic
                                            :selected? true
                                            :margin 20
                                            :group group)
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
                                            :group group)]))
                #(let [selected-id (sc/config (sc/selection group) :id)
                       {:keys [distances zeroing-idx]}
                       (get distance-presets selected-id)]
                   (swap! *w-state
                          (fn [w-s] (-> w-s
                                        (assoc-in
                                         [:profile :distances]
                                         distances)
                                        (assoc-in
                                         [:profile :c-zero-distance-idx]
                                         zeroing-idx))))
                   (next-frame-fn)))))


(defn start-wizard! [main-frame-cons wizard-frame-cons *state]
  (chain-frames! *state
                 main-frame-cons
                 wizard-frame-cons
                 [make-description-frame
                  make-rifle-frame
                  make-cartridge-frame
                  make-bullet-frame
                  make-dist-preset-frame
                  make-bc-type-preset-frame
                  make-bc-row-count-preset-frame
                  make-coef-frame]))
