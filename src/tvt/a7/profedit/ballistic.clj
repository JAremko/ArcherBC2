(ns tvt.a7.profedit.ballistic
  (:require
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [clojure.spec.alpha :as s]
   [seesaw.forms :as sf]
   [j18n.core :as j18n]
   [seesaw.bind :as ssb])
  (:import [java.awt AWTEvent]))


(declare ^:private make-func-coefs)


(defn- bc-type->coef-key [bc-type]
  (bc-type {:g1 :coef-g1 :g7 :coef-g7 :custom :coef-custom}))


(defn- state->bc-type [state]
  (:bc-type (prof/state->cur-prof state)))


(defn- state->bc-coef-sel [state]
  (let [bc-type (state->bc-type state)]
    [:profiles (:selected-profile state) (bc-type->coef-key bc-type)]))


(defn- state->bc-coef [state]
  (get-in state (state->bc-coef-sel state)))


(defn- regen-func-coefs [*state frame]
  (sc/invoke-later
   (let [op (sc/select frame [:#func-pan-wrap])
         cont (sc/select frame [:#func-pan-cont])
         np (make-func-coefs *state)]
     (sc/replace! cont op np))))


(defn- make-bc-type-sel [*state]
  (let [get-deps (fn [*state]
                   (let [state (deref *state)]
                     {:bc-type (state->bc-type state)
                      :count (count (state->bc-coef state))}))
        *last-deps (atom nil)]
    (w/input-sel *state
                 [:bc-type]
                 {:g1 (j18n/resource ::g1)
                  :g7 (j18n/resource ::g7)
                  :custom (j18n/resource ::custom)}
                 ::prof/bc-type
                 :listen
                 [:selection
                  (fn [e]
                    (sc/request-focus! (sc/to-root e))
                    (let [new-deps (get-deps *state)
                          last-deps (deref *last-deps)]
                      (when (not= last-deps new-deps)
                        (regen-func-coefs *state (sc/to-root e))
                        (reset! *last-deps new-deps))))])))


(defn make-g1-g7-row [*state bc-type idx]
  (let [bc-c-key (bc-type->coef-key bc-type)]
    [(sc/label :text ::mv)
     (w/input-num *state [bc-c-key idx :mv] ::prof/mv :columns 5)
     (sc/label :text ::bc)
     (w/input-num *state [bc-c-key idx :bc] ::prof/bc :columns 5)]))


(defn- sync-custom-cofs [*state vpath spec ^AWTEvent e]
  (let [{:keys [min-v max-v fraction-digits]} (meta (s/get-spec spec))
        source (sc/to-widget e)
        new-val (w/str->double (or (sc/value source) "")
                               fraction-digits)]
    (if (and new-val (<= min-v new-val max-v))
      (prof/assoc-in-prof! *state vpath new-val)
      (do (prof/status-err! (format (j18n/resource ::range-error-fmt)
                                    (str min-v)
                                    (str max-v)))
          (sc/value! source (w/val->str (prof/get-in-prof* *state vpath)
                                        fraction-digits))))))


(defn make-custom-row [*state cofs idx]
  (let [mk-units-ma  (sc/text :text ::ma-units
                              :editable? false
                              :focusable? false
                              :margin 0)
        sync-cd (partial sync-custom-cofs
                         *state
                         [:coef-custom idx :cd]
                         ::prof/cd)
        sync-ma (partial sync-custom-cofs
                         *state
                         [:coef-custom idx :ma]
                         ::prof/ma)]
    [(sc/label :text (str "[" idx "] " (j18n/resource ::cd)))
     (sc/horizontal-panel
      :items [(sc/text :text (:cd (nth cofs idx))
                       :listen [:focus-lost sync-cd]
                       :columns 5)])
     (sc/label :text ::ma)
     (sc/horizontal-panel
      :items [(sc/text :text (:ma (nth cofs idx))
                       :listen [:focus-lost sync-ma]
                       :columns 5)
              mk-units-ma])]))


(defn- make-func-children [*state]
  (let [state (deref *state)
        bc-coef (state->bc-coef state)
        bc-type (state->bc-type state)
        num-rows (count bc-coef)]
    (into [(sf/span (sc/label ::coefs) 5) (sf/next-line)]
          (mapcat
           (if (= bc-type :custom)
             (partial make-custom-row *state (state->bc-coef @*state))
             (partial make-g1-g7-row *state bc-type)))
          (range 0 num-rows))))


(defn- make-func-coefs [*state]
  (sc/scrollable
   (w/forms-with-bg
    :func-coeficients-panel
    "pref,4dlu,pref,4dlu,pref,4dlu,pref"
    :items (make-func-children *state))
   :id :func-pan-wrap))


(defn- bind-coef-upd [*state w]
  (ssb/bind *state
            (ssb/some
             (w/mk-debounced-transform
              #(when (= :custom (prof/get-in-prof % [:bc-type]))
                 (count (prof/get-in-prof % [:coef-custom])))))
            (ssb/b-do* (fn [_] (regen-func-coefs *state (sc/to-root w)))))
  w)

;; NOTE:  current implementation doesn't update input widgets when
;;        individual values change in the state atom.
(defn- make-func-panel [*state]
  (bind-coef-upd
   *state (sc/border-panel
           :id :func-pan-cont
           :vgap 10
           :center (make-func-coefs *state))))


(defn make-zeroing-panel [*pa]
  (w/forms-with-bg
   :zeroing-panel
   "pref,4dlu,pref,20dlu,pref,4dlu,pref"
   :items [(sc/label :text ::root-tab-zeroing) (sf/next-line)
           (sc/label ::general-section-coordinates-zero-x)
           (w/input-0125-mult *pa [:zero-x] ::prof/zero-x :columns 4)
           (sc/label ::general-section-coordinates-zero-y)
           (w/input-0125-mult *pa [:zero-y] ::prof/zero-y :columns 4)
           (sc/label :text ::general-section-direction-distance
                     :icon (conf/key->icon ::zeroing-dist-icon))
           (w/input-set-distance *pa [:c-zero-distance-idx])
           (sc/label ::general-section-direction-pitch)
           (w/input-num *pa [:c-zero-w-pitch] ::prof/c-zero-w-pitch :columns 4)
           (sc/label ::general-section-temperature-air)
           (w/input-num *pa [:c-zero-air-temperature]
                        ::prof/c-zero-air-temperature :columns 4)
           (sc/label ::general-section-temperature-powder)
           (w/input-num *pa [:c-zero-p-temperature]
                        ::prof/c-zero-p-temperature :columns 4)
           (sc/label ::general-section-environment-pressure)
           (w/input-num, *pa [:c-zero-air-pressure]
                        ::prof/c-zero-air-pressure :columns 4)
           (sc/label ::general-section-environment-humidity)
           (w/input-num *pa [:c-zero-air-humidity]
                        ::prof/c-zero-air-humidity :columns 4)]))


(defn make-ballistic-panel [*state]
  (sc/tabbed-panel
   :placement :right
   :overflow :scroll
   :tabs
   [{:tip (j18n/resource ::rifle-tab-title)
     :icon (conf/key->icon :tab-icon-rifle)
     :content
     (sc/scrollable
      (w/forms-with-bg
       :rifle-tab-panel
       "pref,4dlu,pref"
       :items [(sc/label :text ::rifle-title) (sf/next-line)
               (sc/label ::rifle-twist-rate)
               (w/input-num *state
                            [:r-twist]
                            ::prof/r-twist :columns 4)
               (sc/label ::rifle-twist-direction)
               (w/input-sel *state
                            [:twist-dir]
                            {:right (j18n/resource ::rifle-twist-right)
                             :left (j18n/resource ::rifle-twist-left)}
                            ::prof/twist-dir)
               (sc/label ::rifle-scope-offset)
               (w/input-num *state
                            [:sc-height]
                            ::prof/sc-height
                            :columns 4)]))}

    {:tip (j18n/resource ::rifle-cartridge-title)
     :icon (conf/key->icon :tab-icon-cartridge)
     :content
     (sc/scrollable
      (w/forms-with-bg
       :cartridge-tab-panel
       "pref,4dlu,pref"
       :items [(sc/label :text ::rifle-cartridge-title) (sf/next-line)
               (sc/label ::rifle-muzzle-velocity)
               (w/input-num *state
                            [:c-muzzle-velocity]
                            ::prof/c-muzzle-velocity)
               (sc/label ::rifle-powder-temperature)
               (w/input-num *state
                            [:c-zero-temperature]
                            ::prof/c-zero-temperature)
               (sc/label ::rifle-ratio)
               (w/input-num *state
                            [:c-t-coeff]
                            ::prof/c-t-coeff)]))}

    {:tip (j18n/resource ::bullet-tab-title)
     :icon (conf/key->icon :tab-icon-bullet)
     :content
     (sc/vertical-panel
      :items
      [(w/forms-with-bg
        :bullet-tab-panel
        "pref,4dlu,pref"
        :items [(sc/label :text ::bullet-bullet) (sf/next-line)
                (sc/label ::bullet-diameter)
                (w/input-num *state [:b-diameter] ::prof/b-diameter
                             :columns 4)
                (sc/label ::bullet-weight)
                (w/input-num *state [:b-weight] ::prof/b-weight
                             :columns 4)
                (sc/label ::bullet-length)
                (w/input-num *state [:b-length] ::prof/b-length
                             :columns 4)
                (sc/label :text ::function-tab-title)
                (make-bc-type-sel *state)
                (sc/label :text ::function-tab-row-count)
                (w/input-coef-count *state regen-func-coefs)])
       (make-func-panel *state)])}

    {:tip (j18n/resource ::root-tab-zeroing)
     :icon (conf/key->icon :tab-icon-zeroing)
     :content (make-zeroing-panel *state)}]))
