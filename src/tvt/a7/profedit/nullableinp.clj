(ns tvt.a7.profedit.nullableinp
  (:require
   [seesaw.core :as sc]
   [seesaw.bind :as sb]
   [seesaw.event :as se]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.widgets :as w]
   [j18n.core :as j18n]
   [clojure.spec.alpha :as s])
  (:import [javax.swing.text
            DefaultFormatterFactory]
           [javax.swing JFormattedTextField]))


(defn create-input [formatter *state vpath spec & opts]
  (let [{:keys [min-v max-v units fraction-digits]} (meta (s/get-spec spec))
        wrapped-fmt (w/wrap-formatter
                     (formatter (partial prof/get-in-prof* *state vpath)
                                fraction-digits))
        fmtr (new DefaultFormatterFactory
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt)
        jf (sc/construct JFormattedTextField fmtr)
        tooltip-text (format (j18n/resource ::w/input-tooltip-text)
                             (str min-v), (str max-v))]
    (sc/config! jf :id :input)
    (sb/bind *state
             (sb/some (w/mk-debounced-transform #(prof/get-in-prof % vpath)))
             (sb/value jf))
    (w/add-tooltip
     (sc/horizontal-panel
      :items
      (w/add-units
       (doto jf
         (w/add-tooltip tooltip-text)
         (se/listen
          :focus-lost (partial w/sync-and-commit *state vpath spec)
          :key-pressed #(when (w/commit-key-pressed? %)
                          (w/sync-and-commit *state vpath spec %)))
         (w/opts-on-nonempty-input opts))
       units))
     tooltip-text)))
