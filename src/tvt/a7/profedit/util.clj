(ns tvt.a7.profedit.util
  (:require [seesaw.core :as sc])
  (:import [javax.swing JFrame]))


(defn maximized?
  [^javax.swing.JFrame frame]
  (= (.getExtendedState frame) JFrame/MAXIMIZED_BOTH))


(defn maximize!
  [^javax.swing.JFrame frame]
  (sc/show! frame)
  (sc/invoke-later (.setExtendedState frame JFrame/MAXIMIZED_BOTH)))


(defn reload-frame! [frame frame-cons]
  (sc/invoke-later
   (let [was-maximized? (maximized? frame)]
     (sc/config! frame :on-close :nothing)
     (sc/dispose! frame)
     (if was-maximized?
       (maximize! (frame-cons))
       (sc/show! (frame-cons))))))


(defn dispose-frame! [frame]
  (sc/invoke-later
   (sc/config! frame :on-close :nothing)
   (sc/dispose! frame)))
