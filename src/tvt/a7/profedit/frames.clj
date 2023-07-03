(ns tvt.a7.profedit.frames
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.actions :as a]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [seesaw.forms :as sf]
   [j18n.core :as j18n])
  (:gen-class))


(defn make-status-bar []
  (sc/vertical-panel
   :items
   [(sc/separator :orientation :horizontal)
    (w/status)]))


(defn pack-with-gap! [frame]
  (let [size (sc/config (sc/pack! frame) :size)
        height (. ^java.awt.Dimension size height)
        width (. ^java.awt.Dimension size width)]
    (sc/config! frame :size [(+ 0 width) :by (+ 0 height)])))


(defn make-menu-file [*state make-frame-main make-frame-wizard]
  (sc/menu
   :text ::frame-file-menu
   :icon (conf/key->icon :actions-group-menu)
   :items
   [(a/act-new! make-frame-main make-frame-wizard *state)
    (a/act-open! make-frame-main *state)
    (a/act-save! *state)
    (a/act-save-as! *state)
    (a/act-reload! make-frame-main *state)
    (a/act-import! make-frame-main *state)
    (a/act-export! *state)]))


(defn make-menu-themes [make-frame]
  (let [at! (fn [name key] (a/act-theme! make-frame name key))]
    (sc/menu
     :text ::frame-themes-menu
     :icon (conf/key->icon :actions-group-theme)
     :items
     [(at! ::action-theme-dark :dark)
      (at! ::action-theme-light :light)
      (at! ::action-theme-sol-dark :sol-dark)
      (at! ::action-theme-sol-light :sol-light)
      (at! ::action-theme-hi-dark :hi-dark)
      (at! ::action-theme-hi-light :hi-light)])))


(defn make-menu-languages [make-frame]
  (sc/menu
   :text ::frame-language-menu
   :icon (conf/key->icon :icon-languages)
   :items
   [(a/act-language-en! make-frame)
    (a/act-language-ua! make-frame)]))


(defn make-frame-wizard [*wizard-state content next-frame-cons]
  (let [next-button (sc/button :text "Next"
                               :listen
                               [:action (fn [e]
                                          (sc/dispose! (sc/to-root e))
                                          (next-frame-cons))])
        frame (sc/frame
               :icon (conf/key->icon :icon-frame)
               :id :frame-main
               :on-close
               (if (System/getProperty "repl") :dispose :exit)
               :menubar
               (sc/menubar
                :items [(make-menu-file *wizard-state make-frame-wizard
                                        make-frame-wizard)
                        (make-menu-themes make-frame-wizard)
                        (make-menu-languages make-frame-wizard)])
               :content (sc/border-panel :center content
                                         :north next-button))]
    frame))


(defn make-frame-main [*state content]
    (sc/frame
     :icon (conf/key->icon :icon-frame)
     :id :frame-main
     :on-close
     (if (System/getProperty "repl") :dispose :exit)
     :menubar
     (sc/menubar
      :items [(make-menu-file *state make-frame-main make-frame-wizard)
              (make-menu-themes make-frame-main)
              (make-menu-languages make-frame-main)])
     :content content))
