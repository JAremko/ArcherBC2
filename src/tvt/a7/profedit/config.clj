(ns tvt.a7.profedit.config
  (:require [clojure.spec.alpha :as s]
            [tvt.a7.profedit.fio :as fio]
            [clojure.java.io :as io]
            [tvt.a7.profedit.asi :as asi]
            [tvt.a7.profedit.profile :as prof]
            [seesaw.core :as sc])
  (:import com.github.weisj.darklaf.LafManager
           [javax.swing UIManager]
           [java.util Locale]
           [javax.swing.plaf FontUIResource]
           [com.github.weisj.darklaf.theme
            SolarizedLightTheme
            SolarizedDarkTheme
            OneDarkTheme
            IntelliJTheme
            HighContrastLightTheme
            HighContrastDarkTheme]))


(defn loc-key->pair [key]
  (get {:english ["en" "EN"]
        :ukrainian ["uk" "UA"]}
       key
       ["en" "EN"]))


(def loc-key->icon
  {:english (sc/icon "flags/en.png")
   :ukrainian (sc/icon "flags/ua.png")})


(s/def ::color-theme #{:sol-light :sol-dark :dark :light :hi-light :hi-dark})


(s/def ::locale #{:english :ukrainian})


(s/def ::config (s/keys :req-un [::color-theme ::locale]))


(def default-config {:color-theme :sol-light :locale :english})


(def ^:private *config (atom default-config))


(defn get-color-theme []
  (get @*config :color-theme (:color-theme default-config)))


(defn input-stream->bytes [is]
  (let [buffer (java.io.ByteArrayOutputStream.)]
    (io/copy is buffer)
    (.toByteArray buffer)))


(def ^:private ph-bg (input-stream->bytes
                      (->> "glasses.png" io/resource io/input-stream)))

(def ^:private ph-icon (input-stream->bytes
                        (->> "glasses_small.png" io/resource io/input-stream)))

;; TODO: DRY IT =========================================

(defn key->skin [img-key]
  (let [img-name (name img-key)
        theme-name (name (get-color-theme))
        working-dir (System/getProperty "user.dir")
        file (io/file (str working-dir "/skins/" theme-name)
                      (str img-name ".png"))]
    (io/make-parents file)
    (if (.exists file)
      (sc/icon file)
      (do
        (with-open [out (io/output-stream file)]
          (io/copy (java.io.ByteArrayInputStream. ph-bg) out))
        (sc/icon file)))))


(defn key->icon [img-key]
  (let [img-name (name img-key)
        theme-name (name (get-color-theme))
        working-dir (System/getProperty "user.dir")
        file (io/file (str working-dir "/skins/" theme-name "/icons")
                      (str img-name ".png"))]
    (io/make-parents file)
    (if (.exists file)
      (sc/icon file)
      (do
        (with-open [out (io/output-stream file)]
          (io/copy (java.io.ByteArrayInputStream. ph-icon) out))
        (sc/icon file)))))


;; =====================================================


(defn save-config! [filename]
  (let [config @*config]
    (if (s/valid? ::config config)
      (fio/write-config filename config)
      (do
        (asi/pop-report! (prof/val-explain ::config config))
        (prof/status-err! ::bad-config-save-err)))))


(defn load-config! [filename]
  (if-let [new-config (fio/read-config filename)]
    (if (s/valid? ::config new-config)
      (reset! *config new-config)
      (do (prof/status-err! ::bad-config-file-err)
          (asi/pop-report! (prof/val-explain ::config new-config))))
    (do (reset! *config default-config)
        (save-config! filename))))


(defn set-theme! [theme-key]
  (if (s/valid? ::color-theme theme-key)
    (do
      (swap! *config assoc :color-theme theme-key)
      (condp = theme-key
        :sol-light (LafManager/setTheme (new SolarizedLightTheme))
        :sol-dark (LafManager/setTheme (new SolarizedDarkTheme))
        :dark (LafManager/setTheme (new OneDarkTheme))
        :light (LafManager/setTheme (new IntelliJTheme))
        :hi-light (LafManager/setTheme (new HighContrastLightTheme))
        :hi-dark (LafManager/setTheme (new HighContrastDarkTheme))
        (LafManager/setTheme (new OneDarkTheme)))
      (LafManager/install)
      (save-config! (fio/get-config-file-path)))
    (do (asi/pop-report! (prof/val-explain ::color-theme theme-key))
        (prof/status-err! ::bad-theme-selection-err))))


(defn get-locale []
  (get @*config :locale (:locale default-config)))


(defn set-locale! [loc-key]
  (if (s/valid? ::locale loc-key)
    (do
      (let [[f l] (loc-key->pair loc-key)]
        (. Locale setDefault (new Locale f l)))
      (swap! *config assoc :locale loc-key)
      (save-config! (fio/get-config-file-path)))
    (do (asi/pop-report! (prof/val-explain ::locale loc-key))
        (prof/status-err! ::bad-locale-selection-err))))


(def font-fat (FontUIResource. "Verdana" java.awt.Font/BOLD 22))

(def font-big (FontUIResource. "Verdana" java.awt.Font/PLAIN 24))

(def font-small (FontUIResource. "Verdana" java.awt.Font/PLAIN 16))


(defn set-ui-font! [f]
  (let [keys (enumeration-seq (.keys (UIManager/getDefaults)))]
    (doseq [key keys]
      (when (instance? FontUIResource (UIManager/get key))
        (UIManager/put key f)))))


(defn reset-theme!
  "Same as set-theme but makes sure that fonts are preserved"
  [theme-key event-source]
  (let [rv  (set-theme! theme-key)]
    (sc/invoke-later
     (doseq [fat-label (sc/select (sc/to-root event-source) [:.fat])]
       (sc/config! fat-label :font font-fat)))
    rv))
