(ns tvt.a7.profedit.actions
  (:require [tvt.a7.profedit.config :as conf]
            [tvt.a7.profedit.fio :as fio]
            [tvt.a7.profedit.ballistic :refer [regen-func-coefs!]]
            [tvt.a7.profedit.util :as u]
            [seesaw.core :as ssc]
            [seesaw.keymap :as skm]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.widgets :as w]
            [j18n.core :as j18n]
            [tvt.a7.profedit.rosetta :as ros]))


(defn- wrap-act-lbl [text]
  (str (if (string? text) text (j18n/resource text)) "    "))


(defn act-language-en! [frame-cons]
  (ssc/action :name (wrap-act-lbl ::frame-language-english)
              :icon (conf/loc-key->icon :english)
              :handler (fn [e]
                         (conf/set-locale! :english)
                         (u/reload-frame! (ssc/to-root e) frame-cons)
                         (prof/status-ok! ::status-language-selected))))


(defn act-language-ua! [frame-cons]
  (ssc/action :name (wrap-act-lbl ::frame-language-ukrainian)
              :icon (conf/loc-key->icon :ukrainian)
              :handler (fn [e]
                         (conf/set-locale! :ukrainian)
                         (u/reload-frame! (ssc/to-root e) frame-cons)
                         (prof/status-ok! ::status-language-selected))))


(defn act-theme! [frame-cons name theme-key]
  (ssc/action :name (wrap-act-lbl name)
              :icon (conf/key->icon theme-key)
              :handler (fn [e]
                         (when (conf/set-theme! theme-key)
                           (u/reload-frame! (ssc/to-root e) frame-cons)
                           (prof/status-ok! ::status-theme-selected)))))


(defn- fframe!
  " Some inputs only commit changes when they defocused"
  [frame]
  (ssc/request-focus! frame))


(defn act-save! [*state frame]
  (let [handler (fn [e]
                  (let [frame (ssc/to-frame e)]
                    (fframe! frame)
                    (ssc/invoke-later
                     (swap! *state ros/remove-zero-coef-rows)
                     (regen-func-coefs! *state frame)
                     (if-let [fp (fio/get-cur-fp)]
                       (when (fio/save! *state fp)
                         (prof/status-ok! ::saved))
                       (w/save-as-chooser *state))
                     (w/reset-tree-selection (ssc/select frame [:#tree])))))]
    (skm/map-key frame "control S" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :file-save)
     :name (wrap-act-lbl ::save)
     :tip (str (j18n/resource ::save) " Ctrl+s")
     :handler handler)))


(defn act-save-as! [*state frame]
  (let [handler  (fn [e]
                   (let [frame (ssc/to-root e)]
                     (fframe! frame)
                     (ssc/invoke-later
                      (swap! *state ros/remove-zero-coef-rows)
                      (regen-func-coefs! *state frame)
                      (w/save-as-chooser *state)
                      (w/reset-tree-selection (ssc/select frame [:#tree])))))]
    (skm/map-key frame "control shift S" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :file-save-as)
     :name (wrap-act-lbl ::save-as)
     :tip (str (j18n/resource ::save-as) " Ctrl+S")
     :handler handler)))


(defn act-reload! [*state frame]
  (let [handler (fn [e]
                  (let [frame (ssc/to-root e)]
                    (fframe! frame)
                    (ssc/invoke-later
                     (swap! *state ros/remove-zero-coef-rows)
                     (regen-func-coefs! *state frame)
                     (when-not (w/notify-if-state-dirty! *state frame)
                       (if-let [fp (fio/get-cur-fp)]
                         (when (fio/load! *state fp)
                           (prof/status-ok! (format (j18n/resource ::reloaded)
                                                    (str fp))))
                         (w/load-from-chooser *state))))))]
    (skm/map-key frame "control R" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :file-reload)
     :name (wrap-act-lbl ::reload)
     :tip (str (j18n/resource ::reload) " Ctrl+r")
     :handler handler)))


(defn act-open! [*state frame]
  (let [handler (fn [e]
                  (let [frame (ssc/to-root e)]
                    (fframe! frame)
                    (ssc/invoke-later
                     (swap! *state ros/remove-zero-coef-rows)
                     (regen-func-coefs! *state frame)
                     (when-not (w/notify-if-state-dirty! *state frame)
                       (w/load-from-chooser *state)
                       (w/reset-tree-selection (ssc/select frame [:#tree]))))))]
    (skm/map-key frame "control O" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :file-open)
     :name (wrap-act-lbl ::open)
     :tip (str (j18n/resource ::open) " Ctrl+o")
     :handler handler)))


(defn act-load-zero-xy! [*state frame]
  (let [handler (fn [_]
                  (fframe! frame)
                  (ssc/invoke-later
                   (w/set-zero-x-y-from-chooser *state)))]
    (skm/map-key frame "control shift Z" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :load-zero-x-y)
     :name (wrap-act-lbl ::load-zero-x-y)
     :tip (str (j18n/resource ::load-zero-x-y) " Ctrl+Z")
     :handler handler)))


(defn act-new! [wizard-cons *state frame]
  (let [handler (fn [e]
                  (let [frame (ssc/to-root e)]
                    (fframe! frame)
                    (ssc/invoke-later
                     (when-not (w/notify-if-state-dirty! *state frame)
                       (u/dispose-frame! frame)
                       (wizard-cons)))))]
    (skm/map-key frame "control N" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :file-new)
     :name (wrap-act-lbl ::file-new)
     :tip (str (j18n/resource ::file-new) " Ctrl+n")
     :handler handler)))


(defn act-import! [*state frame]
  (let [handler (fn [e]
                  (fframe! frame)
                  (ssc/invoke-later
                   (when-not (w/notify-if-state-dirty! *state (ssc/to-root e))
                     (w/import-from-chooser *state))))]
    (skm/map-key frame "control I" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :file-import)
     :name (wrap-act-lbl ::import)
     :tip (str (j18n/resource ::import) " Ctrl+i")
     :handler handler)))


(defn act-export! [*state frame]
  (let [handler (fn [_]
                  (fframe! frame)
                  (ssc/invoke-later
                   (w/export-to-chooser *state)))]
    (skm/map-key frame "control E" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :file-export)
     :name (wrap-act-lbl ::export)
     :tip (str (j18n/resource ::export) " Ctrl+e")
     :handler handler)))


(defn act-dnd! [*state frame dnd-frame-cons]
  (let [handler (fn [_]
                  (fframe! frame)
                  (ssc/invoke-later (dnd-frame-cons *state frame)))]
    (skm/map-key frame "control D" handler :scope :global)
    (ssc/action
     :icon (conf/key->icon :action-dnd-small)
     :name (wrap-act-lbl ::dnd)
     :tip (str (j18n/resource ::dnd) " Ctrl+d")
     :handler handler)))
