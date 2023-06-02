(ns tvt.a7.profedit.app
  (:require
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.ballistic :refer [make-ballistic-panel]]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [seesaw.forms :as sf]
   [seesaw.border :refer [empty-border]])
  (:import [java.util Locale])
  (:gen-class))


(def *pa (atom prof/example))


(defn make-general-panel []
  (sf/forms-panel
   "pref,4dlu,pref,40dlu,pref,4dlu,pref,100dlu,pref"
   :items [(sf/separator ::general-section-profile) (sf/next-line)
           (sc/label ::general-section-profile-name)
           (sf/span (w/input-str *pa [:profile-name] ::prof/profile-name) 7)
           (sc/label ::general-section-profile-top)
           (w/input-str *pa [:short-name-top] ::prof/short-name-top :columns 8)
           (sf/next-line)
           (sc/label ::general-section-profile-bottom)
           (w/input-str *pa [:short-name-bot] ::prof/short-name-bot :columns 8)
           (sf/next-line)
           (sf/separator ::general-section-round) (sf/next-line)
           (sc/label ::general-section-round-cartridge)
           (sf/span (w/input-str *pa [:cartridge-name] ::prof/cartridge-name) 7)
           (sc/label ::general-section-round-bullet)
           (sf/span (w/input-str *pa [:bullet-name] ::prof/bullet-name) 7)
           (sf/next-line)
           (sf/separator ::general-section-user-note) (sf/next-line)
           (sf/span (w/input-mul-text *pa [:user-note] ::prof/user-note) 8)]))


(defn make-zeroing-panel []
  (sf/forms-panel
   "pref,4dlu,pref,20dlu,pref,4dlu,pref"
   :items [(sf/separator ::general-section-coordinates) (sf/next-line)
           (sc/label ::general-section-coordinates-zero-x)
           (w/input-num *pa [:zero-x] ::prof/zero-x :columns 4)
           (sc/label ::general-section-coordinates-zero-y)
           (w/input-num *pa [:zero-y] ::prof/zero-y :columns 4)
           (sf/separator ::general-section-direction) (sf/next-line)
           (sc/label ::general-section-direction-distance)
           (w/input-sel-distance *pa [:c-zero-distance-idx])
           (sc/label ::general-section-direction-pitch)
           (w/input-int *pa [:c-zero-w-pitch] ::prof/c-zero-w-pitch :columns 4)
           (sf/separator ::general-section-temperature) (sf/next-line)
           (sc/label ::general-section-temperature-air)
           (w/input-int *pa [:c-zero-air-temperature]
                        ::prof/c-zero-air-temperature :columns 4)
           (sc/label ::general-section-temperature-powder)
           (w/input-int *pa [:c-zero-p-temperature]
                        ::prof/c-zero-p-temperature :columns 4)
           (sf/separator ::general-section-environment) (sf/next-line)
           (sc/label ::general-section-environment-pressure)
           (w/input-int *pa [:c-zero-air-pressure]
                        ::prof/c-zero-air-pressure :columns 4)
           (sc/label ::general-section-environment-humidity)
           (w/input-int *pa [:c-zero-air-humidity]
                        ::prof/c-zero-air-humidity :columns 4)]))


(defn- wrp-tab [tab-cons]
  (sc/border-panel
   :hgap 20
   :vgap 20
   :border (empty-border :thickness 10)
   :center (sc/scrollable (tab-cons))))


(defn make-tabs []
  (sc/tabbed-panel
   :placement :top
   :overflow :scroll
   :tabs [{:title (w/fat-label ::root-tab-general)
           :content (wrp-tab make-general-panel)}
          {:title (w/fat-label ::root-tab-zeroing)
           :content (wrp-tab make-zeroing-panel)}
          {:title (w/fat-label ::root-tab-ballistics)
           :content (make-ballistic-panel *pa)}
          {:title (w/fat-label ::root-tab-distances)
           :content (wrp-tab #(make-dist-panel *pa))}]))


(defn make-profile-bar []
  (sc/border-panel
   :hgap   5
   :border [5 (empty-border :bottom 1 :top 1)]
   :center
   (sc/horizontal-panel :items [(w/profile-selector *pa)
                                (w/act-prof-dupe! *pa)
                                (w/act-prof-del! *pa)])))


(defn make-status-bar []
  (sc/vertical-panel
   :items
   [(sc/separator :orientation :horizontal)
    (w/status)]))


(defn make-frame
  []
  (sc/frame
   :title ::frame-title
   :id :frame-main
   :on-close
   (if (System/getProperty "repl") :dispose :exit)
   :menubar
   (sc/menubar
    :items [(sc/menu :text ::frame-file-menu :items
                     [(w/act-open! *pa)
                      (w/act-save! *pa)
                      (w/act-save-as! *pa)
                      (w/act-reload! *pa)
                      (w/act-import! *pa)
                      (w/act-export! *pa)])
            (sc/menu :text ::frame-themes-menu :items
                     [(w/act-theme! ::action-theme-dark :dark)
                      (w/act-theme! ::action-theme-light :light)
                      (w/act-theme! ::action-theme-sol-dark :sol-dark)
                      (w/act-theme! ::action-theme-sol-light :sol-light)
                      (w/act-theme! ::action-theme-hi-dark :hi-dark)
                      (w/act-theme! ::action-theme-hi-light :hi-light)])
            (sc/menu :text ::frame-language-menu :items
                     [(sc/menu-item :text ::frame-language-english
                                    :id :lang-english)])])
   :content (sc/border-panel
             :border 5
             :hgap 5
             :vgap 5
             :north  (make-profile-bar)
             :center (make-tabs)
             :south  (make-status-bar))))


(defn- pack-with-gap! [frame]
  (let [size (sc/config (sc/pack! frame) :size)
        height (. ^java.awt.Dimension size height)
        width (. ^java.awt.Dimension size width)]
    (sc/config! frame :size [(+ 15 width) :by (+ 20 height)])))


(defn -main [& args]
  (conf/set-ui-font conf/font-big)
  (. Locale setDefault (new Locale "ua" "UA"))
  (sc/invoke-later
   (sc/native!)
   (conf/load-config! (fio/get-config-file-path))
   (conf/set-theme! (conf/get-color-theme))
   (when-let [fp (first args)]
     (fio/load! *pa fp))
   (-> (make-frame) pack-with-gap! sc/show!)))


(when (System/getProperty "repl") (-main nil))
