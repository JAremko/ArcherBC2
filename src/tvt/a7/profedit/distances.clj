(ns tvt.a7.profedit.distances
  (:require [seesaw.core :as sc]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.border :refer [empty-border]]
            [tvt.a7.profedit.config :as conf]
            [dk.ative.docjure.spreadsheet :as sp]
            [j18n.core :as j18n]
            [seesaw.forms :as sf]
            [clojure.spec.alpha :as s])
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
       (do (swap! *state
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
                                (prof/assoc-in-prof [:c-zero-distance-idx]
                                                    new_zidx)
                                (prof/assoc-in-prof [:distances]
                                                    new-val)))))))
           (sc/invoke-later
            (doto ^JList d-lb
              (.setSelectedIndex idx)
              (.ensureIndexIsVisible idx))
            (sc/request-focus! d-lb)))
       (prof/status-err! ::del-sel-select-for-deletion)))))


(defn- find-index
  [arr val]
  (let [idx (.indexOf (java.util.Arrays/asList (to-array arr)) val)]
    (if (>= idx 0) idx nil)))


(defn- dist-swapper [state distances]
  (let [zero-dist (nth (prof/get-in-prof state [:distances])
                       (prof/get-in-prof state [:c-zero-distance-idx]))
        {:keys [units min-v max-v]}
        (meta (s/get-spec ::prof/distance))
        dist (mapv parse-double distances)
        dist-z-idx (find-index dist zero-dist)
        new-dist (if dist-z-idx
                   dist
                   (into [zero-dist] dist))]
    (if (s/valid? ::prof/distances new-dist)
      (-> state
          (prof/assoc-in-prof [:distances] new-dist)
          (prof/assoc-in-prof [:c-zero-distance-idx]
                              (or dist-z-idx 0)))
      (throw (Exception. (format (j18n/resource ::dist-import-invalid-data-err)
                                 (j18n/resource units) min-v max-v))))))


(defn- import-from-excel [*state]
  (try
    (let [wb (w/load-excel-from-chooser)
          hv (w/workbook->header-vec wb)
          c-hv (count hv)]
      (cond
        (= 0 c-hv)
        (throw (Exception. (str (j18n/resource ::dist-import-atleast-one-colum-err))))

        (= 1 c-hv)
        (swap! *state dist-swapper (w/get-workbook-column wb 0))

        :else
        (throw (Exception. (str (j18n/resource ::dist-import-too-many-colums-err)))))
      (prof/status-ok! ::dist-import-succ-msg))
    (catch Exception e (prof/status-err!
                        (let [em (.getMessage e)]
                          (if (seq em)
                            em
                            (j18n/resource ::dist-import-bad-table-err))))
           nil)))


(defn- export-to-excel [*state frame]
  (try
    (let [distances (prof/get-in-prof* *state [:distances])
          {:keys [units]} (meta (s/get-spec ::prof/distance))
          wb (sp/create-workbook
              (j18n/resource ::dist-export-sheet-title)
              (into [[(format (j18n/resource ::dist-export-col-title)
                              (j18n/resource units))]]
                    (map #(vector (str %)))
                    distances))]
      (dorun (for [sheet (sp/sheet-seq wb)]
               (sp/auto-size-all-columns! sheet)))
      (w/save-excel-as-chooser *state frame "dist" wb)
      (prof/status-ok! ::dist-export-succ-msg))
    (catch Exception e (prof/status-err! (.getMessage e)) nil)))


(defn make-dist-panel [*state]
  (let [d-lb (w/distances-listbox *state)

        btn-del (sc/button
                 :icon (conf/key->icon :distances-button-del-icon)
                 :text ::dist-pan-delete-selected
                 :listen [:action (fn [_] (del-selected! *state d-lb))])

        btn-e-imp (sc/button
                   :text (j18n/resource ::dist-import-bnt-text)
                   :listen [:action (fn [_] (import-from-excel *state))])

        btn-e-exp (sc/button
                   :text (j18n/resource ::dist-export-bnt-text)
                   :listen [:action (fn [e] (export-to-excel *state
                                                             (sc/to-root e)))])]

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
              :south (sc/horizontal-panel
                      :items [btn-del
                              (sc/separator :orientation :vertical)
                              (->> :file-excel
                                   conf/key->icon
                                   sc/icon
                                   (sc/label :icon))
                              (sf/forms-panel
                               "pref"
                               :items [btn-e-exp btn-e-imp])])))))
