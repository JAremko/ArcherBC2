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
   [tvt.a7.profedit.log :as log]
   [tvt.a7.profedit.wizard-state :as wizard-state]
   [seesaw.border :refer [line-border empty-border]]
   [seesaw.core :as sc]
   [seesaw.bind :as sb]
   [tvt.a7.profedit.util :as u]
   [j18n.core :as j18n]
   [seesaw.forms :as sf]
   [tvt.a7.profedit.rosetta :as ros])
  (:import [javax.swing JFrame JButton Action]))


(defn make-status-bar []
  (sc/vertical-panel
   :items
   [(sc/separator :orientation :horizontal)
    (w/status)]))


(declare make-dnd-frame)


(defn- wizard-summary [*w-state content]
  (let [state @*w-state]
    (assoc (wizard-state/state-summary state)
           :step-index (get-in state [:wizard :content-idx])
           :step-id (:id content)
           :status-ok (prof/status-ok?))))

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


(defn- make-menu-file
  "Takes the already-built Action seq — the SAME Action objects back both the
   menu items and the toolbar buttons (Q2: building them twice doubled the
   global accelerator registrations)."
  [action-items]
  (sc/menu :icon
           (conf/key->icon :actions-group-menu)
           :items action-items))


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


(defn- save-new-profile
  "Wizard save-as attempt. Clears the status first so a save error is
   distinguishable from a plain cancel. Returns :saved, :error or :cancelled."
  [frame *w-state]
  (log/info! "wizard-save-dialog-opened" (wizard-summary *w-state nil))
  (prof/status-ok! "")
  (let [outcome (cond
                  (w/save-as-chooser *w-state frame) :saved
                  (prof/status-err?)                 :error
                  :else                              :cancelled)]
    (log/info! (if (= outcome :saved)
                 "wizard-profile-saved"
                 "wizard-save-not-completed")
               (assoc (wizard-summary *w-state nil)
                      :outcome outcome
                      :status-text (prof/status-text)))
    outcome))


(defn- wizard-finalizer
  "Terminal wizard step (C7 rewrite): never System/exit.  The wizard frame is
   disposed HERE, after the save decision, so the chooser always has a live
   parent.  :saved -> commit + open the editor.  :error -> re-prompt (a later
   cancel exits through :cancelled, so the loop is escapable).  :cancelled ->
   open the editor with the wizard-built, UNSAVED profile."
  [*state frame *w-state main-frame-cons]
  (let [outcome (save-new-profile frame *w-state)]
    (log/info! "wizard-finalizer-finished"
               (assoc (wizard-summary *w-state nil) :outcome outcome))
    (if (= outcome :error)
      (recur *state frame *w-state main-frame-cons)
      (do (reset! *state (select-keys (deref *w-state) [:profile]))
          (when (= outcome :cancelled)
            (reset! fio/*current-fp nil)
            (prof/status-ok! (j18n/resource ::wizard-profile-not-saved)))
          (u/dispose-frame! frame)
          (-> (main-frame-cons) sc/pack! sc/show!)
          nil))))


;; --- wizard navigation state (pure helpers over the per-run *w-state) ------

(defn- wiz-content-idx [*w-state]
  (get-in (deref *w-state) [:wizard :content-idx]))


(defn- wiz-page
  "Current page map {:cons .. :finalizer ..}, or nil when past the last page."
  [*w-state content-vec]
  (let [i (wiz-content-idx *w-state)]
    (when (and (>= i 0) (< i (count content-vec)))
      (nth content-vec i))))


(defn- wiz-wrap-page [*w-state content-vec]
  (sc/border-panel
   :id :content
   :center ((:cons (wiz-page *w-state content-vec)))))


(defn- wiz-reset-content! [frame *w-state content-vec]
  (let [c-c (sc/select frame [:#content-container])
        cur-cont (sc/select c-c [:#content])]
    (sc/replace! c-c cur-cont (wiz-wrap-page *w-state content-vec))))


;; --- wizard button handlers -------------------------------------------------

(defn- wiz-back-handler [*w-state content-vec]
  (fn [e]
    (let [frame (sc/to-root e)
          can-go-back? #(pos? (wiz-content-idx *w-state))]
      (when (can-go-back?)
        (log/info! "wizard-back-clicked"
                   (wizard-summary *w-state
                                   (wiz-page *w-state content-vec)))
        (swap! *w-state update-in [:wizard :content-idx] dec)
        (sc/invoke-later (wiz-reset-content! frame *w-state content-vec))
        (sc/config! e :enabled? (can-go-back?))))))


(defn- wiz-advance!
  "Move past a successfully finalized page: next page, or — after the last
   page — hand off to wizard-finalizer (which owns save/dispose policy, C7).
   The repeating-speeds check gates the handoff exactly as before."
  [frame e *state *w-state main-frame-cons content-vec prev-b]
  (sc/invoke-later
   (swap! *w-state update-in [:wizard :content-idx] inc)
   (if (wiz-page *w-state content-vec)
     (do
       (log/info! "wizard-step-opened"
                  (wizard-summary *w-state
                                  (wiz-page *w-state content-vec)))
       (wiz-reset-content! frame *w-state content-vec)
       (sc/config! prev-b :enabled? true))
     (if-not (ros/repeating-speeds? (:profile (deref *w-state)))
       ;; frame is NOT disposed here — the finalizer disposes it after the
       ;; save decision so the chooser has a live parent (C7).
       (sc/invoke-later
        (wizard-finalizer *state frame *w-state main-frame-cons))
       (do
         (swap! *w-state update-in [:wizard :content-idx] dec)
         (wiz-reset-content! frame *w-state content-vec)
         (prof/status-err! ::ros/profile-bc-repeating-speeds)))))
  e)


(defn- wiz-next-handler [*state *w-state main-frame-cons content-vec prev-b]
  (fn [e]
    (let [frame (sc/to-root e)
          content (wiz-page *w-state content-vec)
          finalize! (:finalizer content)
          _ (log/info! "wizard-next-clicked"
                       (wizard-summary *w-state content))
          inputs-valid? (w/commit-inputs! frame)
          finalized? (and inputs-valid? (finalize! e))]
      (log/info! "wizard-step-validation-finished"
                 (assoc (wizard-summary *w-state content)
                        :valid finalized?
                        :inputs-valid inputs-valid?
                        :status-text (prof/status-text)))
      (if finalized?
        (if (select-first-empty-input frame)
          (when (prof/status-ok?)
            (prof/status-err! ::fill-the-input))
          (wiz-advance! frame e *state *w-state main-frame-cons
                        content-vec prev-b))
        (wiz-reset-content! frame *w-state content-vec)))))


;; --- wizard frame assembly ----------------------------------------------------

(defn make-frame-wizard [*state *w-state main-frame-cons content-vec]
  (let [frame-cons (partial make-frame-wizard
                            *state *w-state main-frame-cons content-vec)
        prev-b (sc/button
                :text ::prev-frame
                :id :back-button
                :enabled? (pos? (wiz-content-idx *w-state))
                :listen [:action (wiz-back-handler *w-state content-vec)])
        next-b (sc/button
                :text ::next-frame
                :listen [:action (wiz-next-handler *state *w-state
                                                   main-frame-cons
                                                   content-vec prev-b)])
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
                                  :center (wiz-wrap-page *w-state content-vec)
                                  :south (sc/horizontal-panel
                                          :items [prev-b next-b]))
                                 (make-status-bar)]))]
    (prof/status-ok! "")
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (if (get-in (deref *w-state) [:wizard :maximized?])
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
        actions (mk-menu-file-items *state frame wizard-cons)
        ;; Icon-only VIEWS over the shared Actions — mutating :name to \"\"
        ;; (the old approach) would blank the menu labels too, since NAME
        ;; lives on the shared Action object.
        buttons (mapv (fn [a]
                        (doto (JButton. ^Action a)
                          (.setHideActionText true)))
                      actions)
        menubar (sc/menubar
                 :items (into
                         buttons
                         [(sc/separator :orientation :vertical)
                          (make-menu-file actions)
                          (make-menu-themes frame-cons)
                          (make-menu-languages frame-cons)]))]
    (doto frame
      (sc/config! :menubar menubar)
      (sc/config! :content (content-cons)))
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    ;; Per-frame gensym key: the old frame's WINDOW_CLOSED arrives AFTER the
    ;; replacement frame registers, so a fixed key would let the old teardown
    ;; delete the new frame's watcher.  Kept as a raw add-watch on purpose:
    ;; Ctrl+R reload resets *current-fp to the SAME value and depends on the
    ;; fire; a seesaw bind would skip o=n changes.
    (let [fp-key (keyword (gensym "file-fp-watch"))]
      (fio/add-current-fp-watcher fp-key
                                  (fn [_] (ball/regen-func-coefs! *state frame)))
      (sc/listen frame :window-closed
                 (fn [_] (fio/remove-current-fp-watcher fp-key))))
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
  (when-not (w/user-aborted-discard? *state frame)
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
