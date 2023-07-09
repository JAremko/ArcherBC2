(ns tvt.a7.profedit.frames
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.actions :as a]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc])
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
    (sc/config! frame :size [(+ 20 width) :by (+ 40 height)])))


(defn make-menu-file [*state make-frame make-wizard-frame]
  (sc/menu
   :icon (conf/key->icon :actions-group-menu)
   :items
   [(a/act-new! make-wizard-frame *state)
    (a/act-open! make-frame *state)
    (a/act-save! *state)
    (a/act-save-as! *state)
    (a/act-reload! make-frame *state)
    (a/act-import! make-frame *state)
    (a/act-export! *state)]))


(defn make-menu-themes [make-frame]
  (let [at! (fn [name key] (a/act-theme! make-frame name key))]
    (sc/menu
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
   :icon (conf/key->icon :icon-languages)
   :items
   [(a/act-language-en! make-frame)
    (a/act-language-ua! make-frame)]))


(defn- select-first-empty-input [frame]
  (when-let [first-empty (first (filter #(-> %
                                             sc/text
                                             seq
                                             nil?)
                                        (sc/select
                                         frame
                                         [:.nonempty-input])))]
    (sc/request-focus! first-empty)))


(defn make-frame-wizard [*state content next-frame-cons]
  (let [frame-cons (partial make-frame-wizard *state content next-frame-cons)
        next-button (sc/button :text ::next-frame
                               :id :next-button
                               :listen
                               [:action
                                (fn [e]
                                  (let [frame (sc/to-root e)]
                                    (if (select-first-empty-input frame)
                                      (prof/status-err! ::fill-the-input)
                                      (do
                                        (sc/dispose! frame)
                                        (next-frame-cons)))))])
        frame (sc/frame
               :icon (conf/key->icon :icon-frame)
               :id :frame-main
               :on-close (if (System/getProperty "repl") :dispose :exit)
               :menubar
               (sc/menubar
                :items [(make-menu-themes frame-cons)
                        (make-menu-languages frame-cons)])
               :content (sc/border-panel
                         :vgap 30
                         :north next-button
                         :center content
                         :south (make-status-bar)))]
    (prof/status-ok! "")
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (-> frame pack-with-gap! sc/show!)))


(defn make-frame-main [*state wizard-cons content-cons]
  (let [frame-cons (partial make-frame-main *state wizard-cons content-cons)
        frame (->> (content-cons)
                   (sc/frame
                    :icon (conf/key->icon :icon-frame)
                    :id :frame-main
                    :on-close (if (System/getProperty "repl") :dispose :exit)
                    :menubar
                    (sc/menubar
                     :items [(make-menu-file *state frame-cons wizard-cons)
                             (make-menu-themes frame-cons)
                             (make-menu-languages frame-cons)])
                    :content))]
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (sc/pack! frame)))
