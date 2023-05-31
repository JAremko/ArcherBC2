(ns tvt.a7.profedit.widgets
  (:require [seesaw.options :as sso]
            [tvt.a7.profedit.config :as conf]
            [tvt.a7.profedit.fio :as fio]
            [seesaw.border :as border]
            [seesaw.cells :as cells]
            [seesaw.chooser :as chooser]
            [seesaw.event :as sse]
            [clojure.spec.alpha :as s]
            [seesaw.core :as ssc]
            [seesaw.bind :as ssb]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.value :as ssv]
            [seesaw.color :refer [default-color]]
            [seesaw.dnd :as dnd]
            [tvt.a7.profedit.widgets :as w]
            [seesaw.core :as sc])
  (:import [javax.swing.text
            DefaultFormatterFactory
            NumberFormatter
            DefaultFormatter])
  (:import [java.awt AWTEvent])
  (:import [java.awt.event KeyEvent])
  (:import [javax.swing JFormattedTextField JComponent])
  (:import [java.text NumberFormat DecimalFormat]))


(ssc/native!)


(def ^:private foreground-color (partial default-color "TextField.foreground"))


(defn- non-empty-string? [value]
  (and value
       (string? value)
       (seq value)
       (re-find #"\S" value)))


(defn parse-input-str [input-str]
  (when (non-empty-string? input-str)
    (let [nf (doto (NumberFormat/getInstance)
               (.setMaximumFractionDigits 10)
               (.setGroupingUsed true))]
      (try
        (.doubleValue (.parse nf input-str))
        (catch Exception _ nil)))))


(defn- report-parse-err! [input-str spec parsed-val]
  (prof/status-err!
   (if parsed-val
     (prof/format-spec-err spec parsed-val)
     (str "Can't parse input value " input-str)))
  nil)


(defn str->long [s]
  (when-let [val (parse-input-str s)] (long val)))


(defn str->double [s]
  (when-let [val (parse-input-str s)] (double val)))


(defn val->str [val]
  (let [format (doto (new DecimalFormat)
                 (.setMaximumFractionDigits 10)
                 (.setGroupingUsed true))]
    (.format format val)))


(defn- truncate-with-ellipsis ^java.lang.String
  [^clojure.lang.Numbers max-len ^java.lang.String s]
  (if (> (count s) max-len)
    (str (subs s 0 (- max-len 3)) "...")
    s))


(defn- fmt-str
  ^java.lang.String [^clojure.lang.Numbers max-len ^java.lang.String s]
  (truncate-with-ellipsis max-len (if (non-empty-string? s) s "<empty>")))


(extend-protocol ssc/ConfigText
  javax.swing.JFormattedTextField
  (set-text* [this v] (.setText this v))
  (get-text* [this] (.getText this)))


(extend-protocol ssv/Value
  javax.swing.JFormattedTextField
  (container?* [_] false)
  (value* [this] (.getValue this))
  (value!* [this v] (.setValue this v)))


(defn- get-event-source [^AWTEvent e] (.getSource e))


(defn- commit-key-pressed? [^KeyEvent e]
  (some? (#{10 27} (. e getKeyCode))))


(defn- sync-and-commit [*state vpath spec e]
  (let [source ^JFormattedTextField (get-event-source e)
        _ (.commitEdit source)
        new-val (.getValue source)]
    (if (s/valid? spec new-val)
      (prof/assoc-in-prof! *state vpath new-val)
      (do (report-parse-err! (ssc/text source) spec new-val)
          (ssc/value! source (prof/get-in-prof *state vpath))))))


(defn- sync-text [*state vpath spec ^AWTEvent e]
  (let [source (ssc/to-widget e)
        new-val (ssc/value source)]
    (if (s/valid? spec new-val)
      (prof/assoc-in-prof! *state vpath new-val)
      (do (report-parse-err! (ssc/text source) spec new-val)
          (ssc/value! source (prof/get-in-prof *state vpath))))))


(defn- mk-number-fmt-default
  [fallback-val]
  (proxy [NumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (str->double s)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (val->str (double (or value (fallback-val))))))))


(defn- mk-int-fmt-default
  [fallback-val]
  (proxy [NumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (str->long s)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (val->str (long (or value (fallback-val))))))))


(defn- mk-str-fmt-default
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn- mk-str-fmt-display
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn- mk-str-fmt-edit
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn- mk-str-fmt-null
  [max-len get-fallback]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len (or s (get-fallback)))))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len (or value (get-fallback)))))))


(defn- wrap-formatter [^DefaultFormatter fmtr]
  (doto fmtr
    (.setCommitsOnValidEdit false)
    (.setOverwriteMode false)))


(defn add-units [input units]
  (if units
    [input (ssc/text :text (str " " units " ")
                     :id :units
                     :editable? false
                     :focusable? false
                     :margin 0)]
    [input]))


(defn- add-tooltip [^JComponent input ^String tooltip]
  (.setToolTipText input tooltip)
  input)


(defn mk-debounced-transform [xf]
  (let [*last-val (atom nil)]
    (fn [state]
      (let [last-val @*last-val
            new-val (xf state)]
        (when (or (nil? last-val)
                  (not= last-val new-val))
          (reset! *last-val new-val)
          new-val)))))


(defn- create-input [formatter *state vpath spec & opts]
  (let [{:keys [min-v max-v units]} (meta (s/get-spec spec))
        wrapped-fmt (wrap-formatter
                     (formatter #(or (prof/get-in-prof* *state vpath)
                                     min-v)))
        fmtr (new DefaultFormatterFactory
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt)
        jf (ssc/construct JFormattedTextField fmtr)
        tooltip-text (str "Number in range from " min-v " to " max-v)]
    (sc/config! jf :id :input)
    (ssb/bind *state
              (ssb/some (mk-debounced-transform #(prof/get-in-prof % vpath)))
              (ssb/value jf))
    (add-tooltip
     (ssc/horizontal-panel
      :items
      (add-units
       (doto jf
         (add-tooltip tooltip-text)
         (sse/listen
          :focus-lost (partial sync-and-commit *state vpath spec)
          :key-pressed #(when (commit-key-pressed? %)
                          (sync-and-commit *state vpath spec %)))
         (sso/apply-options opts))
       units))
     tooltip-text)))


(defn input-int [& args]
  (apply create-input mk-int-fmt-default args))


(defn input-num [& args]
  (apply create-input mk-number-fmt-default args))


(defn input-str
  [*state vpath spec & opts]
  (let [max-length (:max-length (meta (s/get-spec spec)))
        formatter (new DefaultFormatterFactory
                       (wrap-formatter (mk-str-fmt-default max-length))
                       (wrap-formatter (mk-str-fmt-display max-length))
                       (wrap-formatter (mk-str-fmt-edit max-length))
                       (wrap-formatter (mk-str-fmt-null
                                        max-length
                                        #(prof/get-in-prof* *state vpath))))
        jf (ssc/construct JFormattedTextField formatter)]
    (ssb/bind *state
              (ssb/some (mk-debounced-transform #(prof/get-in-prof % vpath)))
              (ssb/value jf))
    (doto jf
      (add-tooltip (str "Non-empty string shorter than "
                        max-length " " "characters"))
      (sse/listen
       :focus-lost (partial sync-and-commit *state vpath spec)
       :key-pressed #(when (commit-key-pressed? %)
                       (sync-and-commit *state vpath spec %)))
      (sso/apply-options opts))))


(defn input-mul-text
  [*state vpath spec & opts]
  (let [w (ssc/text :multi-line? true :rows 3)]
    (ssb/bind *state
              (ssb/transform #(prof/get-in-prof % vpath))
              (ssb/value w))
    (doto w
      (add-tooltip "Single or multi line string")
      (ssc/value! (prof/get-in-prof* *state vpath))
      (sse/listen
       :focus-lost (partial sync-text *state vpath spec))
      (sso/apply-options opts))))


(defn- profile-selector-model [state]
  (map-indexed (fn [idx {:keys [profile-name
                                short-name-top
                                short-name-bot]}]
                 {:id idx
                  :rep {:name profile-name
                        :s-top short-name-top
                        :s-bot short-name-bot}})
               (:profiles state)))


(defn- profile-selector-renderer
  [w {{{:keys [name s-bot s-top]} :rep} :value}]
  (ssc/value! w (str name "(" s-bot "/" s-top ")")))


(defn profile-selector [*state & opts]
  (let [sel #(get-in % [:selected-profile])
        sel! (fn [id]
               (when (not= id (get-in @*state [:selected-profile]))
                 (prof/assoc-in! *state [:selected-profile] id)))
        w (ssc/combobox :model (profile-selector-model @*state)
                        :listen [:selection #(->> %
                                                 (ssc/selection)
                                                 (:id)
                                                 (sel!)
                                                 (ssc/invoke-later))]
                        :selected-index (sel @*state)
                        :renderer (cells/default-list-cell-renderer
                                   profile-selector-renderer))
        dt (mk-debounced-transform profile-selector-model)]
    (ssb/bind *state
              (ssb/tee
               (ssb/bind
                (ssb/some dt)
                (ssb/property w :model))
               (ssb/bind
                (ssb/filter #(not= (ssc/config w :selected-index)
                                   (:selected-profile %)))
                (ssb/transform sel)
                (ssb/property w :selected-index))))
    (doto w
      (add-tooltip "Select profile to work with")
      (sso/apply-options opts))))


(defn status [& opts]
  (let [w (ssc/text :foreground (foreground-color)
                   :multi-line? true
                   :editable? false
                   :wrap-lines? true
                   :text (#(get-in % [:status-text]) @prof/*status))]
    (ssb/bind prof/*status
              (ssb/tee
               (ssb/bind (ssb/transform
                          #(let [is-ok (get-in % [:status-ok])]
                             (if is-ok (foreground-color) :red)))
                         (ssb/property w :foreground))

               (ssb/bind (ssb/transform
                          #(get-in % [:status-text]))
                         (ssb/value w))))
    (doto w
      (add-tooltip "Status bar")
      (sso/apply-options opts))))


(defn- input-sel-renderer [w {{t :typle} :value}]
  (ssc/value! w (str (second t))))


(defn input-sel [*state vpath key->label-map spec & opts]
  (let [sel #(prof/get-in-prof % vpath)
        sel! (partial prof/assoc-in-prof! *state vpath spec)
        model (vec (map-indexed
                    (fn [idx entry] {:id idx :typle entry})
                    key->label-map))
        sel->idx (fn [sel] (some #(when (= sel (first (:typle %))) (:id %))
                                 model))
        w (ssc/combobox :model model
                        :listen [:focus-lost #(->> %
                                                   (ssc/selection)
                                                   (:typle)
                                                   (first)
                                                   (sel!)
                                                   (ssc/invoke-later))]
                        :selected-index (sel->idx (sel @*state))
                        :renderer (cells/default-list-cell-renderer
                                   input-sel-renderer))]
    (ssb/bind *state
              (ssb/some (mk-debounced-transform #(sel->idx (sel %))))
              (ssb/property w :selected-index))
    (sso/apply-options w opts)))


(defn act-theme! [name theme-key]
  (ssc/action :name (str name "  ")
             :handler (fn [_]
                        (when (conf/set-theme! theme-key)
                          (prof/status-ok! "Theme selected")))))


(defn- delete-profile [state]
  (let [idx (:selected-profile state)]
    (if (> (count (:profiles state)) 1)
      (let [new-state
            (-> state
                (assoc :selected-profile 0)
                (update-in [:profiles]
                           #(into (subvec % 0 idx)
                                  (subvec % (inc idx)))))]
        (prof/status-ok! "Profile deleted")
        new-state)
      (do (prof/status-err! "Can't delete last remaining profile")
          state))))


(defn- duplicate-at [idx v]
  (vec (concat (take idx v)
               [(nth v idx)]
               [(nth v idx)]
               (drop (inc idx) v))))


(defn- duplicate-profile [state]
  (let [idx (:selected-profile state)]
    (-> state
        (update-in [:profiles] (partial duplicate-at idx))
        (update-in [:selected-profile] inc))))


(defn act-prof-del! [*state]
  (ssc/action :name "Del"
              :handler (fn [_] (swap! *state delete-profile))))


(defn act-prof-dupe! [*state]
  (ssc/action :name "Duplicate"
             :handler (fn [_]
                        (swap! *state duplicate-profile)
                        (prof/status-ok! "Profile duplicated"))))


(def ^:private chooser-f-prof [["Profiles (.a7p)" ["a7p"]]])


(defn- save-as [*state _ ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (when-let [full-fp (fio/save! *state fp)]
      (prof/status-ok! (str "Saved as " full-fp)))))


(defn- save-as-chooser [*state]
  (chooser/choose-file
   :all-files? false
   :type :save
   :filters chooser-f-prof
   :success-fn (partial save-as *state)))


(defn- load-from [*state _ ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (when (fio/load! *state fp)
      (prof/status-ok! (str "Loaded profiles from " fp)))))


(defn- load-from-chooser [*state]
  (chooser/choose-file
   :all-files? false
   :type :open
   :filters chooser-f-prof
   :success-fn (partial load-from *state)))


(defn act-save! [*state]
  (ssc/action
   :name "Save"
   :handler (fn [_]
              (if-let [fp (fio/get-cur-fp)]
                (when (fio/save! *state fp)
                  (prof/status-ok! "Saved"))
                (save-as-chooser *state)))))


(defn act-save-as! [*state]
  (ssc/action
   :name "Save As    "
   :handler (fn [_] (save-as-chooser *state))))


(defn act-reload! [*state]
  (ssc/action
   :name "Reload"
   :handler (fn [_] (if-let [fp (fio/get-cur-fp)]
                      (when (fio/load! *state fp)
                        (prof/status-ok! (str "Reloaded " fp)))
                      (load-from-chooser *state)))))


(defn act-open! [*state]
  (ssc/action
   :name "Open"
   :handler (fn [_] (load-from-chooser *state))))


(def ^:private chooser-f-json [["Profile exported as JSON (.json)" ["json"]]])


(defn- export-as [*state _ ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (when-let [full-fp (fio/export! *state fp)]
      (prof/status-ok! (str "Exported profiles to " full-fp)))))


(defn- export-to-chooser [*state]
  (chooser/choose-file
   :all-files? false
   :type :save
   :filters chooser-f-json
   :success-fn (partial export-as *state)))


(defn- import-from [*state _ ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (when-let [full-fp (fio/import! *state fp)]
      (prof/status-ok! (str "Imported profiles from " full-fp)))))


(defn- import-from-chooser [*state]
  (chooser/choose-file
   :all-files? false
   :type :open
   :filters chooser-f-json
   :success-fn (partial import-from *state)))


(defn act-import! [*state]
  (ssc/action
   :name "Import"
   :handler (fn [_] (import-from-chooser *state))))


(defn act-export! [*state]
  (ssc/action
   :name "Export"
   :handler (fn [_] (export-to-chooser *state))))


(defn- list-with-elem-at-index
  [cur-order elem-to-move new-idx]
  (let [cur-order (vec cur-order)
        cur-idx (.indexOf
                 ^clojure.lang.PersistentVector cur-order
                          elem-to-move)]
    (if (= new-idx cur-idx)
      cur-order
      (if (< new-idx cur-idx)
        (vec (concat (subvec cur-order 0 new-idx)
                     [elem-to-move]
                     (subvec cur-order new-idx cur-idx)
                     (subvec cur-order (inc cur-idx))))
        (vec (concat (subvec cur-order 0 cur-idx)
                     (subvec cur-order (inc cur-idx) new-idx)
                     [elem-to-move]
                     (subvec cur-order new-idx)))))))


(defn- linked-sw-pos? [*state dist-idx sw-pos-sel]
  (let [sw-dist-idx (prof/get-in-prof* *state [sw-pos-sel :c-idx])]
    (= sw-dist-idx dist-idx)))


(defn zeroing-dist-idx? [*state dist-idx]
  (let [zd-idx (prof/get-in-prof* *state [:c-zero-distance-idx])]
    (= zd-idx dist-idx)))


(defn- mk-distances-renderer [*state]
  (fn [w {:keys [value index]}]
    (let [pad (apply str (repeat 2 " "))] ;; FIXME Use mono-space font instead
      (ssc/value! w (let [idx-s (str "[" index "]")]
                      (str idx-s
                           (apply str
                                  " "
                                  (if (zeroing-dist-idx? *state index)
                                    "Z" pad)

                                  (if (linked-sw-pos? *state index :sw-pos-a)
                                    "A" pad)

                                  (if (linked-sw-pos? *state index :sw-pos-b)
                                    "B" pad)

                                  (if (linked-sw-pos? *state index :sw-pos-c)
                                    "C" pad)

                                  (if (linked-sw-pos? *state index :sw-pos-d)
                                    "D" pad)
                                  (repeat (- 10 (* 2 (count idx-s))) " "))
                           (str value) " meters"))))))


(defn distances-listbox
  [*state]
  (let
   [sel [:distances]
    get-state (partial prof/get-in-prof* *state sel)
    set-state! (partial prof/assoc-in-prof! *state sel)
    th (dnd/default-transfer-handler
        :import [dnd/string-flavor
                 (fn [{:keys [data drop? drop-location]}]
                   (let [item-list (get-state)
                         item-set (set item-list)]
                     (when (and drop?
                                (:insert? drop-location)
                                (:index drop-location)
                                (item-set data))
                       (let [new-order (list-with-elem-at-index
                                        item-list
                                        data
                                        (:index drop-location))]
                         (set-state! new-order)
                         (prof/status-ok! "Distances reordered")))))]
        :export {:actions (constantly :copy)
                 :start   (fn [c]
                            [dnd/string-flavor
                             (ssc/selection c)])})
    lb (ssc/listbox :model (get-state)
                    :id :distance-list
                    :border (border/line-border
                             :thickness 1
                             :color (default-color "TextField.foreground"))
                    :drag-enabled? true
                    :drop-mode :insert
                    :renderer (cells/default-list-cell-renderer
                               (mk-distances-renderer *state))
                    :transfer-handler th)
    dt (mk-debounced-transform #(prof/get-in-prof % sel))]
    (ssb/bind *state
              (ssb/some dt)
              (ssb/property lb :model))
    lb))


(defn input-distance [*state & opts]
  (let [spec ::prof/distance
        get-df (constantly 100)
        {:keys [min-v max-v units]} (meta (s/get-spec spec))
        wrapped-fmt (wrap-formatter
                     (mk-int-fmt-default get-df))
        fmtr (new DefaultFormatterFactory
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt)
        jf (ssc/construct JFormattedTextField fmtr)
        commit (fn [_] (.commitEdit ^JFormattedTextField jf)
                 (let [new-val (.getValue ^JFormattedTextField jf)]
                   (if (s/valid? spec new-val)
                     (prof/update-in-prof!
                      *state
                      [:distances]
                      (fn [old-dist]
                        (let [new-dis (into [new-val] old-dist)]
                          (if (s/valid? ::prof/distances new-dis)
                            (do (prof/status-ok! "Distance added")
                                new-dis)
                            (do (prof/status-err!
                                 "There can't be more than 200 distances.")
                                old-dist)))))
                     (prof/status-err!
                      (str "Distance should be from " min-v " to " max-v)))))
        tooltip-text (str "Number in range from " min-v " to " max-v)]
    (ssc/border-panel
     :border (border/line-border :thickness 1)
     :center (add-tooltip
              (ssc/horizontal-panel
               :items
               (add-units
                (doto jf
                  (add-tooltip tooltip-text)
                  (sso/apply-options opts))
                units))
              tooltip-text)
     :west (ssc/button :text "Add" :listen [:action commit]))))


(defn- mk-input-sel-distance*
  [*state vpath mk-model-fn renderer-fn idx-xf & opts]
  (let [sel #(prof/get-in-prof % vpath)
        w (ssc/combobox :model (mk-model-fn @*state)
                        :selected-index (idx-xf (sel @*state))
                        :renderer (cells/default-list-cell-renderer
                                   renderer-fn))
        dt (mk-debounced-transform
            (fn [state]
              {:m (mk-model-fn state) :idx (sel state)}))]
    (ssb/bind *state
              (ssb/some dt)
              (ssb/notify-later)
              (ssb/tee
               (ssb/bind (ssb/transform :m)
                         (ssb/property w :model))
               (ssb/bind
                (ssb/transform (fn [{:keys [m idx]}]
                                 ;; This code is a bit stinky.
                                 ;; The idea is that the distance indexes
                                 ;; are in range 0-200 and -1 means that
                                 ;; we aren't using them. So to go from
                                 ;; our +1 model to distances list we have
                                 ;; to dec all indexes in the our model.
                                 ;; Well. Expect -1.

                                 (min (idx-xf idx) (dec (count m)))))
                (ssb/property w :selected-index))))
    (sso/apply-options w opts)))


(defn- dist-sw-model-fn [state]
  (into [[-1 0]] (map-indexed vector) (prof/get-in-prof state [:distances])))


(defn- input-sel-sw-distance-renderer [w {[idx dist] :value}]
  (ssc/value! w (if (= idx -1)
                  "Manual"
                  (str "[" idx "] " dist " meters"))))


(defn- dist-sel! [*state vpath idx]
  (prof/assoc-in-prof! *state vpath ::prof/c-idx idx))


(defn- dist-sw-selection-fn [*state vpath dist-cont e]
  (ssc/invoke-later
   (let [idx (dec (ssc/config e :selected-index))]
     (dist-sel! *state vpath idx)
     (->> [:#distance-list]
          (ssc/select dist-cont)
          ssc/repaint!))))


(defn- input-sel-distance-renderer [w {[idx dist] :value}]
  (ssc/value! w (str "[" idx "] " dist " meters")))


(defn- dist-model-fn [state]
  (into [] (map-indexed vector) (prof/get-in-prof state [:distances])))


(defn- dist-selection-fn [*state vpath e]
  (ssc/invoke-later (dist-sel! *state vpath (ssc/config e :selected-index))))


(defn input-sel-sw-distance [dist-cont *state vpath & opts]
  (let [w (apply mk-input-sel-distance*
                 *state
                 vpath
                 dist-sw-model-fn
                 input-sel-sw-distance-renderer
                 inc
                 opts)]
    (doto w (ssc/listen :selection (partial dist-sw-selection-fn
                                             *state
                                             vpath
                                             dist-cont)))))


(defn input-sel-distance [*state vpath & opts]
  (let [w (apply mk-input-sel-distance*
                 *state
                 vpath
                 dist-model-fn
                 input-sel-distance-renderer
                 identity
                 opts)]
    (doto w (ssc/listen :selection (partial dist-selection-fn
                                             *state
                                             vpath)))))
