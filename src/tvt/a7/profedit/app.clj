(ns tvt.a7.profedit.app
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :refer [make-ballistic-panel]]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [seesaw.forms :as sf]
   [seesaw.border :refer [empty-border]]
   [j18n.core :as j18n])
  (:gen-class))


(def *pa (atom prof/example))


(defn make-general-panel []
  (w/forms-with-bg
   :description-panel
   "pref,4dlu,pref,40dlu,pref,4dlu,pref,100dlu,pref"
   :items [(sc/label :text ::general-section-profile) (sf/next-line)
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


(defn- wrp-tab [tab-cons]
  (sc/scrollable (tab-cons)))


(defn make-tabs []
  (sc/tabbed-panel
   :placement :top
   :overflow :scroll
   :tabs [{:tip (j18n/resource ::root-tab-general)
           :icon (conf/key->icon :tab-icon-description)
           :content (wrp-tab make-general-panel)}
          {:tip (j18n/resource ::root-tab-ballistics)
           :icon (conf/key->icon :tab-icon-ballistics)
           :content (make-ballistic-panel *pa)}
          {:tip (j18n/resource ::root-tab-distances)
           :icon (conf/key->icon :tab-icon-distances)
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


(defn- pack-with-gap! [frame]
  (let [size (sc/config (sc/pack! frame) :size)
        height (. ^java.awt.Dimension size height)
        width (. ^java.awt.Dimension size width)]
    (sc/config! frame :size [(+ 5 width) :by (+ 20 height)])))


(defn make-frame
  []
  (let [at! (fn [name key] (w/act-theme! make-frame name key))]
    (->> (sc/border-panel
          :border 5
          :hgap 5
          :vgap 5
          :north  (make-profile-bar)
          :center (make-tabs)
          :south  (make-status-bar))

         (sc/frame
          :icon (conf/key->icon :icon-frame)
          :id :frame-main
          :on-close
          (if (System/getProperty "repl") :dispose :exit)
          :menubar
          (sc/menubar
           :items [(sc/menu
                    :text ::frame-file-menu
                    :icon (conf/key->icon :actions-group-menu)
                    :items
                    [(w/act-open! *pa)
                     (w/act-save! *pa)
                     (w/act-save-as! *pa)
                     (w/act-reload! *pa)
                     (w/act-import! *pa)
                     (w/act-export! *pa)])
                   (sc/menu
                    :text ::frame-themes-menu
                    :icon (conf/key->icon :actions-group-theme)
                    :items
                    [(at! ::action-theme-dark :dark)
                     (at! ::action-theme-light :light)
                     (at! ::action-theme-sol-dark :sol-dark)
                     (at! ::action-theme-sol-light :sol-light)
                     (at! ::action-theme-hi-dark :hi-dark)
                     (at! ::action-theme-hi-light :hi-light)])
                   (sc/menu
                    :text ::frame-language-menu
                    :icon (conf/key->icon :icon-languages)
                    :items
                    [(w/act-language-en! make-frame)
                     (w/act-language-ua! make-frame)])])
          :content)

         (pack-with-gap!))))


(defn -main [& args]
  (sc/invoke-later
   (conf/set-ui-font! conf/font-big)
   (conf/load-config! (fio/get-config-file-path))
   (conf/set-locale! (conf/get-locale))
   (conf/set-theme! (conf/get-color-theme))
   (when-let [fp (first args)]
     (fio/load! *pa fp))
   (sc/show! (make-frame))))


(when (System/getProperty "repl") (-main nil))
