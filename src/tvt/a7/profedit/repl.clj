(ns tvt.a7.profedit.repl
  (:require
   [tvt.a7.profedit.app :as app]
   [tvt.a7.profedit.rosetta :as ros]
   [tvt.a7.profedit.fio :as fio]
   [seesaw.core :as sc]))

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
   (let [full-fp (fio/ensure-extension file-path ".json")
         pld (select-keys @app/*pa [:profile])]
     (spit full-fp (ros/json-ser pld))
     full-fp)))


(defn p-import [file-path]
  (sc/invoke-now
   (let [str (slurp file-path)
         pld (ros/json-deser str)
         {:keys [profile]} pld]
     (when pld
       (swap! app/*pa #(assoc % :profile profile))
       file-path))))


(defn p-load [file-path]
  ;; Delegates to fio/load! — same validation, error reporting, atomic-write
  ;; counterpart, and *current-fp/snapshot bookkeeping as the UI paths.
  (sc/invoke-now (fio/load! app/*pa file-path)))


(defn p-save [file-path]
  (sc/invoke-now (fio/save! app/*pa file-path)))


(defn p-state [] @app/*pa)
