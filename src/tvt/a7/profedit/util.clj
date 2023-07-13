(ns tvt.a7.profedit.util
  (:require [seesaw.core :as sc]))


(defn- all-screen-devices []
  (->> (java.awt.GraphicsEnvironment/getLocalGraphicsEnvironment)
       .getScreenDevices
       seq))


(defn full-screen?
  ([^java.awt.GraphicsDevice device window]
   (= (sc/to-root window) (.getFullScreenWindow device)))
  ([window]
   (some #(full-screen? % window) (all-screen-devices))))


(defn reload-frame! [frame frame-cons]
  (sc/invoke-later
   (sc/config! frame :on-close :nothing)
   (sc/dispose! frame)
   (sc/show! (frame-cons))))


(defn dispose-frame! [frame]
  (sc/invoke-later
   (sc/config! frame :on-close :nothing)
   (sc/dispose! frame)))
