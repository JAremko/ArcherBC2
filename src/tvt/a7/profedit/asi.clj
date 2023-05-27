(ns tvt.a7.profedit.asi
  (:require [clojure.spec.alpha :as s]
            [tvt.a7.profedit.reticle :as r] ;; NOTE: FOR SPECS
            [tvt.a7.profedit.profile :as p] ;; NOTE: ALSO FOR SPECS
            [seesaw.core :as sc]))


(defn- simp-report [report]
  (->> report
       (str)
       (sc/text :multi-line? true
                :wrap-lines? true
                :editable? false
                :columns 60
                :text)
       (sc/scrollable)))


(defn pop-report! [report spec]
  (->> report
         (simp-report)
         (sc/dialog
          :type :error
          :option-type :default
          :content)
         (sc/pack!)
         (sc/show!)))
