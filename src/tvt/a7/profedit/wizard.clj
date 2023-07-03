(ns tvt.a7.profedit.wizard
  (:require
   [seesaw.options :as so]
   [seesaw.core :as sc]
   [seesaw.bind :as sb]
   [seesaw.forms :as sf]
   [seesaw.event :as se]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [tvt.a7.profedit.asi :as ass]
   [seesaw.border :refer [empty-border]]
   [j18n.core :as j18n]
   [clojure.spec.alpha :as s])
  (:import
   [javax.swing.text
    DefaultFormatterFactory
    NumberFormatter
    DefaultFormatter]
   [java.text NumberFormat DecimalFormat]
   [javax.swing JFormattedTextField]))


(defn- valid-profile? [profile]
  (s/valid? ::prof/profile profile))


(defn- report-invalid-profile! [profile]
  (ass/pop-report! (prof/val-explain ::prof/profile profile))
  nil)


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
       (w/round-to-closest-0125-mul (w/str->double s fraction-digits))))

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
      (so/apply-options opts))))



(def template
  {:profile
   {:profile-name nil
    :cartridge-name nil
    :bullet-name nil
    :short-name-top nil
    :short-name-bot nil
    :user-note ""
    :zero-x -12.1
    :zero-y 10.01
    :distances [100.10 100.0 120.0 130.0 140.0
                150.0 160.0 170.0 180.0 190.0
                200.0 210.0 220.0 250.0 300.0
                1000.0 1500.0 1600.0 1700.0 2000.0 3000.0]
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
    :sc-height 90.0
    :r-twist 9.45
    :twist-dir :right
    :c-muzzle-velocity 890.0
    :c-zero-temperature 25.0
    :c-t-coeff 1.03
    :c-zero-distance-idx 0
    :c-zero-air-temperature 20.0
    :c-zero-air-pressure 990.0
    :c-zero-air-humidity 51.0
    :c-zero-w-pitch 0.0
    :c-zero-p-temperature 20.0
    :b-diameter 0.338
    :b-weight 250.0
    :b-length 1.55
    :coef-g1 [{:bc 0.343 :mv 850.0}
              {:bc 0.335 :mv 600.0}
              {:bc 0.325 :mv 400.0}
              {:bc 0.327 :mv 0.0}
              {:bc 0.001 :mv 0.0}]
    :coef-g7 [{:bc 0.343 :mv 850.0}
              {:bc 0.327 :mv 0.0}
              {:bc 0.001 :mv 0.0}]
    :coef-custom [{:cd 0.8 :ma 1.0}
                  {:cd 0.3 :ma 0.6}
                  {:cd 0.1 :ma 0.4}]
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
   [(sc/label :text ::app/general-section-profile) (sf/next-line)
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


(defn- make-test-frame [frame-cons next-frame-fn]
  (frame-cons *w-state (sc/label "FOOBAR") next-frame-fn))


(defn start-wizard! [main-frame-cons wizard-frame-cons *state]
  (chain-frames! *state
                 main-frame-cons
                 wizard-frame-cons
                 [make-description-frame make-test-frame]))
