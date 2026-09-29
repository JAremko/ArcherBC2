(ns tvt.a7.profedit.asi
  (:require [seesaw.core :as sc]
            [tvt.a7.profedit.log :as log]))


(defn- simp-report [report]
  (->> report
       (str)
       (sc/text :multi-line? true
                :wrap-lines? true
                :editable? false
                :columns 60
                :text)
       (sc/scrollable)))


(defn pop-report! [report]
  ;; The detailed spec problem is logged at its source (without copying the
  ;; full profile and user note). This event proves that a report dialog was
  ;; actually presented and lets support correlate it with that source event.
  (log/warn! "validation-report-shown"
             {:report-length (count (str report))})
  (sc/invoke-now
   (->> report
        (simp-report)
        (sc/dialog
         :type :error
         :option-type :default
         :content)
        (sc/pack!)
        (sc/show!))))
