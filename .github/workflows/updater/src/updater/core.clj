(ns updater.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io])
  (:gen-class))


(defn download-file [^String url ^String filename]
  (let [response (client/get url {:as :stream :debug false})]
    (when (= 200 (:status response))
      (with-open [out (io/output-stream filename)]
        (io/copy (:body response) out)))))


(defn rename-file [^String old-name ^String new-name]
  (.renameTo (io/file old-name) (io/file new-name)))


(defn rename-loop [^String old-name ^String new-name]
  (when-not (rename-file old-name new-name)
    (println "App is still running. Waiting...")
    (Thread/sleep 2000)
    (recur old-name new-name)))


(defn delete-file [^String filename]
  (when (.exists (io/file filename))
    (.delete (io/file filename))))


(defn move-file [^String old-name ^String new-name]
  (io/copy (io/file old-name) (io/file new-name) :replace true)
  (delete-file old-name))


(defn start-app [^String filename]
  (let [runtime (Runtime/getRuntime)]
    (.exec runtime filename)
    (System/exit 0)))


(defn updater []
  (println "Updating...")
  (let [download-url "https://github.com/JAremko/profedit/releases/latest/download/profedit.jar"
        jar-name "profedit.jar"
        new-jar-name "profedit-new.jar"
        backup-jar-name "profedit.jar.bak"]
    (when (.exists (io/file backup-jar-name))
      (println "Deleting old backup file...")
      (delete-file backup-jar-name))

    (download-file download-url new-jar-name)
    (if (.exists (io/file new-jar-name))
      (do
        (println "Update downloaded successfully.")
        (when (.exists (io/file jar-name))
          (println "Waiting for the application to close...")
          (rename-loop jar-name backup-jar-name)
          (println "Application closed, proceeding with the update.")
          (delete-file jar-name))
        (move-file new-jar-name jar-name)
        (println "Update completed!")
        (start-app "profedit.exe"))
      (do
        (println "Update failed! Please manually download the new version from the following URL:")
        (println "https://github.com/JAremko/profedit/releases")
        (when (.exists (io/file backup-jar-name))
          (rename-loop backup-jar-name jar-name))))))


(defn -main [& args]
  (updater))
