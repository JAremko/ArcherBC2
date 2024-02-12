(ns tvt.a7.profedit.frames
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.update :refer [get-current-version]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.actions :as a]
   [tvt.a7.profedit.ballistic :as ball]
   [seesaw.dnd :as dnd]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [seesaw.border :refer [line-border empty-border]]
   [seesaw.core :as sc]
   [seesaw.bind :as sb]
   [tvt.a7.profedit.util :as u]
   [j18n.core :as j18n]
   [seesaw.forms :as sf]
   [tvt.a7.profedit.rosetta :as ros])
  (:import [javax.swing JFrame]))


(defn make-status-bar []
  (sc/vertical-panel
   :items
   [(sc/separator :orientation :horizontal)
    (w/status)]))


(declare make-dnd-frame)

(defn- mk-menu-file-items [*state frame make-wizard-frame]
  [(a/act-new! make-wizard-frame *state frame)
   (a/act-open! *state frame)
   (a/act-save! *state frame)
   (a/act-save-as! *state frame)
   (a/act-reload! *state frame)
   (a/act-load-zero-xy! *state frame)
   (a/act-import! *state frame)
   (a/act-export! *state frame)
   (a/act-dnd! *state frame make-dnd-frame)])


(defn- make-menu-file [*state frame make-wizard-frame]
  (sc/menu :icon
           (conf/key->icon :actions-group-menu)
           :items
           (mk-menu-file-items *state frame make-wizard-frame)))


(defn- make-menu-themes [make-frame]
  (let [at! (fn [t-name t-key] (a/act-theme! make-frame t-name t-key))]
    (sc/menu
     :icon (conf/key->icon :actions-group-theme)
     :items
     [(at! ::action-theme-sol-light :sol-light)
      (at! ::action-theme-sol-dark :sol-dark)
      (at! ::action-theme-real-dark :dark)
      (at! ::action-theme-hi-dark :hi-dark)])))


(defn- make-menu-languages [make-frame]
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


(defn- save-new-profile [*state frame *w-state main-frame-cons]
  (if (w/save-as-chooser *w-state frame)
    (do (reset! *state (select-keys (deref *w-state) [:profile]))
        (-> (main-frame-cons) sc/pack! sc/show!))
    (reset! fio/*current-fp nil)))


(defn- wizard-finalizer [*state frame *w-state main-frame-cons]
  (let [saved? (save-new-profile *state frame *w-state main-frame-cons)]
    (cond (prof/status-err?) (recur *state frame *w-state main-frame-cons)
          (not saved?) (System/exit 0)
          :else nil)))


;; FIXME: The ugliest function I ever wrote X_X
(defn make-frame-wizard [*state *w-state main-frame-cons content-vec]
  (let [c-sel [:wizard :content-idx]
        get-cur-content-idx #(get-in (deref *w-state) c-sel)

        inc-cur-content-idx! (partial swap! *w-state #(update-in % c-sel inc))

        dec-cur-content-idx! (partial swap! *w-state #(update-in % c-sel dec))

        get-cur-content-map #(let [cur-idx (get-cur-content-idx)]
                               (when (and (>= cur-idx 0)
                                          (< cur-idx (count content-vec)))
                                 (nth content-vec cur-idx)))

        wrap-cur-content #(sc/border-panel
                           :id :content
                           :center ((:cons (get-cur-content-map))))

        reset-content! #(let [c-c (sc/select % [:#content-container])
                              cur-cont (sc/select c-c [:#content])]
                          (sc/replace! c-c cur-cont
                                       (wrap-cur-content)))
        finalize-cur-content! #((:finalizer (get-cur-content-map)) %)

        frame-cons (partial make-frame-wizard
                            *state
                            *w-state
                            main-frame-cons
                            content-vec)
        wiz-fs-sel [:wizard :maximized?]

        can-go-back? #(> (get-cur-content-idx) 0)

        prev-b (sc/button
                :text ::prev-frame
                :id :back-button
                :enabled? (can-go-back?)
                :listen
                [:action
                 (fn [e]
                   (let [frame (sc/to-root e)]
                     (when (can-go-back?)
                       (dec-cur-content-idx!)
                       (sc/invoke-later (reset-content! frame))
                       (sc/config! e :enabled? (can-go-back?)))))])
        next-b (sc/button
                :text ::next-frame
                :listen
                [:action
                 (fn [e]
                   (let [frame (sc/to-root e)]
                     (if (finalize-cur-content! e)
                       (if (select-first-empty-input frame)
                         (when (prof/status-ok?)
                           (prof/status-err! ::fill-the-input))
                         (sc/invoke-later
                          (inc-cur-content-idx!)
                          (if (get-cur-content-map)
                            (do
                              (reset-content! frame)
                              (sc/config! prev-b :enabled? true))
                            (if-not (ros/repeating-speeds?
                                     (:profile (deref *w-state)))
                              (sc/invoke-later
                               (sc/dispose! frame)
                               (wizard-finalizer *state
                                                 frame
                                                 *w-state
                                                 main-frame-cons))
                              (do
                                (dec-cur-content-idx!)
                                (reset-content! frame)
                                (prof/status-err!
                                 ::ros/profile-bc-repeating-speeds))))))
                       (reset-content! frame))))])

        frame (sc/frame
               :icon (conf/key->icon :icon-frame)
               :id :frame-main
               :on-close (if (System/getProperty "repl") :dispose :exit)
               :menubar
               (sc/menubar
                :items [(make-menu-themes frame-cons)
                        (make-menu-languages frame-cons)])
               :content (sc/vertical-panel
                         :items [(w/make-banner)
                                 (sc/border-panel
                                  :id :content-container
                                  :size [900 :by 500]
                                  :vgap 30
                                  :border 5
                                  :center (wrap-cur-content)
                                  :south (sc/horizontal-panel
                                          :items [prev-b next-b]))
                                 (make-status-bar)]))]
    (prof/status-ok! "")
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (if (get-in (deref *w-state) wiz-fs-sel)
      (u/maximize! frame)
      (sc/show! (sc/pack! frame)))
    frame))


(defn make-frame-main [*state wizard-cons content-cons]
  (let [frame (sc/frame
               :title (format "ArcherBC2 %s" (get-current-version))
               :icon (conf/key->icon :icon-frame)
               :id :frame-main
               :on-close (if (System/getProperty "repl") :dispose :exit))
        frame-cons (partial make-frame-main *state wizard-cons content-cons)
        buttons (mapv #(sc/config! % :name "")
                      (mk-menu-file-items *state frame wizard-cons))
        menubar (sc/menubar
                 :items (into
                         buttons
                         [(sc/separator :orientation :vertical)
                          (make-menu-file *state frame wizard-cons)
                          (make-menu-themes frame-cons)
                          (make-menu-languages frame-cons)]))]
    (doto frame
      (sc/config! :menubar menubar)
      (sc/config! :content (content-cons)))
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (fio/add-current-fp-watcher :file-fp-watch
                                (fn [_] (ball/regen-func-coefs! *state frame)))
    (sc/pack! frame)))


(defn make-a7p-drop-handler [dnd-h]
  (dnd/default-transfer-handler
   :import [dnd/file-list-flavor
            (fn [{:keys [data drop? _]}]
              (let [files (if (instance? java.util.List data) data nil)
                   ^java.io.File single-file (first files)]
                (when (and drop? single-file (fio/a7p-file? single-file))
                  (dnd-h (.getAbsolutePath single-file)))))]

   :export {:actions (constantly :none)}))


(defn make-start-frame [dnd-handle new-handle open-handle]
  (let [frame (sc/frame :title (j18n/resource ::start-menu-title)
                        :icon (conf/key->icon :icon-frame)
                        :on-close (if (System/getProperty "repl")
                                    :dispose :exit))

        dnd-wh #(sc/invoke-later (sc/dispose! frame)
                                 (dnd-handle %))

        new-btn (sc/button :text (j18n/resource ::new)
                           :listen [:action (fn  [_]
                                              (sc/invoke-later
                                               (sc/dispose! frame)
                                               (new-handle)))])

        open-btn (sc/button :text (j18n/resource ::open)
                            :listen [:action (fn [_]
                                               (sc/invoke-later
                                                (sc/dispose! frame)
                                                (open-handle)))])

        content-panel (sf/forms-panel
                       "pref"
                       :items [(sc/label (j18n/resource ::start-menu-text))
                               new-btn
                               open-btn
                               (sc/label
                                :transfer-handler (make-a7p-drop-handler dnd-wh)
                                :border (line-border
                                         :color (w/foreground-color)
                                         :thickness 1)
                                :icon (conf/key->icon :action-dnd)
                                :text (str
                                       (j18n/resource ::start-menu-dnd-text)
                                       " "))])]
    (sc/config! frame :transfer-handler (make-a7p-drop-handler dnd-wh))
    (.setAlwaysOnTop ^JFrame frame true)
    (sc/config! frame :content (sc/scrollable content-panel :border 20))
    (-> frame sc/pack! sc/show!)))


(defn- dnd-open! [*state frame *file-path]
  (swap! *state ros/remove-zero-coef-rows)
  (when-not (w/notify-if-state-dirty! *state frame)
    (let [file-path @*file-path]
     (when (fio/load! *state file-path)
       (prof/status-ok! (format (j18n/resource ::loaded-from)
                                (str file-path)))
       (w/reset-tree-selection (sc/select frame [:#tree]))))))


(defn- dnd-load-zero-xy! [*to-state *from-state *file-path]
  (swap! *to-state update :profile
         (fn [profile]
           (merge profile
                  (select-keys (:profile @*from-state)
                               [:zero-x :zero-y]))))
  (prof/status-ok! (format (j18n/resource ::loaded-xy)
                           (str @*file-path))))


(defn- dnd-load-file [*dnd-file-state fp]
  (fio/side-load! *dnd-file-state fp))


(defn make-dnd-frame [*state main-frame]
  (let [state @*state

        cur-fp (fio/get-cur-fp)

        frame (sc/frame :title (j18n/resource ::dnd-menu-title)
                        :icon (conf/key->icon :icon-frame)
                        :on-close :dispose)

        menubar (sc/menubar
                 :items
                 (mapv #(sc/config! % :name "")
                       [(a/act-open! *state frame main-frame)
                        (a/act-save! *state frame main-frame)
                        (a/act-save-as! *state frame main-frame)
                        (a/act-reload! *state frame main-frame)]))

        *selected-file-state (atom state)

        *selected-file-path (atom cur-fp)

        fp-label (sc/label :text cur-fp)

        mk-th #(make-a7p-drop-handler
                (fn [fp]
                  (if (dnd-load-file *selected-file-state fp)
                    (reset! *selected-file-path fp)
                    (do (reset! *selected-file-path nil)
                        (reset! *selected-file-state nil)))
                  (sc/pack! frame)))

        xy-btn (sc/button :text (j18n/resource ::load-xy-from-selected-file)
                          :enabled? true
                          :listen [:action (fn  [_]
                                             (dnd-load-zero-xy!
                                              *state
                                              *selected-file-state
                                              *selected-file-path))])

        open-btn (sc/button :text (j18n/resource ::open-selected-file)
                            :enabled? true
                            :listen [:action (fn [_]
                                               (dnd-open!
                                                *state
                                                main-frame
                                                *selected-file-path))])

        get-x #(str (get-in % [:profile :zero-x] "_"))

        get-y #(str (get-in % [:profile :zero-y] "_"))

        x-label (sc/label :text (get-x state))

        y-label (sc/label :text (get-y state))

        content-panel (sf/forms-panel
                       "pref,pref"
                       :items [(sc/label
                                :transfer-handler (mk-th)
                                :border (line-border
                                         :color (w/foreground-color)
                                         :thickness 1)
                                :text (str (j18n/resource ::dnd-menu-text) " ")
                                :icon (conf/key->icon :action-dnd))
                               (sf/next-line)
                               fp-label
                               (sc/label
                                ::ball/general-section-coordinates-zero-x)
                               x-label
                               (sc/label
                                ::ball/general-section-coordinates-zero-y)
                               y-label
                               xy-btn
                               open-btn])]

    (sb/bind *selected-file-path
             (sb/tee (sb/property fp-label :text)
                     (sb/bind
                      (sb/transform some?)
                      (sb/property xy-btn :enabled?))
                     (sb/bind
                      (sb/transform some?)
                      (sb/property open-btn :enabled?))))

    (sb/bind *selected-file-state
             (sb/tee (sb/bind (sb/transform get-x)
                              (sb/property x-label :text))
                     (sb/bind (sb/transform get-y)
                              (sb/property y-label :text))))

    (sc/config! fp-label :transfer-handler (mk-th))

    (doto frame
      (sc/config! :menubar menubar)
      (sc/config! :transfer-handler (mk-th))
      (sc/config! :content (sc/border-panel :border (empty-border :thickness 20)
                                            :center content-panel))
      (#(.setAlwaysOnTop ^JFrame % true)))

    (-> frame sc/pack! sc/show!)))
