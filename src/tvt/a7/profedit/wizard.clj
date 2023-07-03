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


(defn input-str
  [*state vpath spec & opts]
  (let [max-length (:max-length (meta (s/get-spec spec)))
        formatter (new DefaultFormatterFactory
                       (w/wrap-formatter (w/mk-str-fmt-default max-length))
                       (w/wrap-formatter (w/mk-str-fmt-display max-length))
                       (w/wrap-formatter (w/mk-str-fmt-edit max-length))
                       (w/wrap-formatter (w/mk-str-fmt-null max-length "")))
        jf (sc/construct JFormattedTextField formatter)]
    (sb/bind *state
              (sb/some (w/mk-debounced-transform #(prof/get-in-prof % vpath)))
              (sb/value jf))
    (doto jf
      (w/add-tooltip (format (j18n/resource ::str-input-tooltip-text)
                           (str max-length)))
      (se/listen
       :focus-lost (partial w/sync-and-commit *state vpath spec)
       :key-pressed #(when (w/commit-key-pressed? %)
                       (w/sync-and-commit *state vpath spec %)))
      (so/apply-options opts))))


(defmacro chain-frames [*w-state w-frame-cons fns]
  (let [reversed-fns (reverse fns)
        start (first reversed-fns)
        rest (rest reversed-fns)]
    `(partial
      ~(reduce (fn [acc fn]
                 `(partial ~fn ~*w-state ~w-frame-cons ~acc))
               start
               rest))))


(def ^:private *wizard-atom {})


(defn- make-test-frame [*w-atom frame-cons next-frame-fn]
  (frame-cons *w-atom (sc/label "FOOBAR") next-frame-fn))


(defn- make-final-frame []
  (when-not (w/save-as-chooser *wizard-atom)
    (reset! fio/*current-fp nil)))


(defn start-wizard! [main-frame-cons wizard-frame-cons *state]
  (reset! *wizard-atom (:profile prof/example))
  (let [f-chain (chain-frames *wizard-atom
                              wizard-frame-cons
                              [make-test-frame
                               make-test-frame
                               make-final-frame])]
    (swap! *state #(assoc % :profile (deref *wizard-atom))))
  (sc/show! (main-frame-cons)))
