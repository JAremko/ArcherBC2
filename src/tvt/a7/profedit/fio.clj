(ns tvt.a7.profedit.fio
  (:require
   [clojure.java.io :as io]
   [tvt.a7.profedit.rosetta :as ros]
   [tvt.a7.profedit.asi :as ass]
   [tvt.a7.profedit.profile :as prof]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
   [j18n.core :as j18n]
   [clojure.spec.alpha :as s]))

(def *current-fp (atom nil))

(defn get-cur-fp ^java.lang.String [] (deref *current-fp))

(def ^:private user-profile-dir-name "archer_bc2_profiles")


(defn get-user-profiles-dir []
  (let [user-home (System/getProperty "user.home")
        dir (java.io.File. ^String user-home ^String user-profile-dir-name)]
    (when-not (.exists dir)
      (.mkdir dir))
    (.getAbsolutePath dir)))


(defn get-cur-fp-dir ^java.lang.String []
  (or (get-user-profiles-dir) (some-> (get-cur-fp) (io/file) .getParent str)))


(defn write-byte-array-to-file [^String file-path ^bytes byte-array]
  (let [output-stream (java.io.FileOutputStream. file-path)]
    (.write output-stream byte-array)
    (.close output-stream)))


(defn read-byte-array-from-file [^String file-path]
  (let [file (java.io.File. file-path)
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
  (let [file (java.io.File. file-path)
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
