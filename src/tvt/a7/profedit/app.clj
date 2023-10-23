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
   [j18n.core :as j18n]
   [clojure.java.io :as io]
   [tvt.a7.profedit.fulog :as fu])
  (:gen-class))


(def *pa (atom prof/example))


(defn make-general-panel []
  (sf/forms-panel
   "pref,4dlu,pref,40dlu,pref,4dlu,pref,100dlu,pref"
   :items [(sc/label :text ::general-section-profile :class :fat) (sf/next-line)
           (sc/label ::general-section-profile-name)
           (sf/span (w/input-str *pa [:profile-name] ::prof/profile-name) 7)
           (sc/label ::general-section-profile-top)
           (sc/horizontal-panel
            :items [(w/input-str *pa [:short-name-top]
                                 ::prof/short-name-top
                                 :columns 8)
                    (sc/button :text "?"
                               :listen
                               [:action (fn [e]
                                          (sc/alert
                                           (sc/to-root e)
                                           (->> "top.jpeg"
                                                io/resource
                                                sc/icon
                                                vector)
                                           :title ::top-help))])])
           (sf/next-line)
           (sc/label ::general-section-profile-bottom)
           (sc/horizontal-panel
            :items [(w/input-str *pa [:short-name-bot]
                                 ::prof/short-name-bot
                                 :columns 8)
                    (sc/button :text "?"
                               :listen
                               [:action (fn [e]
                                          (sc/alert
                                           (sc/to-root e)
                                           (->> "bot.jpeg"
                                                io/resource
                                                sc/icon
                                                vector)
                                           :title ::bot-help))])])
           (sf/next-line)
           (sf/separator ::general-section-round) (sf/next-line)
           (sc/label ::general-section-round-cartridge)
           (sf/span (w/input-str *pa [:cartridge-name] ::prof/cartridge-name) 7)
           (sc/label ::general-section-round-bullet)
           (sf/span (w/input-str *pa [:bullet-name] ::prof/bullet-name) 7)
           (sf/next-line)
           (sf/separator ::general-section-user-note) (sf/next-line)
           (sf/span (w/input-mul-text *pa [:user-note] ::prof/user-note) 8)]))


(defn make-mini-general-panel []
  (sc/border-panel
   :hgap 5
   :center (w/input-str *pa [:profile-name]
                        ::prof/profile-name
                        :editable? false)
   :east (w/input-str *pa [:cartridge-name]
                      ::prof/cartridge-name
                      :editable? false)))


(defn- wrp-tab [tab-cons]
  (sc/scrollable (tab-cons) :border 10))


(defn make-tabs []
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
     (wrp-tab
      #(sf/forms-panel
        "pref,4dlu,pref"
        :items [(sc/label :text ::rifle-title :class :fat) (sf/next-line)
                (sc/label ::rifle-caliber)
                (w/input-str *pa
                             [:caliber]
                             ::prof/caliber)
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
     (wrp-tab
      #(sf/forms-panel
        "pref,4dlu,pref"
        :items [(sc/label :text ::rifle-cartridge-title :class :fat)
                (sf/next-line)
                (sc/label ::rifle-muzzle-velocity)
                (w/input-num *pa
                             [:c-muzzle-velocity]
                             ::prof/c-muzzle-velocity
                             :columns 4)
                (sc/label ::rifle-powder-temperature)
                (w/input-num *pa
                             [:c-zero-temperature]
                             ::prof/c-zero-temperature
                             :columns 4)
                (sc/label ::rifle-ratio)
                (w/input-num *pa
                             [:c-t-coeff]
                             ::prof/c-t-coeff
                             :columns 4)]))}

    {:tip (j18n/resource ::bullet-tab-title)
     :icon (conf/key->icon :tab-icon-bullet)
     :content
     (sc/border-panel
      :border 10
      :vgap 20
      :north
      (sf/forms-panel
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
               (sc/label :text ::used-function)
               (ball/make-bc-type-sel *pa)
               (sf/separator)
               (sc/label :text ::function-tab-row-count)
               (w/input-coef-count *pa ball/regen-func-coefs!)])
      :center (ball/make-func-panel *pa))}

    {:tip (j18n/resource ::root-tab-zeroing)
     :icon (conf/key->icon :tab-icon-zeroing)
     :content (wrp-tab (partial ball/make-zeroing-panel *pa))}

    {:tip (j18n/resource ::root-tab-distances)
     :icon (conf/key->icon :tab-icon-distances)
     :content (wrp-tab #(make-dist-panel *pa))}

    {:tip (j18n/resource ::root-tab-file-tree)
     :icon (conf/key->icon :tab-icon-file-tree)
     :content (wrp-tab #(sc/border-panel
                         :vgap 10
                         :south (make-mini-general-panel)
                         :center (sc/scrollable (w/file-tree *pa))))}]))


(defn mk-firmware-update-dialogue
  [frame {:keys [device serial version] :as entry}]
  (sc/invoke-later
   (let [new-version (:version (:newest-firmware entry))
         action (sc/input
                 frame
                 (format (j18n/resource ::firmware-update-text)
                         device
                         serial
                         version
                         new-version)
                 :title (j18n/resource ::firmware-update-title)
                 :choices [::update-firmware-now
                           ::undate-firmware-later]
                 :value ::update-firmware-now
                 :type :question
                 :to-string j18n/resource)]
     (when (= action ::update-firmware-now)
       (try
         (fu/push (-> entry
                      (dissoc :profiles)
                      (dissoc :newest-firmware)
                      (assoc :new-version new-version)))
         (fio/copy-newest-firmware entry)
         (sc/alert frame (j18n/resource ::firmware-uploaded) :type :info)
         (catch Exception e (sc/alert frame (.getMessage e) :type :error)))))))


(defn fr-main []
  (->>
   (sc/vertical-panel
    :items [(w/make-banner)
            (sc/border-panel
             :center
             (sc/border-panel
              :border 5
              :hgap 5
              :vgap 5
              :center (make-tabs)
              :south  (f/make-status-bar)))])
   #()
   (f/make-frame-main
    *pa
    (partial start-wizard! fr-main f/make-frame-wizard *pa))))


(defn status-check! []
  (when (prof/status-err?)
    (sc/alert (prof/status-text))
    (System/exit 1)))


(defn show-main-frame! [file-path]
  (sc/invoke-now
   (fio/load-from-fp! *pa file-path)
   (status-check!)
   (sc/show! (fr-main))))


(defn -main [& args]
  (check-for-update)
  (conf/load-config! (fio/get-config-file-path))
  (conf/set-locale! (conf/get-locale))
  (sc/invoke-later
   (conf/set-ui-font! conf/font-big)
   (conf/set-theme! (conf/get-color-theme))
   (if-let [fp (first args)]
     (let [main-frame (show-main-frame! fp)]
       (sc/invoke-later (fio/start-file-tree-updater-thread
                         (partial mk-firmware-update-dialogue main-frame))))
     (let [open-handle #(if (w/load-from-chooser *pa)
                          (do
                            (status-check!)
                            (sc/show! (fr-main)))
                          (System/exit 0))
           new-handle #(start-wizard! fr-main f/make-frame-wizard *pa)
           start-frame
           (f/make-start-frame show-main-frame! new-handle open-handle)]
       (sc/invoke-later (fio/start-file-tree-updater-thread
                         (partial mk-firmware-update-dialogue start-frame)))))))


(when (System/getProperty "repl") (-main nil))
