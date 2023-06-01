(ns tvt.a7.profedit.distances
  (:require [seesaw.core :as sc]
            [seesaw.bind :as sb]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.forms :as sf]
            [seesaw.border :refer [empty-border]])
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
    (sf/forms-panel
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
       (prof/update-in-prof!
        *state
        [:distances]
        ::prof/distances
        (fn [cur-val]
          (let [new-val (into (subvec cur-val 0 idx)
                              (subvec cur-val (inc idx)))
                nw-cnt (count new-val)]
            (if (>= (prof/get-in-prof* *state [:c-zero-distance-idx]) nw-cnt)
              (do (prof/status-err! ::del-sel-move-zeroing)
                  cur-val)
              (if (w/zeroing-dist-idx? *state idx)
                (do (prof/status-err! ::del-sel-cant-delete)
                    cur-val)
                (do (prof/status-ok! ::del-sel-distance-deleted)
                    new-val))))))
       (prof/status-err! ::del-sel-select-for-deletion)))))


(defn make-dist-panel [*state]
  (let [d-lb (w/distances-listbox *state)
        btn-del (sc/button
                 :text ::dist-pan-delete-selected
                 :listen [:action (fn [_] (del-selected! *state d-lb))])]
    (sc/border-panel
     :hgap 20
     :vgap 20
     :border (empty-border :thickness 5)
     :center (sc/border-panel
              :north (sc/label :text ::dist-pan-switch-positions)
              :center
              (sc/border-panel
               :center (sc/border-panel
                        :center (sc/tabbed-panel
                                 :placement :right
                                 :overflow :scroll
                                 :tabs
                                 [{:title "A" :content
                                   (make-switch-pos d-lb *state :sw-pos-a)}
                                  {:title "B" :content
                                   (make-switch-pos d-lb *state :sw-pos-b)}
                                  {:title "C" :content
                                   (make-switch-pos d-lb *state :sw-pos-c)}
                                  {:title "D" :content
                                   (make-switch-pos d-lb *state :sw-pos-d)}]))))
     :west (sc/border-panel
            :vgap 5
            :hgap 5
            :north (sc/label :text ::dist-pan-distances-reorder)
            :center (sc/border-panel
                     :south btn-del
                     :center (sc/scrollable d-lb))
            :south (w/input-distance *state)))))
