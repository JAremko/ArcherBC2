(ns tvt.a7.profedit.repl
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.app :as app]
   [tvt.a7.profedit.rosetta :as ros]
   [tvt.a7.profedit.fio :as fio]
   [seesaw.core :as sc]
   [j18n.core :as j18n]))

(defn p-help []
  (println "State")
  (println "  - p-state: Prints out current app state")
  (println "  You can modify it by swapping app/*pa atom (see clojure docs),")
  (println "  dont forget to import it from tvt.a7.profedit.app first!")
  (println "File Export and Import Functions:")
  (println "  - p-export [file-path]: Exports the current state to a JSON file at the specified file-path.")
  (println "  - p-import [file-path]: Imports state from a JSON file located at the specified file-path.")

  (println "\nFile Load and Save Functions:")
  (println "  - p-load [file-path]: Loads state from a binary file located at the specified file-path.")
  (println "  - p-save [file-path]: Saves the current state to a binary file at the specified file-path.")
  (println "    Note: The file-path must only contain ASCII characters."))

(defn p-export [file-path]
  (sc/invoke-now
   (let [full-fp (fio/ensure-extension file-path ".json")]
     (when-let [pld (select-keys @app/*pa [:profile])]
       (spit full-fp (ros/json-ser pld)) full-fp))))


(defn p-import [file-path]
  (sc/invoke-now
   (let [str (slurp file-path)
         pld (ros/json-deser str)
         {:keys [profile]} pld]
     (when pld
       (swap! app/*pa #(assoc % :profile profile))
       file-path))))


(defn p-load [file-path]
  (sc/invoke-now
   (let [bytes (fio/read-byte-array-from-file file-path)
         pld (ros/proto-bin-deser bytes)
         {:keys [profile]} pld]
     (if pld
       (do
         (swap! app/*pa #(assoc % :profile profile))
         (reset! fio/*current-fp file-path))
       (do (prof/status-err! (j18n/resource ::bad-profile-file))
           nil)))))


(defn- write-byte-array-to-file
  [^String file-path ^bytes byte-array]
  (with-open [output-stream (java.io.FileOutputStream. file-path)]
    (.write output-stream byte-array)))


(defn p-save [file-path]
  (sc/invoke-now
   (when-let [full-fp (fio/ensure-extension file-path ".a7p")]
     (if (fio/ascii-only-name? full-fp)
       (when-let [pld (select-keys @app/*pa [:profile])]
         (write-byte-array-to-file
          full-fp
          (ros/proto-bin-ser pld))
         (p-load file-path)
         (reset! fio/*current-fp full-fp))
       (let [err-str ^String (j18n/resource ::err-non-ascii-file-name)]
         (throw (Exception. err-str)))))))


(defn p-state [] @app/*pa)
