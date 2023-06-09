(ns tvt.a7.profedit.distances
  (:require [seesaw.core :as sc]
            [seesaw.bind :as sb]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.forms :as sf]
            [seesaw.border :refer [empty-border]]
            [tvt.a7.profedit.config :as conf])
  (:import [javax.swing JList]))


(defn make-switch-pos [dist-cont *state key]
  (let [c-idx-sel [key :c-idx]
        man-inp (w/input-int *state
                             [key :distance]
                             ::prof/distance
                             :columns 2)
        input (sc/select man-inp [:#input])
        units (sc/select man-inp [:#units])
        state->enabled? #(= (prof/get-in-prof % c-idx-sel) -1)]
    (when-not (state->enabled? @*state)
      (sc/config! input :enabled? false)
      (sc/config! units :enabled? false))
    (sb/bind *state
             (sb/transform state->enabled?)
             (sb/tee
              (sb/property input :enabled?)
              (sb/property units :enabled?)))
    (w/forms-with-bg
     :switch-pos-pannel
     "pref,4dlu,pref"
     :items [(sf/separator ::sw-distance) (sf/next-line)
             (sc/label :text ::sw-from-table)
             (w/input-sel-sw-distance dist-cont *state c-idx-sel)
             (sc/label :text ::sw-manual)
             man-inp
             (sf/separator ::sw-zoom) (sf/next-line)
             (sc/label :text ::sw-level)
             (w/input-sel *state [key :zoom]
                          {1 "1X" 2 "2X" 3 "3X" 4 "4X"}
                          ::prof/zoom)
             (sf/separator ::sw-reticle) (sf/next-line)
             (sc/label :text ::sw-index)
             (w/input-int *state
                          [key :reticle-idx]
                          ::prof/reticle-idx
                          :columns 2)])))


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
     :west (sc/border-panel
            :north (sc/label :text ::dist-pan-switch-positions
                             :font conf/font-small)
            :center
            (sc/border-panel
             :center (sc/border-panel
                      :center (sc/tabbed-panel
                               :placement :top
                               :overflow :scroll
                               :tabs
                               [{:title (w/fat-label "A")
                                 :content
                                 (make-switch-pos d-lb *state :sw-pos-a)}
                                {:title (w/fat-label "B")
                                 :content
                                 (make-switch-pos d-lb *state :sw-pos-b)}
                                {:title (w/fat-label "C")
                                 :content
                                 (make-switch-pos d-lb *state :sw-pos-c)}
                                {:title (w/fat-label "D")
                                 :content
                                 (make-switch-pos d-lb *state :sw-pos-d)}]))))
     :center (sc/border-panel
              :vgap 5
              :hgap 5
              :north (sc/label :text ::dist-pan-distances-reorder
                               :font conf/font-small)
              :center (sc/border-panel
                       :north (w/input-distance *state)
                       :center (sc/scrollable d-lb))
              :south btn-del))))
