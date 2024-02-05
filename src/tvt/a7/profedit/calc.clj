(ns tvt.a7.profedit.calc
  (:require [clojure.spec.alpha :as s]
            [j18n.core :as j18n]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.nullableinp :as ni]
            [seesaw.border :refer [empty-border]]
            [seesaw.forms :as sf]
            [seesaw.core :as sc]
            [tvt.a7.profedit.profile :as prof])
  (:import [numericutil CustomNumberFormatter]
           [powder SensitivityCalculator]))


(def ^:private row-count 10)


(defn- make-pwdr-sens-calc-state []
  {:profile {:pwdr-sens-table (->> {:temperature nil :velocity nil}
                                   constantly
                                   (repeatedly)
                                   (into [] (take row-count)))}})


(s/def ::temperature (s/nilable ::prof/c-zero-p-temperature))


(s/def ::velocity (s/nilable ::prof/c-muzzle-velocity))


(s/def ::pwdr-sens-table-row (s/keys :req-un [::temperature ::velocity]))


(s/def ::pwdr-sens-table (s/coll-of ::pwdr-sens-table-row
                                    :count row-count
                                    :kind vector))


(defn- mk-nulable-number-fmt
  [_ fraction-digits]
  (proxy [CustomNumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (w/str->double s fraction-digits)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (if value
         (w/val->str (double value) fraction-digits)
         "")))))


(defn- nulable-input-num [& args]
  (apply ni/create-input mk-nulable-number-fmt args))


(defn- make-pwdr-sens-calc-row [*calc-state idx]
  [(nulable-input-num *calc-state
                      [:pwdr-sens-table idx :temperature]
                      ::prof/c-zero-p-temperature :columns 5)
   (nulable-input-num *calc-state
                      [:pwdr-sens-table idx :velocity]
                      ::prof/c-muzzle-velocity :columns 5)])


(defn- make-pwdr-sens-calc-children [*calc-state]
  (let [calc-state (deref *calc-state)
        rows (get-in calc-state [:profile :pwdr-sens-table])
        num-rows (count rows)]
    (into [(sf/span (sc/label :text ::ballistic-table :class :fat) 3) (sf/next-line)]
          (mapcat
           (partial make-pwdr-sens-calc-row *calc-state))
          (range 0 num-rows))))


(defn- make-func-coefs [*calc-state]
  (sc/border-panel :border (empty-border :thickness 20)
                   :center (sc/scrollable
                            (sf/forms-panel
                             "pref,4dlu,pref"
                             :items (make-pwdr-sens-calc-children *calc-state)))))


(defn calculateSensitivity [data]
  (let [java-array (into-array (map (fn [{:keys [temperature velocity]}]
                                      (into-array Double/TYPE [(double temperature)
                                                               (double velocity)]))
                                    data))]
    (SensitivityCalculator/calculateSensitivity java-array)))


(defn show-pwdr-sens-calc-frame [*state parent]
  (let [*c-s (atom (make-pwdr-sens-calc-state))]
    (try
      (add-watch *c-s :refresh-*state
                 (fn [_ _ _ new-state]
                   (let [table (get-in new-state [:profile :pwdr-sens-table])
                         f-tb (->> table
                                   (filter (every-pred :temperature :velocity))
                                   (group-by :temperature)
                                   (map (fn [[_ entries]] (first entries)))
                                   (sort-by :temperature >)
                                   (seq)
                                   (vec))]
                     (when (>= (count f-tb) 1)
                       (let [coef (calculateSensitivity f-tb)]
                         (if (s/valid? ::prof/c-t-coeff coef)
                           (swap! *state #(assoc-in % [:profile :c-t-coeff] coef))
                           (prof/status-err! (str
                                              (j18n/resource ::status-bad-coef-pref)
                                              " "
                                              (prof/format-spec-err ::prof/c-t-coeff
                                                                    coef)))))))))
      (->> *c-s
           make-func-coefs
           (sc/dialog :parent parent
                      :title ::calc-title
                      :modal? true
                      :content)
           sc/pack!
           sc/show!)
      (finally (remove-watch *c-s :refresh-*state)))))
