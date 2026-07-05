(ns tvt.a7.profedit.config
  (:require [clojure.spec.alpha :as s]
            [tvt.a7.profedit.fio :as fio]
            [clojure.edn :as edn]
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


;; Only themes with SHIPPED skin directories (C5: a config naming a skinless
;; theme used to pass validation and then crash every icon lookup at startup).
(s/def ::color-theme #{:sol-light :sol-dark :dark :hi-dark})


(s/def ::locale #{:english :ukrainian})


(s/def ::config (s/keys :req-un [::color-theme ::locale]))


(def default-config {:color-theme :sol-dark :locale :ukrainian})


(def ^:private *config (atom default-config))


(defn- read-edn-from-resources [filename]
  (when-let [res (io/resource filename)]
    (with-open [reader (io/reader res)]
      (edn/read-string (slurp reader)))))


;; Dev fallback: update_conf.edn is intentionally NOT tracked (it is recreated
;; in CI from .github/workflows/update_conf.edn — single source of truth after
;; the 2023 divergent-config incident). A fresh clone must still be runnable,
;; so fall back to the canonical values in code. Keep in sync with
;; .github/workflows/update_conf.edn.
(def ^:private default-update-conf
  {:github-api-url "https://api.github.com/repos/JAremko/ArcherBC2/tags"
   :version-resource-path "version.txt"})


(defn get-color-theme []
  (get @*config :color-theme (:color-theme default-config)))


(defn input-stream->bytes [is]
  (let [buffer (java.io.ByteArrayOutputStream.)]
    (io/copy is buffer)
    (.toByteArray buffer)))


(def update-conf (or (read-edn-from-resources "update_conf.edn")
                     default-update-conf))


(def ^:private ph-icon (input-stream->bytes
                        (->> "glasses_small.png" io/resource io/input-stream)))


(def ^:private ph-banner (input-stream->bytes
                        (->> "banner.png" io/resource io/input-stream)))


(def ^:private ph-icon-image (javax.swing.ImageIcon. ^bytes ph-icon))

(def ^:private ph-banner-image (javax.swing.ImageIcon. ^bytes ph-banner))


;; Icons were re-read from the classpath on EVERY call (dozens per frame
;; build, again per rebuild — P4); and the old missing-resource fallback tried
;; to WRITE into a nil jar-resource URL and crashed (C5).  Cache per resource
;; path; fall back to an in-memory placeholder.
(def ^:private *icon-cache (atom {}))


(defn- resolve-icon [res-path placeholder]
  (or (get @*icon-cache res-path)
      (let [icon (if-let [url (io/resource res-path)]
                   (sc/icon url)
                   placeholder)]
        (swap! *icon-cache assoc res-path icon)
        icon)))


(defn key->icon [img-key]
  (resolve-icon (str "skins/" (name (get-color-theme))
                     "/icons/" (name img-key) ".png")
                ph-icon-image))


(defn banner-source [b-name]
  (resolve-icon (str "skins/" (name (get-color-theme)) "/banners/" b-name)
                ph-banner-image))


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


(defn- system-locale []
  (let [locale (java.util.Locale/getDefault)]
    [(str (.getLanguage locale))
     (str (.getCountry locale))]))


(defn get-locale []
  (get @*config :locale
       (if (some #{(first (system-locale))} ["ru" "uk"])
         :ukrainian :english)))


(defn set-locale! [loc-key]
  (if (s/valid? ::locale loc-key)
    (do
      (let [[f l] (loc-key->pair loc-key)]
        (. Locale setDefault (new Locale f l)))
      (swap! *config assoc :locale loc-key)
      (save-config! (fio/get-config-file-path)))
    (do (asi/pop-report! (prof/val-explain ::locale loc-key))
        (prof/status-err! ::bad-locale-selection-err))))


(def font-fat (FontUIResource. "Verdana" java.awt.Font/BOLD 26))

(def font-big (FontUIResource. "Verdana" java.awt.Font/PLAIN 24))

(def font-small (FontUIResource. "Verdana" java.awt.Font/PLAIN 16))


(defn set-ui-font! [f]
  (let [keys (enumeration-seq (.keys (UIManager/getDefaults)))]
    (doseq [key keys]
      (when (instance? FontUIResource (UIManager/get key))
        (UIManager/put key f)))))
