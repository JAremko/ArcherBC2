(ns tvt.a7.profedit.asi
  (:require [seesaw.core :as sc]))


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
  (sc/invoke-now
   (->> report
        (simp-report)
        (sc/dialog
         :type :error
         :option-type :default
         :content)
        (sc/pack!)
        (sc/show!))))
