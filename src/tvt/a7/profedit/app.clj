(ns tvt.a7.profedit.app
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.frames :as f]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [tvt.a7.profedit.wizard :refer [start-wizard!]]
   [tvt.a7.profedit.update :refer [check-for-update]]
   [seesaw.core :as sc]
   [seesaw.forms :as sf]
   [j18n.core :as j18n])
  (:gen-class))


(def *pa (atom prof/example))

(defn make-general-panel []
  (w/forms-with-bg
   :description-panel
   "pref,4dlu,pref,40dlu,pref,4dlu,pref,100dlu,pref"
   :items [(sc/label :text ::general-section-profile :class :fat) (sf/next-line)
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


(defn make-tabs [frame-cons]
  (sc/tabbed-panel
   :placement :left
   :overflow :scroll
   :tabs
   [{:tip (j18n/resource ::root-tab-general)
     :icon (conf/key->icon :tab-icon-description)
     :content (wrp-tab make-general-panel)}

    {:tip (j18n/resource ::rifle-tab-title)
     :icon (conf/key->icon :tab-icon-rifle)
     :content
     (sc/scrollable
      (w/forms-with-bg
       :rifle-tab-panel
       "pref,4dlu,pref"
       :items [(sc/label :text ::rifle-title :class :fat) (sf/next-line)
               (sc/label ::rifle-twist-rate)
               (w/input-num *pa
                            [:r-twist]
                            ::prof/r-twist :columns 4)
               (sc/label ::rifle-twist-direction)
               (w/input-sel *pa
                            [:twist-dir]
                            {:right (j18n/resource ::rifle-twist-right)
                             :left (j18n/resource ::rifle-twist-left)}
                            ::prof/twist-dir)
               (sc/label ::rifle-scope-offset)
               (w/input-num *pa
                            [:sc-height]
                            ::prof/sc-height
                            :columns 4)]))}

    {:tip (j18n/resource ::rifle-cartridge-title)
     :icon (conf/key->icon :tab-icon-cartridge)
     :content
     (sc/scrollable
      (w/forms-with-bg
       :cartridge-tab-panel
       "pref,4dlu,pref"
       :items [(sc/label :text ::rifle-cartridge-title :class :fat)
               (sf/next-line)
               (sc/label ::rifle-muzzle-velocity)
               (w/input-num *pa
                            [:c-muzzle-velocity]
                            ::prof/c-muzzle-velocity)
               (sc/label ::rifle-powder-temperature)
               (w/input-num *pa
                            [:c-zero-temperature]
                            ::prof/c-zero-temperature)
               (sc/label ::rifle-ratio)
               (w/input-num *pa
                            [:c-t-coeff]
                            ::prof/c-t-coeff)]))}

    {:tip (j18n/resource ::bullet-tab-title)
     :icon (conf/key->icon :tab-icon-bullet)
     :content
     (sc/border-panel
      :vgap 20
      :north
      (w/forms-with-bg
       :bullet-tab-panel
       "pref,4dlu,pref"
       :items [(sc/label :text ::bullet-bullet :class :fat) (sf/next-line)
               (sc/label ::bullet-diameter)
               (w/input-num *pa [:b-diameter] ::prof/b-diameter
                            :columns 4)
               (sc/label ::bullet-weight)
               (w/input-num *pa [:b-weight] ::prof/b-weight
                            :columns 4)
               (sc/label ::bullet-length)
               (w/input-num *pa [:b-length] ::prof/b-length
                            :columns 4)
               (sc/label :text ::function-tab-title)
               (ball/make-bc-type-sel *pa)
               (sc/label :text ::function-tab-row-count)
               (w/input-coef-count *pa ball/regen-func-coefs)])
      :center (ball/make-func-panel *pa))}

    {:tip (j18n/resource ::root-tab-zeroing)
     :icon (conf/key->icon :tab-icon-zeroing)
     :content (ball/make-zeroing-panel *pa)}

    {:tip (j18n/resource ::root-tab-distances)
     :icon (conf/key->icon :tab-icon-distances)
     :content (wrp-tab #(make-dist-panel *pa))}

    {:tip (j18n/resource ::root-tab-file-tree)
     :icon (conf/key->icon :tab-icon-file-tree)
     :content (wrp-tab #(w/make-file-tree *pa frame-cons))}]))


(defn make-frame []
  (->> (sc/border-panel
        :border 5
        :hgap 5
        :vgap 5
        :center (make-tabs make-frame)
        :south  (f/make-status-bar))
       (sc/border-panel :north
                        (sc/horizontal-panel
                         :items [(sc/label
                                  :icon (conf/banner-source "banner_a.gif"))
                                 (sc/label
                                  :icon (conf/banner-source "banner_b.gif"))])
                        :paint (w/skin :banner-bg)
                        :center)
       #()
       (f/make-frame-main *pa (partial start-wizard!
                                       make-frame
                                       f/make-frame-wizard
                                       *pa))))


(defn -main [& args]
  (check-for-update)
  (conf/load-config! (fio/get-config-file-path))
  (conf/set-locale! (conf/get-locale))
  (sc/invoke-later
   (conf/set-ui-font! conf/font-big)
   (conf/set-theme! (conf/get-color-theme))
   (when-let [fp (first args)]
     (fio/load! *pa fp))
   (let [frame (make-frame)]
     (sc/show! frame))))


(when (System/getProperty "repl") (-main nil))
