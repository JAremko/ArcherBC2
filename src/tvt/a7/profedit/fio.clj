(ns tvt.a7.profedit.fio
  (:require
   [clojure.java.io :as io]
   [tvt.a7.profedit.rosetta :as ros]
   [tvt.a7.profedit.asi :as ass]
   [tvt.a7.profedit.profile :as prof]
   [clojure.pprint :as pprint]
   [clojure.string :refer [split]]
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [j18n.core :as j18n]))


(def ^:private current-fp* (atom nil))


(defn set-cur-fp! [fp] (reset! current-fp* fp))


(defn get-cur-fp [] (deref current-fp*))


(defn generate-unique-filename [base-filename]
  (let [home-dir (System/getProperty "user.home")
        ext (split base-filename #"\.")
        file-name (first ext)
        extension (last ext)]
    (loop [i 1]
      (let [new-filename (str file-name " (" i ")." extension)
            full-path (str home-dir java.io.File/separator new-filename)]
        (if (.exists (java.io.File. full-path))
          (recur (inc i))
          full-path)))))


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


(defn get-file-extension [^java.io.File file]
  (let [file-name (.getName file)
        last-dot-index (.lastIndexOf file-name ".")]
    (if (and (not= last-dot-index -1)
             (< last-dot-index (dec (.length file-name))))
      (.substring file-name (inc last-dot-index))
      "")))


(defn- state->pld [state]
  (let [pld (select-keys state [:profile])]
    (if (s/valid? ::ros/pld pld)
      pld
      (do
        (ass/pop-report! (prof/val-explain ::ros/pld pld))
        (prof/status-err! ::error-reported)
        nil))))


(defn- check-and-add-extension
  [^String file-path ^String extension]
  (let [ext (if (.startsWith extension ".")
              extension
              (str "." extension))]
    (if (.endsWith file-path ext)
      file-path
      (str file-path ext))))


(defn- safe-exec! [fn & args]
  (try
    (apply fn args)
    (catch Exception e (prof/status-err! (.getMessage e)) nil)))


(defn export! [*state file-path]
  (safe-exec!
   (fn []
     (let [full-fp (check-and-add-extension file-path ".json")]
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


(defn save! [*state file-path]
  (safe-exec!
   (fn []
     (let [full-fp (check-and-add-extension file-path ".a7p")]
       (when-let [pld (state->pld @*state)]
         (write-byte-array-to-file
          full-fp
          (ros/expr! ros/proto-bin-ser pld))
         (set-cur-fp! full-fp)
         full-fp)))))


(defn load! [*state file-path]
  (safe-exec!
   (fn []
     (let [bytes (read-byte-array-from-file file-path)
           pld (ros/impr! ros/proto-bin-deser bytes)
           {:keys [profile]} pld]
       (if pld
         (do
           (swap! *state #(assoc % :profile profile))
           (set-cur-fp! file-path))
         (do (prof/status-err! (j18n/resource ::bad-profile-file))
             nil))))))


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
