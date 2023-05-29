(ns tvt.a7.profedit.ballistic
  (:require
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.profile :as prof]
   [seesaw.core :as sc]
   [clojure.spec.alpha :as s]
   [seesaw.forms :as sf]
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
                 {:g1 "G1"
                  :g7 "G7"
                  :custom "Custom"}
                 ::prof/bc-type
                 :listen
                 [:selection
                  (fn [e]
                    ;; FIXME: We need to trigger de-focus logic
                    ;; of the element to get actual value of bc-type.
                    ;; It's a HACK. Logic of the event should assessable
                    ;; in a better way.
                    (sc/request-focus! (sc/to-root e))
                    (let [new-deps (get-deps *state)
                          last-deps (deref *last-deps)]
                      (when (not= last-deps new-deps)
                        (regen-func-coefs *state (sc/to-root e))
                        (reset! *last-deps new-deps))))])))


(defn make-g1-g7-row [*state bc-type idx]
  (let [bc-c-key (bc-type->coef-key bc-type)]
    [(sc/label :text "mv:")
     (w/input-int *state [bc-c-key idx :mv] ::prof/mv :columns 5)
     (sc/label :text "bc:")
     (w/input-num *state [bc-c-key idx :bc] ::prof/bc :columns 5)]))


(defn- sync-custom-cofs [*state vpath spec ^AWTEvent e]
  (let [{:keys [min-v max-v]} (meta (s/get-spec spec))
        source (sc/to-widget e)
        new-val (w/str->double (or (sc/value source) ""))]
    (if (and new-val (<= min-v new-val max-v))
      (prof/assoc-in-prof! *state vpath new-val)
      (do (prof/status-err! (str "Value should be in range from "
                                 min-v
                                 " to "
                                 max-v))
          (sc/value! source (w/val->str (prof/get-in-prof* *state vpath)))))))


(defn make-custom-row [*state cofs idx]
  (let [units-cd (sc/text :text "lb/in^2"
                          :editable? false
                          :focusable? false
                          :margin 0)
        mk-units-ma  (sc/text :text "Ma"
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
    [(sc/label :text (str "[" idx "] cd:"))
     (sc/horizontal-panel
      :items [(sc/text :text (:cd (nth cofs idx))
                       :listen [:focus-lost sync-cd]
                       :columns 5)
              units-cd])
     (sc/label :text "ma:")
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
    (into [(sf/span (sc/label "Coefficients") 5) (sf/next-line)]
          (mapcat
           (if (= bc-type :custom)
             (partial make-custom-row *state (state->bc-coef @*state))
             (partial make-g1-g7-row *state bc-type)))
          (range 0 num-rows))))


(defn- make-func-coefs [*state]
  (sc/scrollable
   (sf/forms-panel "pref,4dlu,pref,4dlu,pref,4dlu,pref"
                   :items (make-func-children *state))
   :id :func-pan-wrap))


(defn- rm-last-bc-row [*state e]
  (swap! *state
         (fn [state]
           (let [sel (state->bc-coef-sel state)
                 cofs (get-in state sel)]
             (if (= 1 (count cofs))
               (do
                 (prof/status-err! "Can't delete last coefficient")
                 state)
               (do
                 (prof/status-ok! "Row deleted")
                 (update-in state sel pop))))))
  (regen-func-coefs *state (sc/to-root e)))


(defn- mk-default-cof [state]
  (if (= (state->bc-type state) :custom)
    {:cd 0.0 :ma 0.0}
    {:bc 0.0 :mv 0}))


(defn- add-bc-row [*state e]
  (swap! *state
         (fn [state]
           (update-in state
                      (state->bc-coef-sel state)
                      (fn [cofs]
                        (let [bc-type (state->bc-type state)
                              cnt (count cofs)]
                          ;; NOTE Custom can has up to 200 rows
                          ;;      others - only 5
                          (if (or (and (= bc-type :custom)
                                       (<= cnt (dec 200)))
                                  (<= cnt (dec 5)))
                            (do
                              (prof/status-ok! "New row added")
                              (conj cofs (mk-default-cof state)))
                            (do (prof/status-err! "Can't add more rows")
                                cofs)))))))
  (regen-func-coefs *state (sc/to-root e)))


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
           :north (make-bc-type-sel *state)
           :center (make-func-coefs *state)
           :south
           (sc/horizontal-panel
            :items
            [(sc/button :text "Add"
                        :listen [:action (partial add-bc-row *state)])
             (sc/button :text "Remove"
                        :listen [:action (partial rm-last-bc-row *state)])]))))


(defn make-ballistic-panel [*state]
  (sc/tabbed-panel
   :placement :right
   :overflow :scroll
   :tabs
   [{:title "Rifle"
     :content
     (sc/scrollable
      (sf/forms-panel
       "pref,4dlu,pref"
       :items [(sf/separator "Rifle:") (sf/next-line)
               (sc/label "Twist rate")
               (w/input-num *state
                            [:r-twist]
                            ::prof/r-twist :columns 4)
               (sc/label "Twist direction:")
               (w/input-sel *state
                            [:twist-dir]
                            {:right "right" :left "left"}
                            ::prof/twist-dir)
               (sc/label "Scope offset:")
               (w/input-int *state
                            [:sc-height]
                            ::prof/sc-height
                            :columns 4)
               (sc/label "Muzzle velocity:")
               (w/input-int *state
                            [:c-muzzle-velocity]
                            ::prof/c-muzzle-velocity)
               (sc/label "Powder temperature:")
               (w/input-int *state
                            [:c-zero-temperature]
                            ::prof/c-zero-temperature)
               (sc/label "Ratio:")
               (w/input-num *state
                            [:c-t-coeff]
                            ::prof/c-t-coeff)]))}

    {:title "Bullet"
     :content
     (sc/scrollable
      (sf/forms-panel
       "pref,4dlu,pref"
       :items [(sf/separator "Bullet:") (sf/next-line)
               (sc/label "Diameter:")
               (w/input-num *state [:b-diameter] ::prof/b-diameter
                            :columns 4)
               (sc/label "Weight:")
               (w/input-num *state [:b-weight] ::prof/b-weight
                            :columns 4)
               (sc/label "Length:")
               (w/input-num *state [:b-length] ::prof/b-length
                            :columns 4)]))}

    {:title "Function"
     :content (make-func-panel *state)}]))
