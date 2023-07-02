(ns tvt.a7.profedit.wizard
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [seesaw.forms :as sf]
   [seesaw.border :refer [empty-border]]
   [j18n.core :as j18n])
  (:gen-class))


(defn start-wizard [frame-cons])
