(ns tvt.a7.profedit.actions
  (:require [tvt.a7.profedit.config :as conf]
            [tvt.a7.profedit.fio :as fio]
            [seesaw.core :as ssc]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.widgets :as w]
            [tvt.a7.profedit.wizard :refer [start-wizard]]
            [j18n.core :as j18n]))


(defn- wrap-act-lbl [text]
  (str (if (string? text) text (j18n/resource text)) "    "))

(defn act-language-en! [frame-cons]
  (ssc/action :name (wrap-act-lbl ::frame-language-english)
              :icon (conf/loc-key->icon :english)
              :handler (fn [e]
                         (conf/set-locale! :english)
                         (w/reload-frame! (ssc/to-root e) frame-cons)
                         (prof/status-ok! ::status-language-selected))))


(defn act-language-ua! [frame-cons]
  (ssc/action :name (wrap-act-lbl ::frame-language-ukrainian)
              :icon (conf/loc-key->icon :ukrainian)
              :handler (fn [e]
                         (conf/set-locale! :ukrainian)
                         (w/reload-frame! (ssc/to-root e) frame-cons)
                         (prof/status-ok! ::status-language-selected))))


(defn act-theme! [frame-cons name theme-key]
  (ssc/action :name (wrap-act-lbl name)
              :icon (conf/key->icon (keyword (str "action-"
                                                  (name theme-key)
                                                  "-theme-icon")))
              :handler (fn [e]
                         (when (conf/reset-theme! theme-key e)
                           (w/reload-frame! (ssc/to-root e) frame-cons)
                           (prof/status-ok! ::status-theme-selected)))))


(defn act-save! [*state]
  (ssc/action
   :icon (conf/key->icon :file-save)
   :name (wrap-act-lbl ::save)
   :handler (fn [e]
              (if-let [fp (fio/get-cur-fp)]
                (when (fio/save! *state fp)
                  (prof/status-ok! ::saved))
                (w/save-as-chooser *state))
              (w/set-tree-selection (ssc/select (ssc/to-root e) [:#tree])
                                  (fio/get-cur-fp)))))


(defn act-save-as! [*state]
  (ssc/action
   :icon (conf/key->icon :file-save-as)
   :name (wrap-act-lbl ::save-as)
   :handler (fn [e] (w/save-as-chooser *state)
              (w/set-tree-selection (ssc/select (ssc/to-root e) [:#tree])
                                  (fio/get-cur-fp)))))


(defn act-reload! [frame-cons *state]
  (ssc/action
   :icon (conf/key->icon :file-reload)
   :name (wrap-act-lbl ::reload)
   :handler (fn [e]
              (when-not (w/notify-if-state-dirty! *state (ssc/to-root e))
               (if-let [fp (fio/get-cur-fp)]
                 (when (fio/load! *state fp)
                   (w/reload-frame! (ssc/to-root e) frame-cons)
                   (prof/status-ok! (format (j18n/resource ::reloaded)
                                            (str fp))))
                 (w/load-from-chooser *state))))))


(defn act-open! [frame-cons *state]
  (ssc/action
   :icon (conf/key->icon :file-open)
   :name (wrap-act-lbl ::open)
   :handler (fn [e]
              (when-not (w/notify-if-state-dirty! *state (ssc/to-root e))
                  (w/load-from-chooser *state)
                  (w/reload-frame! (ssc/to-root e) frame-cons)))))


(defn act-new! [frame-cons *state]
  (ssc/action
   :icon (conf/key->icon :file-new)
   :name (wrap-act-lbl ::file-new)
   :handler (fn [e]
              (let [frame (ssc/to-root e)]
                (when-not (w/notify-if-state-dirty! *state frame)
                  (w/dispose-frame! frame)
                  (start-wizard frame-cons *state))))))


(defn act-import! [frame-cons *state]
  (ssc/action
   :icon (conf/key->icon :file-import)
   :name (wrap-act-lbl ::import)
   :handler (fn [e]
              (when-not (w/notify-if-state-dirty! *state (ssc/to-root e))
                (w/import-from-chooser *state)
                (w/reload-frame! (ssc/to-root e) frame-cons)))))


(defn act-export! [*state]
  (ssc/action
   :icon (conf/key->icon :file-export)
   :name (wrap-act-lbl ::export)
   :handler (fn [_] (w/export-to-chooser *state))))
