(ns tvt.a7.profedit.distances
  (:require [seesaw.core :as sc]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.border :refer [empty-border]]
            [tvt.a7.profedit.config :as conf])
  (:import [javax.swing JList]))


(defn- del-selected! [*state ^JList d-lb]
  (sc/invoke-later
   (let [idx (.getSelectedIndex d-lb)]
     (if (> idx -1)
       ;; TODO Join state updates into single transaction
       (do
         (prof/update-in-prof!
          *state
          [:distances]
          ::prof/distances
          (fn [cur-val]
            (let [new-val (into (subvec cur-val 0 idx)
                                (subvec cur-val (inc idx)))]
              (if (w/zeroing-dist-idx? *state idx)
                (do (prof/status-err! ::del-sel-cant-delete)
                    cur-val)
                (do (prof/status-ok! ::del-sel-distance-deleted)
                    new-val)))))
         (when (> (prof/get-in-prof* *state [:c-zero-distance-idx]) idx)
           (prof/update-in-prof! *state [:c-zero-distance-idx] dec)))
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
     :north (sc/label :text :tvt.a7.profedit.distances/distances-tab-header)
     :center (sc/border-panel
              :vgap 5
              :hgap 5
              :north (sc/label :text ::dist-pan-distances-reorder
                               :font conf/font-small)
              :center (sc/border-panel
                       :north (w/input-distance *state)
                       :center (sc/scrollable d-lb))
              :south btn-del))))
