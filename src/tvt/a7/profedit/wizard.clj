(ns tvt.a7.profedit.wizard
  (:require
   [seesaw.core :as sc]
   [seesaw.bind :as sb]
   [seesaw.forms :as sf]
   [seesaw.event :as se]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.rosetta :as ros]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [j18n.core :as j18n]
   [clojure.spec.alpha :as s])
  (:import
   [javax.swing.text
    DefaultFormatterFactory
    NumberFormatter
    DefaultFormatter]
   [javax.swing JFormattedTextField]))


(defn mk-number-fmt
  [_ fraction-digits]
  (proxy [NumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (w/str->double s fraction-digits)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (if value (w/val->str (double value) fraction-digits) "")))))


(defn mk-number-0125-mult-fmt
  [_ fraction-digits]
  (proxy [NumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (try
         (w/round-to-closest-0125-mul (w/str->double s fraction-digits))
         (catch Exception _ nil))))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (if value (w/val->str (double value) fraction-digits) "")))))



(defn input-num [& args]
  (apply w/create-input mk-number-fmt args))


(defn input-0125-mult [& args]
  (apply w/create-input mk-number-0125-mult-fmt args))


(defn- fmt-str
  ^java.lang.String [^clojure.lang.Numbers max-len ^java.lang.String s]
  (w/truncate-with-ellipsis (max max-len 3) s))


(defn mk-str-fmt-default
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn mk-str-fmt-display
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn mk-str-fmt-edit
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


(defn input-str
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
  {:profile
   {:profile-name nil
    :cartridge-name nil
    :bullet-name nil
    :short-name-top nil
    :short-name-bot nil
    :user-note ""
    :zero-x nil
    :zero-y nil
    :distances nil
    :switches [{:c-idx 255
                :distance 1.0
                :reticle-idx 0
                :zoom 1}
               {:c-idx 1
                :distance 0.0
                :reticle-idx 0
                :zoom 1}
               {:c-idx 2
                :distance 0.0
                :reticle-idx 0
                :zoom 2}
               {:c-idx 3
                :distance 0.0
                :reticle-idx 0
                :zoom 4}]
    :sc-height nil
    :r-twist nil
    :twist-dir :right
    :c-muzzle-velocity nil
    :c-zero-temperature nil
    :c-t-coeff nil
    :c-zero-distance-idx 0
    :c-zero-air-temperature nil
    :c-zero-air-pressure nil
    :c-zero-air-humidity nil
    :c-zero-w-pitch nil
    :c-zero-p-temperature nil
    :b-diameter nil
    :b-weight nil
    :b-length nil
    :coef-g1 []
    :coef-g7 []
    :coef-custom []
    :bc-type :g1}})


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


(defn make-description-panel [*state]
  (w/forms-with-bg
   :wizard-description-panel
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


(defn make-zeroing-panel [*pa]
  (w/forms-with-bg
   :zeroing-panel
   "pref,4dlu,pref,20dlu,pref,4dlu,pref"
   :items [(sc/label :text ::ball/root-tab-zeroing :class :fat) (sf/next-line)
           (sc/label ::ball/general-section-coordinates-zero-x)
           (input-0125-mult *pa [:zero-x] ::prof/zero-x :columns 4)
           (sc/label ::ball/general-section-coordinates-zero-y)
           (input-0125-mult *pa [:zero-y] ::prof/zero-y :columns 4)
           (sc/label ::ball/general-section-direction-pitch)
           (input-num *pa [:c-zero-w-pitch] ::prof/c-zero-w-pitch :columns 4)
           (sc/label ::ball/general-section-temperature-air)
           (input-num *pa [:c-zero-air-temperature]
                        ::prof/c-zero-air-temperature :columns 4)
           (sc/label ::ball/general-section-temperature-powder)
           (input-num *pa [:c-zero-p-temperature]
                        ::prof/c-zero-p-temperature :columns 4)
           (sc/label ::ball/general-section-environment-pressure)
           (input-num, *pa [:c-zero-air-pressure]
                        ::prof/c-zero-air-pressure :columns 4)
           (sc/label ::ball/general-section-environment-humidity)
           (input-num *pa [:c-zero-air-humidity]
                        ::prof/c-zero-air-humidity :columns 4)]))


(defn make-zerioing-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-zeroing-panel *w-state) next-frame-fn))


(defn make-rifle-panel [*pa]
  (sc/scrollable
   (w/forms-with-bg
    :rifle-tab-panel
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


(defn make-rifle-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-rifle-panel *w-state) next-frame-fn))


(defn make-cartridge-panel [*pa]
  (sc/scrollable
   (w/forms-with-bg
    :cartridge-tab-panel
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


(defn make-bullet-panel [*pa]
  (w/forms-with-bg
   :bullet-tab-panel
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


(defn make-bullet-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-bullet-panel *w-state) next-frame-fn))


(defn make-coef-panel [*pa]
 (sc/border-panel
      :vgap 20
      :north
      (w/forms-with-bg
       :bullet-tab-panel
       "pref,4dlu,pref"
       :items [(sc/label :text ::app/function-tab-title)
               (ball/make-bc-type-sel *pa)
               (sc/label :text ::app/function-tab-row-count)
               (w/input-coef-count *pa ball/regen-func-coefs)])
      :center (ball/make-func-panel *pa)))


(defn make-coef-frame [frame-cons next-frame-fn]
  (let [c-up-state #(update-in % [:profile] ros/remove-zero-coef-rows)
        maybe-next-frame! #(let [profile (:profile (swap! *w-state c-up-state))
                                 act-bc-k (ros/profile->bc-type-sel profile)
                                 act-row (get profile act-bc-k)]
                             (if (seq act-row)
                               (next-frame-fn)
                               (do (make-coef-frame frame-cons next-frame-fn)
                                   (prof/status-err!
                                    ::ros/profile-bc-table-err))))]
    (frame-cons *w-state (make-coef-panel *w-state) maybe-next-frame!)))


(defn make-cartridge-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (make-cartridge-panel *w-state) next-frame-fn))


(defn make-distance-frame [frame-cons next-frame-fn]
  (let [zero-dist-inp (sc/horizontal-panel
                       :items [(sc/label
                                :text ::zeroing-distance-value
                                :icon (conf/key->icon
                                       ::ball/zeroing-dist-icon))
                               (w/input-set-distance *w-state
                                                     [:c-zero-distance-idx])])]
    (frame-cons *w-state
                (sc/border-panel
                 :vgap 20
                 :center (make-dist-panel *w-state)
                 :south zero-dist-inp)
                next-frame-fn)))


(def ^:private distance-presets
  {:short [25.0 50.0 60.0 70.0 80.0 90.0 100.0]
   :medium [100.0 150.0 200.0 300.0]
   :long [1000.0 1100.0 1200.0 1300.0 1400.0 1500.0]})


(defn- make-dist-preset-frame [frame-cons next-frame-fn]
  (let [group (sc/button-group)]
    (frame-cons *w-state
                (sc/border-panel
                 :border 20
                 :vgap 20
                 :north (sc/label :text ::distance-preset-headder :class :fat)
                 :center (sc/vertical-panel
                          :items [(sc/radio :id :short
                                            :text ::distance-preset-close
                                            :margin 20
                                            :group group)
                                  (sc/radio :id :medium
                                            :text ::distance-preset-medium
                                            :margin 20
                                            :group group)
                                  (sc/radio :id :long
                                            :text ::distance-preset-long
                                            :margin 20
                                            :selected? true
                                            :group group)]))
                #(let [selected-id (sc/config (sc/selection group) :id)
                       distances (get distance-presets selected-id)]
                   (swap! *w-state (fn [w-s] (assoc-in w-s
                                                       [:profile :distances]
                                                       distances)))
                   (next-frame-fn)))))


(defn start-wizard! [main-frame-cons wizard-frame-cons *state]
  (chain-frames! *state
                 main-frame-cons
                 wizard-frame-cons
                 [make-description-frame
                  make-dist-preset-frame
                  make-distance-frame
                  make-zerioing-frame
                  make-rifle-frame
                  make-cartridge-frame
                  make-bullet-frame
                  make-coef-frame]))
