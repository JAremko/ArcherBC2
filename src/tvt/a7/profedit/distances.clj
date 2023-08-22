(ns tvt.a7.profedit.distances
  (:require [seesaw.core :as sc]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.border :refer [empty-border]]
            [tvt.a7.profedit.config :as conf])
  (:import [javax.swing JList]))


(defn- del-selected! [*state ^JList d-lb]
  (sc/invoke-later
   (let [idx (.getSelectedIndex d-lb)
         zeroing-dist-idx? (fn [state dist-idx]
                             (let [zd-idx (prof/get-in-prof
                                           state
                                           [:c-zero-distance-idx])]
                               (= zd-idx dist-idx)))]
     (if (> idx -1)
       (swap! *state
              (fn [state]
                (let [cur-val (prof/get-in-prof state [:distances])
                      new-val (into (subvec cur-val 0 idx)
                                    (subvec cur-val (inc idx)))
                      cur-zidx (prof/get-in-prof state [:c-zero-distance-idx])
                      new_zidx (if (> cur-zidx idx) (dec cur-zidx) cur-zidx)]
                  (if (zeroing-dist-idx? state idx)
                    (do (prof/status-err! ::del-sel-cant-delete)
                        state)
                    (do (prof/status-ok! ::del-sel-distance-deleted)
                        (-> state
                            (prof/assoc-in-prof [:c-zero-distance-idx] new_zidx)
                            (prof/assoc-in-prof [:distances] new-val)))))))
       (prof/status-err! ::del-sel-select-for-deletion)))))


(defn make-dist-panel [*state]
  (let [d-lb (w/distances-listbox *state)
        btn-del (sc/button
                 :icon (conf/key->icon :distances-button-del-icon)
                 :text ::dist-pan-delete-selected
                 :listen [:action (fn [_] (del-selected! *state d-lb))])]
    (sc/border-panel
     :hgap 20
     :vgap 20
     :border (empty-border :thickness 5)
     :north (sc/label :text :tvt.a7.profedit.distances/distances-tab-header
                      :class :fat)
     :center (sc/border-panel
              :vgap 5
              :hgap 5
              :north (sc/label :text ::dist-pan-distances-reorder
                               :font conf/font-small)
              :center (sc/border-panel
                       :north (w/input-distance *state)
                       :center (sc/scrollable d-lb))
              :south btn-del))))
