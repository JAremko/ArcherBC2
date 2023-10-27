(ns tvt.a7.profedit.widgets
  (:require [tvt.a7.profedit.config :as conf]
            [tvt.a7.profedit.fio :as fio]
            [tvt.a7.profedit.profile :as prof]
            [clojure.java.io :as io]
            [seesaw.options :as sso]
            [seesaw.core :as ssc]
            [seesaw.bind :as ssb]
            [seesaw.event :as sse]
            [seesaw.cells :as cells]
            [seesaw.chooser :as chooser]
            [clojure.spec.alpha :as s]
            [seesaw.value :as ssv]
            [seesaw.tree :as sst]
            [seesaw.color :refer [default-color color]]
            [seesaw.dnd :as dnd]
            [dk.ative.docjure.spreadsheet :as sp]
            [clojure.string :as string]
            [j18n.core :as j18n])
  (:import [javax.swing.text
            DefaultFormatterFactory
            DefaultFormatter]
           [org.apache.poi.ss.usermodel Workbook]
           [javax.swing.tree TreePath]
           [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]
           [javax.swing.filechooser FileNameExtensionFilter]
           [numericutil CustomNumberFormatter CustomNumberFormat]
           [java.io File]
           [java.awt AWTEvent]
           [java.awt.event KeyEvent]
           [javax.swing JFormattedTextField JComponent JFileChooser JList]))


(defn opts-on-nonempty-input [widget opts]
  (sso/apply-options widget (into opts [:nonempty-input :class])))


(defn- state-unsaved? [*state]
  (if-let [cur-fp (fio/get-cur-fp)]
    (let [*temp-state (atom {})]
      (fio/load! *temp-state cur-fp)
      (not= (deref *state) (deref *temp-state)))
    true))


(defn notify-if-state-dirty! [*state frame]
  (->> (ssc/confirm
        frame
        (j18n/resource ::save-current-file-question)
        :title (j18n/resource ::save-current-file-title)
        :type :warning
        :option-type :yes-no)
       (false?)
       (when (state-unsaved? *state))))


(def foreground-color (partial default-color "TextField.foreground"))


(defn non-empty-string? [value]
  (and value
       (string? value)
       (seq value)
       (re-find #"\S" value)))


(defn parse-input-str [input-str fraction-digits]
  (when (non-empty-string? input-str)
    (let [nf (doto (CustomNumberFormat.)
               (.setMaximumFractionDigits fraction-digits)
               (.setParseIntegerOnly false)
               (.setGroupingUsed false))]
      (try
        (.doubleValue (.parse nf input-str))
        (catch Exception _ nil)))))


(defn- report-parse-err! [input-str spec parsed-val]
  (prof/status-err!
   (if parsed-val
     (prof/format-spec-err spec parsed-val)
     (str (j18n/resource ::failed-to-parse-inp-value) input-str)))
  nil)


(defn- round-to [num fraction-digits]
  (let [bd-num (bigdec num)]
    (-> bd-num
        (.setScale ^int fraction-digits java.math.RoundingMode/HALF_UP)
        ^double .doubleValue)))


(defn str->long [s]
  (when-let [val (parse-input-str s 0)] (long val)))


(defn str->double [s fraction-digits]
  (when-let [val (parse-input-str s fraction-digits)] (double val)))


(defn val->str [val fraction-digits]
  (let [format (doto (CustomNumberFormat.)
                 (.setMaximumFractionDigits fraction-digits)
                 (.setGroupingUsed false))]
    (.format format val)))


(defn truncate-with-ellipsis ^java.lang.String
  [^clojure.lang.Numbers max-len ^java.lang.String s]
  (if (> (count s) max-len)
    (str (subs s 0 (- max-len 3)) "...")
    s))


(defn- fmt-str
  ^java.lang.String [^clojure.lang.Numbers max-len ^java.lang.String s]
  (truncate-with-ellipsis
   (max max-len 3)
   (if (non-empty-string? s) s (j18n/resource ::empty-str-pl))))


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


(defn commit-key-pressed? [^KeyEvent e]
  (some? (#{10 27} (. e getKeyCode))))


(defn sync-and-commit [*state vpath spec e]
  (let [source ^JFormattedTextField (get-event-source e)
        _ (.commitEdit source)
        new-val (.getValue source)
        fd (->> spec s/get-spec meta :fraction-digits)]
    (if (s/valid? spec new-val)
      (prof/assoc-in-prof! *state vpath (if fd (round-to new-val fd)
                                            new-val))
      (do (report-parse-err! (ssc/text source) spec new-val)
          (ssc/value! source (prof/get-in-prof *state vpath))))))


(defn sync-text [*state vpath spec ^AWTEvent e]
  (let [source (ssc/to-widget e)
        new-val (ssc/value source)]
    (if (s/valid? spec new-val)
      (prof/assoc-in-prof! *state vpath new-val)
      (do (report-parse-err! (ssc/text source) spec new-val)
          (ssc/value! source (prof/get-in-prof *state vpath))))))


(defn- mk-number-fmt-default
  [fallback-val fraction-digits]
  (proxy [CustomNumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (str->double s fraction-digits)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (val->str (double (or value (fallback-val))) fraction-digits)))))


(defn round-to-closest-0125-mul [val]
  (* 0.125 (Math/round (/ val 0.125))))


(defn mk-number-0125-mult-fmt-default
  [fallback-val fraction-digits]
  (proxy [CustomNumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (round-to-closest-0125-mul (str->double s fraction-digits))))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (val->str (double (or value (fallback-val))) fraction-digits)))))


(defn mk-int-fmt-default
  [fallback-val _]
  (proxy [CustomNumberFormatter] []
    (stringToValue
      (^clojure.lang.Numbers [^java.lang.String s]
       (str->long s)))

    (valueToString
      (^java.lang.String [^clojure.lang.Numbers value]
       (val->str (long (or value (fallback-val))) 0)))))


(defn mk-str-fmt-default
  [max-len]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len s)))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len value)))))


(defn mk-str-fmt-null
  [max-len get-fallback]
  (proxy [DefaultFormatter] []
    (stringToValue
      (^java.lang.String [^java.lang.String s]
       (fmt-str max-len (or s (get-fallback)))))

    (valueToString
      (^java.lang.String [^java.lang.String value]
       (fmt-str max-len (or value (get-fallback)))))))


(defn wrap-formatter [^DefaultFormatter fmtr]
  (doto fmtr
    (.setCommitsOnValidEdit false)
    (.setOverwriteMode false)))


(defn add-units [input units]
  (if units
    [input (ssc/text :text (str " " (j18n/resource units) " ")
                     :id :units
                     :editable? false
                     :focusable? false
                     :margin 0)]
    [input]))


(defn add-tooltip [^JComponent input ^String tooltip]
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


(defn create-input [formatter *state vpath spec & opts]
  (let [{:keys [min-v max-v units fraction-digits]} (meta (s/get-spec spec))
        wrapped-fmt (wrap-formatter
                     (formatter #(or (prof/get-in-prof* *state vpath) min-v)
                                fraction-digits))
        fmtr (new DefaultFormatterFactory
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt)
        jf (ssc/construct JFormattedTextField fmtr)
        tooltip-text (format (j18n/resource ::input-tooltip-text)
                             (str min-v), (str max-v))]
    (ssc/config! jf :id :input)
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
         (opts-on-nonempty-input opts))
       units))
     tooltip-text)))


(defn input-num [& args]
  (apply create-input mk-number-fmt-default args))


(defn input-0125-mult [& args]
  (apply create-input mk-number-0125-mult-fmt-default args))


(defn input-str
  [*state vpath spec & opts]
  (let [max-length (:max-length (meta (s/get-spec spec)))
        formatter (new DefaultFormatterFactory
                       (wrap-formatter (mk-str-fmt-default max-length))
                       (wrap-formatter (mk-str-fmt-default max-length))
                       (wrap-formatter (mk-str-fmt-default max-length))
                       (wrap-formatter (mk-str-fmt-null
                                        max-length
                                        #(prof/get-in-prof* *state vpath))))
        jf (ssc/construct JFormattedTextField formatter)]
    (ssb/bind *state
              (ssb/some (mk-debounced-transform #(prof/get-in-prof % vpath)))
              (ssb/value jf))
    (doto jf
      (add-tooltip (format (j18n/resource ::str-input-tooltip-text)
                           (str max-length)))
      (sse/listen
       :focus-lost (partial sync-and-commit *state vpath spec)
       :key-pressed #(when (commit-key-pressed? %)
                       (sync-and-commit *state vpath spec %)))
      (opts-on-nonempty-input opts))))


(defn input-mul-text
  [*state vpath spec & opts]
  (let [w (ssc/text :multi-line? true :rows 3)]
    (ssb/bind *state
              (ssb/transform #(prof/get-in-prof % vpath))
              (ssb/value w))
    (doto w
      (add-tooltip (j18n/resource ::input-mull-str))
      (ssc/value! (prof/get-in-prof* *state vpath))
      (sse/listen
       :focus-lost (partial sync-text *state vpath spec))
      (sso/apply-options opts))))


(defn status [& opts]
  (let [icon-good (conf/key->icon :status-bar-icon-good)
        icon-bad (conf/key->icon :status-bar-icon-bad)
        w-icon (ssc/label :icon icon-good)
        w-text (ssc/text :foreground (foreground-color)
                         :editable? false
                         :focusable? false
                         :text (#(get-in % [:status-text]) @prof/*status))]
    (ssb/bind
     prof/*status
     (ssb/tee
      (ssb/bind (ssb/transform #(get-in % [:status-ok]))
                (ssb/tee
                 (ssb/bind (ssb/transform #(if % icon-good icon-bad))
                           (ssb/property w-icon :icon))
                 (ssb/bind (ssb/transform #(if % (foreground-color) :red))
                           (ssb/property w-text :foreground))))

      (ssb/bind (ssb/transform
                 #(get-in % [:status-text]))
                (ssb/value w-text))))
    (doto (ssc/horizontal-panel
           :items [w-icon (ssc/scrollable w-text :vscroll :never)])
      (add-tooltip (j18n/resource ::status-bar-tip))
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
    (opts-on-nonempty-input w opts)))


(defn- chooser-f-prof []
  [[(j18n/resource ::chooser-f-prof) ["a7p"]]])


(defn- save-as [*state _ ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (when-let [full-fp (fio/save! *state fp)]
      (prof/status-ok! (format (j18n/resource ::saved-as) (str full-fp)))
      true)))


(defn- show-file-chooser
  [frame dialog-title filter-desc filter-ext default-filename]
  (let [file-filter (FileNameExtensionFilter.
                      (j18n/resource filter-desc)
                      (into-array [filter-ext]))
        file-chooser (doto (JFileChooser. (fio/get-cur-fp-dir))
                       (.setFileSelectionMode JFileChooser/FILES_ONLY)
                       (.setDialogType JFileChooser/SAVE_DIALOG)
                       (.setDialogTitle (j18n/resource dialog-title))
                       (.addChoosableFileFilter file-filter)
                       (.setFileFilter file-filter)
                       (.setSelectedFile (File. ^String default-filename)))
        return-val (.showSaveDialog file-chooser frame)]
    (when (= return-val JFileChooser/APPROVE_OPTION)
      (.getSelectedFile file-chooser))))


(defn remove-non-latin [s]
  (string/replace s #"[^A-Za-z0-9\s\-_]" ""))


(defn- generate-default-filename [*state suf ext]
  (let [{:keys [profile-name cartridge-name]} (:profile @*state)
        sanitized-profile-name (remove-non-latin profile-name)
        sanitized-cartridge-name (remove-non-latin cartridge-name)
        time-str (-> (LocalDateTime/now)
                     (.format (DateTimeFormatter/ofPattern
                               "yyyy_MM_dd_HH_mm_ss")))]
    (str sanitized-profile-name "_"
         sanitized-cartridge-name "_"
         time-str "_"
         suf
         "." ext)))


(defn save-as-chooser [*state frame]
  (let [selected-file (show-file-chooser frame
                                         ::save-as
                                         ::chooser-f-prof
                                         "a7p"
                                         (generate-default-filename *state
                                                                    "prof"
                                                                    "a7p"))]
    (when selected-file
      (save-as *state nil selected-file))))


(defn load-from-chooser [*state frame]
  (chooser/choose-file
   frame
   :dir (fio/get-cur-fp-dir)
   :all-files? false
   :type :open
   :filters (chooser-f-prof)
   :success-fn (fn [_ file] (fio/load-from! *state file))))


(defn set-zero-x-y-from-chooser [*state frame]
  (chooser/choose-file
   frame
   :dir (fio/get-cur-fp-dir)
   :all-files? false
   :type :open
   :filters (chooser-f-prof)
   :success-fn (fn [_ ^java.io.File file]
                 (let [tmp-state* (atom {})
                       fp (.getAbsolutePath file)]
                   (fio/side-load! tmp-state* fp)
                   (swap! *state update :profile
                          (fn [profile]
                            (merge profile
                                   (select-keys (:profile @tmp-state*)
                                                [:zero-x :zero-y]))))
                   (prof/status-ok! (format (j18n/resource ::loaded-xy)
                                            fp))))))


(defn- chooser-f-json []
  [[(j18n/resource ::chooser-f-json) ["json"]]])


(defn- export-as [*state _ ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (when-let [full-fp (fio/export! *state fp)]
      (prof/status-ok! (format (j18n/resource ::exported-prof-to) full-fp)))))


(defn export-to-chooser [*state frame]
  (let [selected-file (show-file-chooser frame
                                         ::save-as
                                         ::chooser-f-json
                                         "json"
                                         (generate-default-filename *state
                                                                    "prof"
                                                                    "json"))]
    (when selected-file
      (export-as *state nil selected-file))))

(defn- import-from [*state _ ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (when-let [full-fp (fio/import! *state fp)]
      (prof/status-ok! (format (j18n/resource ::imported-prof-from) full-fp)))))

(defn import-from-chooser [*state]
  (chooser/choose-file
   :dir (fio/get-cur-fp-dir)
   :all-files? false
   :type :open
   :filters (chooser-f-json)
   :success-fn (partial import-from *state)))


(defn zeroing-dist-idx? [*state dist-idx]
  (let [zd-idx (prof/get-in-prof* *state [:c-zero-distance-idx])]
    (= zd-idx dist-idx)))


(defn- mk-distances-renderer [*state]
  (fn [w {:keys [value index]}]
    (when (zeroing-dist-idx? *state index)
      (ssc/config! w :icon (conf/key->icon ::zeroing-dist-icon)))
    (ssc/value! w
                (str (apply str
                            (val->str value (:fraction-digits
                                             (meta
                                              (s/get-spec ::prof/distance))))
                            " "
                            (j18n/resource ::meters))))))


(defn- move-list-item [v from-index to-index]
  (let [item (get v from-index)
        temp-vec (vec (concat (subvec v 0 from-index)
                              (subvec v (inc from-index))))
        v-front (subvec temp-vec 0 to-index)
        v-back (subvec temp-vec to-index)]
    (vec (concat v-front [item] v-back))))


(defn- dl-box-dnd-import-handler
  [*state src-idx drop-idx]
  (swap! *state
         (fn [state]
           (let [distances (prof/get-in-prof state [:distances])
                 z-idx (prof/get-in-prof state [:c-zero-distance-idx])

                 from-zero? (= src-idx z-idx)
                 to-zero? (= drop-idx z-idx)
                 to-zero-down? (and to-zero? (> src-idx z-idx))
                 to-zero-up? (and to-zero? (> z-idx src-idx))
                 over-z-down? (< src-idx z-idx drop-idx)
                 over-z-up? (< drop-idx z-idx src-idx)

                 mv-zero (fn [_] drop-idx)

                 new-distances (move-list-item distances src-idx drop-idx)
                 new-z-idx ((cond from-zero? mv-zero
                                  to-zero-down? inc
                                  to-zero-up? dec
                                  over-z-down? dec
                                  over-z-up? inc
                                  :else identity) z-idx)]
             (update
              state
              :profile
              (fn [profile]
                (-> profile
                    (assoc :distances new-distances)
                    (assoc :c-zero-distance-idx new-z-idx))))))))


(defn- mk-dist-list-box-dnd-handler [*state lb]
  (dnd/default-transfer-handler
   :import [dnd/string-flavor
            (fn [{:keys [data drop? drop-location]}]
              (let [src-idx (:index data)
                    drop-idx (:index drop-location)
                    same-idx? (= src-idx drop-idx)]
                (when (and (not same-idx?)
                           drop?
                           (:insert? drop-location)
                           drop-idx)
                  (dl-box-dnd-import-handler *state src-idx drop-idx))))]

   :export {:actions (constantly :copy)
            :start (fn [_]
                     [dnd/string-flavor
                      {:index (.getSelectedIndex ^JList lb)}])}))


(defn distances-listbox
  [*state]
  (let [lb (ssc/listbox :model (prof/get-in-prof* *state [:distances])
                        :id :distance-list
                        :drag-enabled? true
                        :drop-mode :insert
                        :renderer (cells/default-list-cell-renderer
                                   (mk-distances-renderer *state)))
        dt (mk-debounced-transform #(prof/get-in-prof % [:distances]))]
    (ssc/config! lb :transfer-handler (mk-dist-list-box-dnd-handler *state lb))
    (ssb/bind *state
              (ssb/some dt)
              (ssb/property lb :model))
    lb))


(defn- add-dist [dist idx old-dists]
  (let [new-dists (concat (subvec old-dists 0 idx)
                          [dist]
                          (subvec old-dists idx))]
    (if (s/valid? ::prof/distances (vec new-dists))
      (do (prof/status-ok! ::distance-added-msg)
          (vec new-dists))
      (do (prof/status-err! ::dist-limit-msg)
          old-dists))))


(defn input-distance [*state & opts]
  (let [spec ::prof/distance
        get-df (constantly 100.0)
        {:keys [min-v max-v fraction-digits units]} (meta (s/get-spec spec))
        wrapped-fmt (wrap-formatter
                     (mk-number-fmt-default get-df fraction-digits))
        fmtr (new DefaultFormatterFactory
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt)
        jf (ssc/construct JFormattedTextField fmtr)
        commit
        (fn [e]
          (.commitEdit ^JFormattedTextField jf)
          (ssc/invoke-later
           (let [new-val (round-to (.getValue ^JFormattedTextField jf)
                                   fraction-digits)
                 up-fn (fn [state]
                         (let [prof-sel (fn [suf] [:profile suf])
                               d-lb (ssc/select (ssc/to-root e)
                                                [:#distance-list])
                               s-idx (max (.getSelectedIndex ^JList d-lb) 0)
                               c-idx (get-in state
                                             (prof-sel
                                              :c-zero-distance-idx))
                               rv (-> state
                                      (update-in (prof-sel :distances)
                                                 (partial add-dist new-val s-idx))
                                      (update-in (prof-sel :c-zero-distance-idx)
                                                 (if (>= c-idx s-idx)
                                                   inc identity)))]
                           (ssc/invoke-later
                            (doto ^JList d-lb
                              (.setSelectedIndex s-idx)
                              (.ensureIndexIsVisible s-idx))
                            (ssc/request-focus! d-lb))
                           rv))]
             (.setText jf (val->str new-val fraction-digits))
             (if (s/valid? spec new-val)
               (swap! *state up-fn)
               (prof/status-err!
                (format (j18n/resource ::imput-dist-range-err)
                        (str min-v)
                        (str max-v)))))))
        tooltip-text (format (j18n/resource ::input-dist-tip)
                             (str min-v)
                             (str max-v))
        commit-on-enter (fn [^KeyEvent e]
                          (when (= (.getKeyChar e) \newline)
                            (commit e)))]
    (ssc/listen jf :key-typed commit-on-enter)
    (ssc/border-panel
     :center (add-tooltip
              (ssc/horizontal-panel
               :items
               (add-units
                (doto jf
                  (add-tooltip tooltip-text)
                  (sso/apply-options opts))
                units))
              tooltip-text)
     :west (ssc/button
            :icon (conf/key->icon :distances-button-add-icon)
            :text ::add
            :listen [:action commit]))))


(defn resize-vector [vect n cons-fn]
  (let [orig-size (count vect)
        new-vector (if (> n orig-size)
                     (concat vect (repeat (- n orig-size) (cons-fn)))
                     (subvec vect 0 n))]
    (vec new-vector)))


(defn get-selected-bc-coefs [state]
  (let [profile (:profile state)
        bc-type (:bc-type profile)
        coef-key (keyword (str "coef-" (name bc-type)))]
    (get profile coef-key)))


(defn input-coef-count [*state refresh-fn & opts]
  (let [get-df #(->> *state deref get-selected-bc-coefs count)
        wrapped-fmt (wrap-formatter (mk-int-fmt-default get-df nil))
        fmtr (new DefaultFormatterFactory
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt)
        jf (ssc/construct JFormattedTextField fmtr)
        commit (fn [e]
                 (.commitEdit ^JFormattedTextField jf)
                 (ssc/invoke-later
                  (let [frame (ssc/to-root e)
                        new-val (.getValue ^JFormattedTextField jf)
                        upd-fn (fn [state]
                                 (let [prof-sel (fn [suf]
                                                  [:profile suf])
                                       bc-t (get-in state (prof-sel :bc-type))
                                       bc-sel-key (->> bc-t
                                                       name
                                                       (str "coef-")
                                                       keyword)
                                       min-count 1
                                       max-count (if (= bc-t :custom) 200 5)
                                       c-f (constantly (if (= bc-t :custom)
                                                         {:cd 0.0 :ma 0.0}
                                                         {:bc 0.0 :mv 0.0}))]
                                   (if (and (<= new-val max-count)
                                            (>= new-val min-count))
                                     (do (prof/status-ok! ::status-ready)
                                         (update-in
                                          state
                                          (prof-sel bc-sel-key)
                                          #(resize-vector % new-val c-f)))
                                     (do (prof/status-err! ::bad-coef-count)
                                         state))))]
                    (swap! *state upd-fn)
                    (refresh-fn *state frame))))
        commit-on-enter (fn [^KeyEvent e]
                          (when (= (.getKeyChar e) \newline)
                            (commit e)))
        dt (mk-debounced-transform (fn [state]
                                     (count (get-selected-bc-coefs state))))]
    (ssb/bind *state (ssb/some dt) (ssb/property jf :text))
    (ssc/listen jf :key-typed commit-on-enter)
    (ssc/border-panel
     :east (ssc/button :icon (conf/key->icon
                              :ball-coef-btn-set-row-count-icon)
                       :listen [:action commit])
     :center (sso/apply-options jf opts))))


(defn input-set-distance [*state idx-vpath & opts]
  (let [spec ::prof/distance
        mk-vp (fn [state] [:distances (prof/get-in-prof state idx-vpath)])
        {:keys [min-v max-v fraction-digits units]} (meta (s/get-spec spec))
        wrapped-fmt (wrap-formatter
                     (mk-number-fmt-default
                      #(or (prof/get-in-prof* *state (mk-vp @*state)) min-v)
                      fraction-digits))
        fmtr (new DefaultFormatterFactory
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt
                  wrapped-fmt)
        jf (ssc/construct JFormattedTextField fmtr)
        tooltip-text (format (j18n/resource ::input-tooltip-text)
                             (str min-v), (str max-v))
        atom-sync
        (fn [e]
          (swap!
           *state
           (fn [state]
             (let [source ^JFormattedTextField (get-event-source e)
                   _ (.commitEdit source)
                   new-val (.getValue source)
                   fd (->> spec s/get-spec meta :fraction-digits)
                   vpath (mk-vp state)]
               (if (s/valid? spec new-val)
                 (do
                   (prof/status-ok! ::prof/status-ready)
                   (prof/assoc-in-prof state vpath (if fd
                                                     (round-to new-val fd)
                                                     new-val)))
                 (do (report-parse-err! (ssc/text source) spec new-val)
                     (ssc/value! source (prof/get-in-prof state vpath))
                     state))))))]
    (ssc/config! jf :id :input)
    (ssb/bind *state
              (ssb/some (mk-debounced-transform
                         #(prof/get-in-prof % (mk-vp %))))
              (ssb/value jf))
    (add-tooltip
     (ssc/horizontal-panel
      :items
      (add-units
       (doto jf
         (add-tooltip tooltip-text)
         (sse/listen
          :focus-lost atom-sync
          :key-pressed #(when (commit-key-pressed? %)
                          (atom-sync %)))
         (sso/apply-options opts))
       units))
     tooltip-text)))


(defn make-banner []
  (ssc/vertical-panel
   :items [(ssc/flow-panel
            :align :right
            :background (color 33 37 43)
            :items [(ssc/label :icon (conf/banner-source "banner.png"))])
           (ssc/separator)]))


(defn- valid-file? [^java.io.File f]
  (and (.exists f)
       (.canRead f)))


(defn- profile-file? [^java.io.File f]
  (and (valid-file? f)
       (.isFile f)
       (.endsWith (.getName f) ".a7p")))


(defn- make-file-tree-model [profile-storages]
  (sst/simple-tree-model
   :path
   :profiles
   {:path "" :profiles (vec profile-storages)}))


(defn- file-tree-render-item [renderer {value :value}]
  (when value
    (ssc/config! renderer :text (cond (map? value)
                                      (let [{:keys [device serial version path]}
                                            value]
                                        (if device
                                          (format "%s:%s (%s) [%s]"
                                                  device serial version path)
                                          (str "[" path "]")))
                                      (string? value) (.getName (io/file value))
                                      :else ""))))


(defn- file-tree-path [tree ^String path]
  (let [model ^javax.swing.tree.TreeModel (ssc/config tree :model)
        root (.getRoot model)]
    [root
     (first (filter #(->> % :profiles (some (partial = path))) (:profiles root)))
     path]))


(defn reset-tree-selection [^javax.swing.JTree tree]
  (when-let [file-path (fio/get-cur-fp)]
    (let [t-path (file-tree-path tree file-path)]
      (if (every? some? t-path)
        (let [j-t-path (TreePath. (to-array t-path))]
          (doto tree
            (.setSelectionPath j-t-path)
            (.scrollPathToVisible j-t-path)))
        (.clearSelection tree)))))


(defn- make-file-tree-w [*state]
  (let [file-tree (ssc/tree :id :tree
                            :root-visible? false
                            :row-height 35
                            :expands-selected-paths? true
                            :scrolls-on-expand? true
                            :model (make-file-tree-model @fio/*profile-storages)
                            :renderer file-tree-render-item)

        maybe-load-file (fn [e]
                          (when-let [fp (last (ssc/selection file-tree))]
                            (when (string? fp)
                              (let [f (File. ^String fp)
                                    this-fp (fio/get-cur-fp)]
                                (when (and (not= fp this-fp)
                                           (valid-file? f)
                                           (profile-file? f))
                                  (if-not (notify-if-state-dirty!
                                           *state
                                           (ssc/to-root e))
                                    (ssc/invoke-later
                                     (fio/load-from! *state f)
                                     (ssc/request-focus! e))
                                    (ssc/invoke-later
                                      (reset-tree-selection file-tree)
                                      (ssc/request-focus! e))))))))]

    (ssc/invoke-later (ssb/bind fio/*profile-storages
                                (ssb/transform
                                 (fn [p-s]
                                   (ssc/invoke-later
                                    (reset-tree-selection file-tree))
                                   (make-file-tree-model p-s)))
                                (ssb/property file-tree :model))

                      (reset-tree-selection file-tree)

                      (ssc/listen file-tree :selection maybe-load-file))
    file-tree))


(defn file-tree [*state]
  (make-file-tree-w *state))


(defn- chooser-f-excel []
  [[(j18n/resource ::chooser-f-excel) ["xls" "xlsx"]]])


(defn load-excel-from-chooser []
  (chooser/choose-file
   :all-files? false
   :type :open
   :filters (chooser-f-excel)
   :success-fn (fn ^Workbook [_ file]
                 (sp/load-workbook-from-file file))))


(defn save-excel-as-chooser [*state frame suf ^Workbook workbook]
  (let [^java.io.File selected-file
        (show-file-chooser frame
                           ::save-as
                           ::chooser-f-excel-xlsx
                           "xlsx"
                           (generate-default-filename *state
                                                      suf
                                                      "xlsx"))]
    (when selected-file
      (sp/save-workbook-into-file!
       (.getAbsolutePath selected-file)
       workbook))))


(defn workbook->header-vec [^Workbook workbook]
  (some->> workbook
           (sp/sheet-seq)
           (first)
           sp/row-seq
           first
           (mapv str)))


(defn get-workbook-column [^Workbook workbook idx]
  (some->> workbook
           (sp/sheet-seq)
           (first)
           (sp/row-seq)
           (map #(str (nth (sp/cell-seq %) idx)))
           (drop 1)))
