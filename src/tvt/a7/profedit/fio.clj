(ns tvt.a7.profedit.fio
  (:require
   [clojure.java.io :as io]
   [tvt.a7.profedit.rosetta :as ros]
   [tvt.a7.profedit.asi :as ass]
   [tvt.a7.profedit.profile :as prof]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [jdk.nio.file.FileSystem :as jnio]
   [j18n.core :as j18n]
   [me.raynes.fs :as fs]
   [toml.core :as toml]
   [clojure.spec.alpha :as s])
  (:import [java.nio.file FileSystems]
           [java.lang Thread]))


(def *current-fp (atom nil))


(defn get-cur-fp ^java.lang.String [] (deref *current-fp))


(def ^:private user-profile-dir-name "archer_bc2_profiles")


(defn get-user-profiles-dir []
  (let [user-home (System/getProperty "user.home")
        dir (io/file user-home user-profile-dir-name)]
    (when-not (.exists dir)
      (.mkdir dir))
    (.getAbsolutePath dir)))


(defn get-cur-fp-dir ^java.lang.String []
  (or (some-> (get-cur-fp) (io/file) .getParent str) (get-user-profiles-dir)))


(defn write-byte-array-to-file [^String file-path ^bytes byte-array]
  (let [output-stream (java.io.FileOutputStream. file-path)]
    (.write output-stream byte-array)
    (.close output-stream)))


(defn read-byte-array-from-file [^String file-path]
  (let [file (io/file file-path)
        length (.length file)
        byte-array (byte-array (int length))
        input-stream (java.io.FileInputStream. file-path)]
    (.read input-stream byte-array)
    (.close input-stream)
    byte-array))


(defn- state->pld [state]
  (let [pld (select-keys state [:profile])]
    (if (s/valid? ::ros/pld pld)
      pld
      (do
        (ass/pop-report! (prof/val-explain ::ros/pld pld))
        (prof/status-err! ::error-reported)
        nil))))


(defn- ensure-extension
  [^String file-path ^String extension]
  (let [ext (if (.startsWith extension ".")
              extension
              (str "." extension))]
    (if (.endsWith file-path ext)
      file-path
      (str file-path ext))))


(defn- ascii-only-name?
  [^String file-path]
  (let [file (io/file file-path)
        name (.getName file)
        ascii-chars (filter #(<= (int %) 127) (.toCharArray name))]
    (= (count ascii-chars) (count (.toCharArray name)))))


(defn- safe-exec! [fn & args]
  (try
    (apply fn args)
    (catch Exception e (prof/status-err! (.getMessage e)) nil)))


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


(defn load! [*state file-path]
  (safe-exec!
   (fn []
     (let [bytes (read-byte-array-from-file file-path)
           pld (ros/impr! ros/proto-bin-deser bytes)
           {:keys [profile]} pld]
       (if pld
         (do
           (swap! *state #(assoc % :profile profile))
           (reset! *current-fp file-path))
         (do (prof/status-err! (j18n/resource ::bad-profile-file))
             nil))))))


(defn side-load! [*state file-path]
  (safe-exec!
   (fn []
     (let [bytes (read-byte-array-from-file file-path)
           pld (ros/impr! ros/proto-bin-deser bytes)
           {:keys [profile]} pld]
       (if pld
         (swap! *state #(assoc % :profile profile))
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
           (load! *state file-path)
           (reset! *current-fp full-fp))
         (throw (Exception.
                 ^String (j18n/resource ::err-non-ascii-file-name))))))))


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
                      name_device
                      firmware]} (-> (io/file info-dir "info.txt")
                                     slurp
                                     string/trim-newline
                                     (toml/read :keywordize))]
          {:uuid    (-> (io/file info-dir "uuid.txt")
                        slurp
                        string/trim-newline)
           :device name_device
           :serial serial_number
           :version firmware
           :path (.getAbsolutePath dir)
           :profiles (profile-names-in-dir (io/file dir "profiles"))})))
    (catch Exception _ nil)))


(defn- profile-storages []
  (let [l-s-paths (->> [(get-user-profiles-dir) #_(get-cur-fp-dir)]
                       (filter some?)
                       (distinct)
                       (map io/file))]
    (into #{}
          (concat
           (map #(hash-map :profiles (profile-names-in-dir %)
                           :path (.getAbsolutePath ^java.io.File %))
                l-s-paths)
           (filter some? (pmap device-dir-manifest (get-mount-points)))))))


(def *profile-storages (atom (profile-storages)))


(defn- update-profile-storages []
  (let [previous-value (volatile! (profile-storages))]
    (loop []
      (let [current-value (profile-storages)]
        (when (not= @previous-value current-value)
          (reset! *profile-storages current-value)
          (vreset! previous-value current-value))
        (Thread/sleep 1000)
        (recur)))))


(defonce ^:private *updater-thread-atom (atom nil))


(defn start-file-tree-updater-thread []
  (when (or (nil? @*updater-thread-atom)
            (not (.isAlive ^Thread @*updater-thread-atom)))
    (let [t (Thread. update-profile-storages)]
      (.start t)
      (reset! *updater-thread-atom t))))
