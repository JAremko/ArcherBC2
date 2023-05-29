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
  (:gen-class))


(def *pa (atom prof/example))


(defn make-general-panel []
  (sf/forms-panel
   "pref,4dlu,pref,40dlu,pref,4dlu,pref,100dlu,pref"
   :items [(sf/separator "Profile") (sf/next-line)
           (sc/label "Name:")
           (sf/span (w/input-str *pa [:profile-name] ::prof/profile-name) 7)
           (sc/label "Top:")
           (w/input-str *pa [:short-name-top] ::prof/short-name-top :columns 8)
           (sf/next-line)
           (sc/label "Bottom:")
           (w/input-str *pa [:short-name-bot] ::prof/short-name-bot :columns 8)
           (sf/next-line)
           (sf/separator "Round") (sf/next-line)
           (sc/label "Cartridge:")
           (sf/span (w/input-str *pa [:cartridge-name] ::prof/cartridge-name) 7)
           (sc/label "Bullet:")
           (sf/span (w/input-str *pa [:bullet-name] ::prof/bullet-name) 7)
           (sf/next-line)
           (sf/separator "User note:") (sf/next-line)
           (sf/span (w/input-mul-text *pa [:user-note] ::prof/user-note) 8)]))


(defn make-zeroing-panel []
  (sf/forms-panel
   "pref,4dlu,pref,20dlu,pref,4dlu,pref"
   :items [(sf/separator "Coordinates") (sf/next-line)
           (sc/label "Zero X:")
           (w/input-num *pa [:zero-x] ::prof/zero-x :columns 4)
           (sc/label "Zero Y:")
           (w/input-num *pa [:zero-y] ::prof/zero-y :columns 4)
           (sf/separator "Direction") (sf/next-line)
           (sc/label "Distance:")
           (w/input-sel-distance *pa [:c-zero-distance-idx])
           (sc/label "Pitch:")
           (w/input-int *pa [:c-zero-w-pitch]
                        ::prof/c-zero-w-pitch :columns 4)
           (sf/separator "Temperature") (sf/next-line)
           (sc/label "Air:")
           (w/input-int *pa [:c-zero-air-temperature]
                        ::prof/c-zero-air-temperature :columns 4)
           (sc/label "Powder:")
           (w/input-int *pa [:c-zero-p-temperature]
                        ::prof/c-zero-p-temperature :columns 4)
           (sf/separator "Environment") (sf/next-line)
           (sc/label "Pressure:")
           (w/input-int *pa [:c-zero-air-pressure]
                        ::prof/c-zero-air-pressure :columns 4)
           (sc/label "Humidity:")
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
   :tabs [{:title "General"
           :content (wrp-tab make-general-panel)}
          {:title "Zeroing"
           :content (wrp-tab make-zeroing-panel)}
          {:title "Ballistics"
           :content (make-ballistic-panel *pa)}
          {:title "Distances"
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
   :title "Profile Editor"
   :id :frame-main
   :on-close
   (if (System/getProperty "repl") :dispose :exit)
   :menubar
   (sc/menubar
    :items [(sc/menu :text "File" :items
                     [(w/act-open! *pa)
                      (w/act-save! *pa)
                      (w/act-save-as! *pa)
                      (w/act-reload! *pa)
                      (w/act-import! *pa)
                      (w/act-export! *pa)])
            (sc/menu :text " Themes" :items
                     [(w/act-theme! "Dark" :dark)
                      (w/act-theme! "Light" :light)
                      (w/act-theme! "Solarized dark" :sol-dark)
                      (w/act-theme! "Solarized light" :sol-light)
                      (w/act-theme! "Contrast dark" :hi-dark)
                      (w/act-theme! "Contrast light" :hi-light)])
            (sc/menu :text "Language" :items
                     [(sc/menu-item :text "Engrish    " :id :lang-english)])])
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
  (sc/invoke-later
   (sc/native!)
   (conf/load-config! (fio/get-config-file-path))
   (conf/set-theme! (conf/get-color-theme))
   (when-let [fp (first args)]
     (fio/load! *pa fp))
   (-> (make-frame) pack-with-gap! sc/show!)))


(when (System/getProperty "repl")
     ;; NOTE: fs-api required in the project file.
    (-main nil))
