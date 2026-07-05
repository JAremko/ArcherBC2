(ns tvt.a7.profedit.fio
  (:require
   [clojure.java.io :as io]
   [clojure.set :refer [difference]]
   [tvt.a7.profedit.rosetta :as ros]
   [j18n.core :as j18n]
   [tvt.a7.profedit.asi :as ass]
   [tvt.a7.profedit.profile :as prof]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [jdk.nio.file.FileSystem :as jnio]
   [me.raynes.fs :as fs]
   [toml.core :as toml]
   [cpath-clj.core :as cp]
   [clojure.spec.alpha :as s]
   [seesaw.core :as sc])
  (:import [java.nio.file FileSystems]
           [java.util Base64]
           [java.lang Thread]))


(def *current-fp (atom nil))


(defn get-cur-fp ^java.lang.String [] (deref *current-fp))


(def ^:private user-profile-dir-name "archer_bc2_profiles")


(defn add-current-fp-watcher [kw func]
   (add-watch *current-fp kw
              (fn [_ _ _ new-val]
                (func new-val))))


(defn remove-current-fp-watcher [kw]
  (remove-watch *current-fp kw))


(defn get-user-profiles-dir []
  (let [user-home (System/getProperty "user.home")
        dir (io/file user-home user-profile-dir-name)]
    (when-not (.exists dir)
      (.mkdir dir))
    (.getAbsolutePath dir)))


(defn get-cur-fp-dir ^java.lang.String []
  (or (some-> (get-cur-fp) (io/file) .getParent str) (get-user-profiles-dir)))


(defn write-byte-array-to-file
  "Atomically replace file-path's contents (C3): write + fsync a temp file in
   the SAME directory (same-filesystem rename guarantee, incl. FAT32 USB
   sticks), then move it over the target with ATOMIC_MOVE, falling back to a
   plain replace where the filesystem cannot do atomic moves.  A crash or
   yanked stick mid-write leaves either the old or the new complete file —
   never a truncated one.  The temp name is non-.a7p so profile-names-in-dir
   never lists it.  Failures throw honestly (callers wrap in safe-exec!)."
  [^String file-path ^bytes byte-array]
  (let [target (.toPath (io/file file-path))
        parent (or (.getParent (.toAbsolutePath target))
                   (.toPath (io/file ".")))
        tmp (java.nio.file.Files/createTempFile
             parent "profedit-write-" ".tmp"
             (make-array java.nio.file.attribute.FileAttribute 0))]
    (try
      (with-open [fos (java.io.FileOutputStream. (.toFile tmp))]
        (.write fos byte-array)
        (.force (.getChannel fos) true))
      (try
        (java.nio.file.Files/move
         tmp target
         (into-array java.nio.file.CopyOption
                     [java.nio.file.StandardCopyOption/ATOMIC_MOVE
                      java.nio.file.StandardCopyOption/REPLACE_EXISTING]))
        (catch java.nio.file.AtomicMoveNotSupportedException _
          (java.nio.file.Files/move
           tmp target
           (into-array java.nio.file.CopyOption
                       [java.nio.file.StandardCopyOption/REPLACE_EXISTING]))))
      (catch Exception e
        ;; best-effort cleanup must never mask the original write failure
        (try (java.nio.file.Files/deleteIfExists tmp)
             (catch Exception _ nil))
        (throw e)))))


(defn read-byte-array-from-file [^String file-path]
  (let [^bytes bytes (try
                       (java.nio.file.Files/readAllBytes
                        (.toPath (io/file file-path)))
                       (catch java.io.IOException _
                         (throw (Exception.
                                 (str (j18n/resource ::bad-profile-file))))))]
    ;; Empty-file check is load-bearing: an empty array would surface later as
    ;; a NegativeArraySizeException inside proto-bin-deser.
    (if (pos? (alength bytes))
      bytes
      (throw (Exception. (str (j18n/resource ::bad-profile-file)))))))


(defn state->pld [state]
  (let [pld (select-keys state [:profile])]
    (if (s/valid? ::ros/pld pld)
      pld
      (do
        (ass/pop-report! (prof/val-explain ::ros/pld pld))
        (prof/status-err! ::error-reported)
        nil))))


(defn a7p-file? [^java.io.File file]
  (and (fs/file? file)
       (fs/readable? file)
       (string/ends-with? (string/lower-case (fs/base-name file)) ".a7p")))


(defn ensure-extension
  [^String file-path ^String extension]
  (let [ext (if (.startsWith extension ".")
              extension
              (str "." extension))]
    (if (.endsWith file-path ext)
      file-path
      (str file-path ext))))


(defn ascii-only-name?
  [^String file-path]
  (let [file (io/file file-path)
        name (.getName file)
        ascii-chars (filter #(<= (int %) 127) (.toCharArray name))]
    (= (count ascii-chars) (count (.toCharArray name)))))


(defn safe-exec! [fn & args]
  (try
    (apply fn args)
    (catch Exception e
      (let [err-msg (.getMessage e)]
        (prof/status-err! err-msg)
        (sc/invoke-now (sc/alert err-msg :type :error)))
      nil)))


(defn export! [*state file-path]
  (safe-exec!
   (fn []
     (let [full-fp (ensure-extension file-path ".json")]
       (when-let [pld (state->pld @*state)]
         (spit full-fp
               (ros/expr! ros/json-ser pld))
         full-fp)))))


(defn import! [*state file-path]
  (safe-exec!
   (fn []
     (let [str (slurp file-path)
           pld (ros/impr! ros/json-deser str)
           {:keys [profile]} pld]
       (when pld
         (swap! *state #(assoc % :profile profile))
         file-path)))))


(def *last-persisted-state
  "Snapshot of the app state as of the last successful load!/save! round-trip.
   Lets the dirty check (widgets/state-unsaved?) compare in memory instead of
   re-reading + proto-parsing the file on the EDT — which also re-fired the
   *current-fp watcher and leaked a coefficient-panel generation per dirty
   check (P3)."
  (atom nil))


(defn load! [*state file-path]
  (safe-exec!
   (fn []
     (let [bytes (read-byte-array-from-file file-path)
           pld (ros/impr! ros/proto-bin-deser bytes)
           {:keys [profile]} pld]
       (if pld
         (do
           ;; snapshot normalized to :profile only — *state may be the main
           ;; app atom ({:profile ...}) or the wizard atom (extra keys).
           (reset! *last-persisted-state
                   (select-keys (swap! *state #(assoc % :profile profile))
                                [:profile]))
           (reset! *current-fp file-path))
         (do (prof/status-err! (j18n/resource ::bad-profile-file))
             nil))))))


(defn side-load! [*st file-path]
  (safe-exec!
   (fn []
     (let [bytes (read-byte-array-from-file file-path)
           pld (ros/impr! ros/proto-bin-deser bytes)
           {:keys [profile]} pld]
       (if pld
         (swap! *st #(assoc % :profile profile))
         (do (prof/status-err! (j18n/resource ::bad-profile-file))
             nil))))))


(defn save! [*state file-path]
  (safe-exec!
   (fn []
     (when-let [full-fp (ensure-extension file-path ".a7p")]
       (if (ascii-only-name? full-fp)
         (when-let [pld (state->pld @*state)]
           (write-byte-array-to-file
            full-fp
            (ros/expr! ros/proto-bin-ser pld))
           ;; Round-trip validation re-load; also sets *current-fp (once).
           (load! *state full-fp)
           full-fp)
         (let [err-str ^String (j18n/resource ::err-non-ascii-file-name)]
           (throw (Exception. err-str))))))))


(defn read-config [filename]
  (try
    (let [contents (slurp (io/file filename))
          config (edn/read-string contents)]
      config)
    (catch Exception e
      (prof/status-err! (format (j18n/resource ::failed-to-read-conf-err)
                                (str (.getMessage e))))
      nil)))


(defn write-config [filename state]
  (try (with-open [writer (io/writer filename)]
         (pprint/write state :stream writer :pretty true)
         true)
       (catch Exception e
         (prof/status-err! (format (j18n/resource ::failed-to-write-conf-err)
                                   (str (.getMessage e))))
         nil)))


(defn get-config-file-path []
  (str (System/getProperty "user.home")
       java.io.File/separator
       ".profedit.edn"))


(defn windows? []
  (re-find #"(?i)win" (System/getProperty "os.name")))


(defn- extract-mount-point [fs-str]
  (if-let [m (re-find #"(^[^\s]+)" fs-str)]
    (first m)
    fs-str))


(defn get-mount-points []
  (if (windows?)
    (seq (.getRootDirectories (FileSystems/getDefault)))
    (let [file-stores (vec (jnio/get-file-stores (FileSystems/getDefault)))
          mount-points (map extract-mount-point (map str file-stores))]
      mount-points)))


(defn- profile-names-in-dir [^java.io.File dir]
  (into []
        (comp (filter #(string/ends-with? % ".a7p"))
              (map #(.getAbsolutePath ^java.io.File %)))
        (seq (.listFiles dir))))


(defn- device-dir-manifest [dir-path]
  (try
    (let [dir (io/file dir-path)
          subdirs ["profiles"]
          info-dir (io/file dir "info")
          info-files {"uuid" "uuid.txt", "info" "info.txt"}]

      (when (and (fs/directory? dir)
                 (every? #(fs/directory? (io/file dir %)) subdirs)
                 (fs/directory? info-dir)
                 (every? #(fs/exists? (io/file info-dir (val %))) info-files))

        (let [{:keys [serial_number
                      device_class
                      name_device
                      serial_core
                      serial_lrf
                      data_manufacture
                      firmware]} (-> (io/file info-dir "info.txt")
                                     slurp
                                     string/trim-newline
                                     (toml/read :keywordize))]
          {:uuid    (-> (io/file info-dir "uuid.txt")
                        slurp
                        string/trim-newline)
           :device name_device
           :serial serial_number
           :core-serial serial_core
           :lrf-serial serial_lrf
           :version firmware
           :data-manufacture data_manufacture
           :device-class (or device_class name_device)
           :path (.getAbsolutePath dir)
           :profiles (profile-names-in-dir (io/file dir "profiles"))})))
    (catch Exception _ nil)))


;; Manifest cache keyed by [info.txt mtime, profiles-dir mtime] — the polling
;; loop must not re-read + TOML-parse every device dir every tick. (P2)
(def ^:private *manifest-cache (atom {}))


(defn- cached-device-dir-manifest [dir-path]
  (let [dir (io/file dir-path)
        cache-key [(.lastModified (io/file dir "info" "info.txt"))
                   (.lastModified (io/file dir "profiles"))]
        entry (get @*manifest-cache (str dir-path))]
    (if (= (:key entry) cache-key)
      (:manifest entry)
      (let [m (device-dir-manifest dir-path)]
        (swap! *manifest-cache assoc (str dir-path) {:key cache-key
                                                     :manifest m})
        m))))


(defn profile-storages
  ([] (profile-storages (get-mount-points)))
  ([mount-points]
   (let [l-s-paths (->> [(get-user-profiles-dir) #_ (get-cur-fp-dir)]
                        (filter some?)
                        (distinct)
                        (map io/file))]
     (into #{}
           (concat
            (map #(hash-map :profiles (profile-names-in-dir %)
                            :path (.getAbsolutePath ^java.io.File %))
                 l-s-paths)
            (filter some? (pmap cached-device-dir-manifest mount-points)))))))


(def *profile-storages (atom (profile-storages)))


(defn- urlsafe-base64-decode [^String s]
  (let [decoder (Base64/getUrlDecoder)]
    (.decode decoder s)))


(defn- decode-name [encoded-name]
  (String. ^"[B" (urlsafe-base64-decode encoded-name)))


(defn- extract-main-dir [resource-path]
  (second (string/split resource-path #"/")))


(defn- extract-sub-dir [resource-path]
  (nth (string/split resource-path #"/") 2))


(defn- make-firmware-tree [resources]
  (let [grouped-by-main-dir (group-by extract-main-dir (keys resources))]
    (reduce (fn [acc main-dir]
              (let [decoded-main-dir (decode-name main-dir)
                    sub-dirs (grouped-by-main-dir main-dir)
                    sub-dir-map
                    (into {}
                          (map
                           (fn [sub-dir-path]
                             [(decode-name (extract-sub-dir sub-dir-path))
                              (get resources sub-dir-path)])
                           sub-dirs))]
                (assoc acc decoded-main-dir sub-dir-map)))
            {} (keys grouped-by-main-dir))))


;; resources/firmware is injected by CI; absent in dev checkouts — degrade to
;; an empty tree instead of failing at namespace load.
(def ^:private firmware-tree
  (make-firmware-tree (try (cp/resources "firmware")
                           (catch Exception _ {}))))


(defn- version-segments
  "Numeric version segments; nil/garbage segments become -1 so comparison
   never throws (firmware dirs may carry arbitrary version strings)."
  [v]
  (let [^String s (or v "")
        clean (if (.startsWith (.toLowerCase s) "v") (.substring s 1) s)]
    (mapv #(try (Long/parseLong ^String %)
                (catch NumberFormatException _ -1))
          (string/split clean #"\."))))


(defn- version-comparator [v1 v2]
  (compare (version-segments v2) (version-segments v1)))


(defn- get-newest-firmware [device-class]
  (let [firmware-versions (get firmware-tree device-class)
        highest-version (first (sort version-comparator
                                     (keys firmware-versions)))
        ;; :path is a VECTOR of URIs (cpath-clj); jar-scheme URIs return nil
        ;; from .getPath, so test the rendered URI string.
        path (get firmware-versions highest-version)
        uri (first path)]
    (when (and uri (string/ends-with? (str uri) "CS10.upg"))
      {:version highest-version :path path})))


(defn- matching-storages-with-newest-firmware [p-s]
  (let [dc-filter (filter (fn [s]
                            (contains? (set (keys firmware-tree))
                                       (:device-class s))))
        map-newest (map (fn [s]
                          (let [dc (:device-class s)
                                fw (get-newest-firmware dc)]
                            ;; fw may be nil (no matching firmware); without
                            ;; this guard one bad storage NPEs and silently
                            ;; kills notifications for the whole batch.
                            (when (and fw
                                       (pos? (version-comparator (:version s)
                                                                 (:version fw))))
                              (assoc s :newest-firmware fw)))))
        nil-filter (filter some?)]

    (into [] (comp dc-filter map-newest nil-filter) p-s)))


(def ^:private updater-interval-seconds 3)

(def ^:private mount-scan-timeout-ms 4000)


(defn- get-mount-points-timeboxed
  "get-mount-points can block indefinitely on a stale NFS/CIFS mount (the
   per-mount stat() hangs); time-box it and return nil on timeout so the tick
   is skipped instead of wedging the updater. (P2)"
  []
  (let [fut (future (doall (get-mount-points)))
        r (deref fut mount-scan-timeout-ms ::timeout)]
    (if (= r ::timeout)
      (do (future-cancel fut) nil)
      r)))


(defn- updater-tick!
  "One polling tick.  The whole body is inside try: an exception must skip the
   tick, never kill the schedule (the old loop sat OUTSIDE its try and died
   silently on the first scan error).  UI-bound atoms are reset via
   invoke-later (composes with the notify-later chains)."
  [firmware-up-callback *previous]
  (try
    (when-let [mounts (get-mount-points-timeboxed)]
      (let [current-value (profile-storages mounts)
            p-v @*previous
            new-storages (difference current-value p-v)]
        (when-not (= p-v current-value)
          (reset! *previous current-value)
          (sc/invoke-later (reset! *profile-storages current-value)))
        (when (seq new-storages)
          (try
            (run! firmware-up-callback
                  (matching-storages-with-newest-firmware new-storages))
            (catch Exception _ nil)))))
    ;; Invalidate *current-fp ONLY when a loaded file disappeared.  The old
    ;; guard re-nilled a nil fp every tick ((fs/readable? nil) is false),
    ;; firing the fp-watcher -> coefficient-panel rebuild at 1 Hz.
    (let [cur-fp @*current-fp]
      (when (and cur-fp (not (fs/readable? cur-fp)))
        (sc/invoke-later (reset! *current-fp nil))))
    (catch Exception _ nil)))


(defonce ^:private *updater-executor (atom nil))


(defn start-file-tree-updater-thread [firmware-up-cb]
  (when (nil? @*updater-executor)
    (let [*previous (atom @*profile-storages)
          executor (java.util.concurrent.Executors/newSingleThreadScheduledExecutor
                    (reify java.util.concurrent.ThreadFactory
                      (newThread [_ r]
                        (doto (Thread. ^Runnable r "file-tree-updater")
                          (.setDaemon true)))))]
      ;; initial firmware sweep over everything already known (old behavior)
      (.execute executor
                ^Runnable
                (fn []
                  (try
                    (run! firmware-up-cb
                          (matching-storages-with-newest-firmware @*previous))
                    (catch Exception _ nil))))
      (.scheduleWithFixedDelay
       executor
       ^Runnable (fn [] (updater-tick! firmware-up-cb *previous))
       (long updater-interval-seconds)
       (long updater-interval-seconds)
       java.util.concurrent.TimeUnit/SECONDS)
      (reset! *updater-executor executor))))


(defn stop-file-tree-updater-thread []
  (when-let [^java.util.concurrent.ScheduledExecutorService e @*updater-executor]
    (.shutdownNow e)
    (reset! *updater-executor nil)))


(defn copy-newest-firmware [entry]
  (let [resource-uri (->> entry
                          :newest-firmware
                          :path
                          first)
        resource-url (.toURL ^java.net.URI resource-uri)
        target-dir (-> entry :path io/file)
        target (io/file target-dir "CS10.upg")]
    (if (and (.exists target-dir)
             (.isDirectory target-dir)
             (.canWrite target-dir))
      (with-open [^java.io.InputStream in-stream
                  (.openStream ^java.net.URL resource-url)]
        (io/copy in-stream target))
      (throw (ex-info (format "Can't write to %s" target-dir) {})))))


(defn load-from-fp! [*state ^String fp]
  (when (load! *state fp)
    (prof/status-ok! (format (j18n/resource ::loaded-from) (str fp)))))


(defn load-from! [*state ^java.io.File file]
  (let [fp (.getAbsolutePath file)]
    (load-from-fp! *state fp)))
