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


(def ^:private max-retries 10)

(def ^:private timeout-duration 1000)

(defn write-byte-array-to-file
  [^String file-path ^bytes byte-array]
  (let [write-op (fn []
                   (try
                     (let [output-stream (java.io.FileOutputStream. file-path)]
                       (try
                         (when (Thread/interrupted)
                           (throw (InterruptedException. "Thread interrupted before write")))
                         (.write output-stream byte-array)
                         (finally
                           (.close output-stream))))
                     (catch InterruptedException _)))

        execute-with-timeout
        (fn [op ms]
          (let [task (java.util.concurrent.FutureTask. op)
                thr (Thread. task)]
            (try
              (.start thr)
              (.get task ms java.util.concurrent.TimeUnit/MILLISECONDS)
              (catch java.util.concurrent.TimeoutException _
                (.cancel task true)
                (when (.isAlive thr)
                  (.interrupt thr))
                (throw (java.util.concurrent.TimeoutException.
                        (j18n/resource ::io-write-timeout))))
              (catch Exception e
                (.cancel task true)
                (when (.isAlive thr)
                  (.interrupt thr))
                (throw e)))))]
    (loop [retries max-retries]
      (let [result (try
                     (execute-with-timeout write-op timeout-duration)
                     :success
                     (catch java.util.concurrent.TimeoutException e
                       (if-not (pos? retries)
                         (throw e)
                         :retry)))]
        (when (= result :retry)
          (recur (dec retries)))))))


(defn read-byte-array-from-file [^String file-path]
  (let [file (io/file file-path)
        length (int (.length file))]
    (if (> length 0)
      (let [byte-array (byte-array length)
            input-stream (java.io.FileInputStream. file-path)]
        (.read input-stream byte-array)
        (.close input-stream)
        byte-array)
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
           (load! *state file-path)
           (reset! *current-fp full-fp))
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


(defn profile-storages []
  (let [l-s-paths (->> [(get-user-profiles-dir) #_ (get-cur-fp-dir)]
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


(def ^:private firmware-tree (make-firmware-tree (cp/resources "firmware")))


(defn- version-comparator [^String v1 ^String v2]
  (let [clean-version (fn [^String v]
                        (if (.startsWith (.toLowerCase v) "v")
                          (.substring v 1)
                          v))
        segments1 (mapv #(Integer. ^String %)
                        (string/split (clean-version v1) #"\."))
        segments2 (mapv #(Integer. ^String %)
                        (string/split (clean-version v2) #"\."))]
    (compare segments2 segments1)))


(defn- get-newest-firmware [device-class]
  (let [firmware-versions (get firmware-tree device-class)
        highest-version (first (sort version-comparator
                                     (keys firmware-versions)))
        path ^java.net.URI (get firmware-versions highest-version)]
    (if (and path #(string/ends-with? (.getPath path) "CS10.upg"))
      {:version highest-version :path path}
      nil)))


(defn- matching-storages-with-newest-firmware [p-s]
  (let [dc-filter (filter (fn [s]
                            (contains? (set (keys firmware-tree))
                                       (:device-class s))))
        map-newest (map (fn [s]
                          (let [dc (:device-class s)
                                fw (get-newest-firmware dc)]
                            (when (> (version-comparator (:version s)
                                                         (:version fw))
                                     0)
                              (assoc s :newest-firmware fw)))))
        nil-filter (filter some?)]

    (into [] (comp dc-filter map-newest nil-filter) p-s)))


(defn- update-profile-storages [firmware-up-callback]
  (let [p-s (profile-storages)
        *previous-value (volatile! p-s)]
    (try
      (run! firmware-up-callback
            (matching-storages-with-newest-firmware p-s))
      (catch Exception _ nil))
    (loop []
      (let [current-value (profile-storages)
            p-v @*previous-value
            new-storages (difference current-value p-v)]
        (when-not (= p-v current-value)
          (reset! *profile-storages current-value)
          (vreset! *previous-value current-value))
        (let [cur-fp @*current-fp]
          (when-not (fs/readable? cur-fp)
            (reset! *current-fp nil)))
        (when (seq new-storages)
          (try
            (run! firmware-up-callback
                  (matching-storages-with-newest-firmware new-storages))
            (catch Exception _ nil)))
        (Thread/sleep 1000)
        (recur)))))


(defonce ^:private *updater-thread-atom (atom nil))


(defn start-file-tree-updater-thread [firmware-up-cb]
  (when (or (nil? @*updater-thread-atom)
            (not (.isAlive ^Thread @*updater-thread-atom)))
    (let [t (Thread. ^Runnable (partial update-profile-storages firmware-up-cb))]
      (.start t)
      (reset! *updater-thread-atom t))))


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
