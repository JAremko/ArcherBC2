(ns tvt.a7.profedit.distances
  (:require [seesaw.core :as sc]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.forms :as sf]
            [seesaw.color :refer [default-color]]
            [seesaw.border :refer [line-border empty-border]])
  (:import [javax.swing JList]))


(defn make-switch-pos [dist-cont *state key]
  (sf/forms-panel
   "pref,4dlu,pref"
   :items [(sf/separator "Distance") (sf/next-line)
           (sc/label :text "From table:")
           (w/input-sel-distance
            *state
            [key :c-idx]
            :listen [:action (fn [_]
                               (->> [:#distance-list]
                                    (sc/select dist-cont)
                                    sc/repaint!
                                    sc/invoke-later))])
           (sc/label :text "Manual:")
           (w/input-int *state
                        [key :distance]
                        ::prof/distance
                        :columns 2)
           (sc/label :text "Use:")
           (w/input-sel *state [key :distance-from]
                        {:value "Manual" :index "Table"}
                        ::prof/distance-from)
           (sf/separator "Zoom") (sf/next-line)
           (sc/label :text "level:")
           (w/input-sel *state [key :zoom]
                        {1 "1X" 2 "2X" 3 "3X" 4 "4X"}
                        ::prof/zoom)
           (sf/separator "Reticle") (sf/next-line)
           (sc/label :text "index:")
           (w/input-int *state
                        [key :reticle-idx]
                        ::prof/reticle-idx
                        :columns 2)]))


(defn make-dist-panel [*state]
  (let [d-lb (w/distances-listbox *state)
        delete-selected
        (fn [_]
          (sc/invoke-later
           (let [idx (.getSelectedIndex ^JList d-lb)]
             (if (> idx -1)
               (prof/update-in-prof!
                *state
                [:distances]
                ::prof/distances
                (fn [cur-val]
                  ;; FIXME Hard to read X_X
                  (let [new-val (into (subvec cur-val 0 idx)
                                      (subvec cur-val (inc idx)))]
                    (if (>= (prof/get-in-prof*
                             *state
                             [:c-zero-distance-idx])
                            (count new-val))
                      (do (prof/status-err! (str "First move zeroing distance "
                                                 "to a lower index"))
                          cur-val)
                      (if (w/zeroing-dist-idx? *state idx)
                        (do (prof/status-err! "Can't delete zeroing distance")
                            cur-val)
                        (do (prof/status-ok! "Distance deleted")
                            new-val))))))
               (prof/status-err! "Please select distance for deletion")))))
        btn-del (sc/button :text "Delete selected"
                           :listen [:action delete-selected])]
    (sc/border-panel
     :hgap 20
     :vgap 20
     :border (empty-border :thickness 5)
     :center (sc/border-panel
              :north (sc/label :text "Switch positions:")
              :center
              (sc/border-panel
               :border (line-border
                        :thickness 1
                        :color (default-color "TextField.foreground"))
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
            :north (sc/label :text "Distances(drag to reorder):")
            :center (sc/border-panel
                     :south btn-del
                     :center (sc/scrollable d-lb))
            :south (w/input-distance *state)))))
