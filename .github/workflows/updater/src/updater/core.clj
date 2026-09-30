(ns updater.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.awt BorderLayout Font]
           [java.lang Thread]
           [javax.swing JFrame JOptionPane JScrollPane JTextArea SwingUtilities]
           [javax.swing.border EmptyBorder])
  (:gen-class))


(defn create-window []
  (let [message-label (doto (JTextArea. 20 0)
                        (.setFont (Font. "Arial" Font/BOLD 24))
                        (.setLineWrap true)
                        (.setWrapStyleWord true)
                        (.setEditable false))
        scroll-pane (doto (JScrollPane. message-label)
                      (.setBorder (EmptyBorder. 10 10 10 10)))
        frame (doto (JFrame. "Update Status")
                (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
                (.setSize 600 800))]
    (.add (.getContentPane frame) scroll-pane BorderLayout/CENTER)
    (.setVisible frame true)
    message-label))


(def default-release-repository "JAremko/ArcherBC2")


(def release-repository
  ;; CI rewrites this resource to github.repository before compiling the
  ;; updater. The fallback keeps direct/local builds compatible with the
  ;; original upstream repository.
  (or (some-> (io/resource "release-repository.txt") slurp string/trim not-empty)
      default-release-repository))


(def releases-url (str "https://github.com/" release-repository "/releases"))


(def fail-string (str "Update failed! Please download latest "
                      "ArcherBC2_install.exe and reinstall the program:\n"
                      releases-url "/latest/download/ArcherBC2_install.exe"))


(def download-url (str releases-url "/latest/download/profedit.jar"))


(def checksum-url (str download-url ".sha256"))


(def ^:private http-timeouts
  ;; U1: without timeouts a stalled connection hangs the updater forever.
  {:socket-timeout 15000 :conn-timeout 10000})


(defn- sha256-hex [^String path]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        buf (byte-array 65536)]
    (with-open [in (io/input-stream path)]
      (loop []
        (let [n (.read in buf)]
          (when (pos? n)
            (.update md buf 0 n)
            (recur)))))
    (format "%064x" (java.math.BigInteger. 1 (.digest md)))))


(defn- fetch-expected-sha256
  "Only a confirmed missing asset permits the legacy no-checksum fallback."
  []
  (let [{:keys [status body]}
        (client/get checksum-url (assoc http-timeouts :throw-exceptions false))]
    (cond
      (= status 404) nil
      (= status 200) (let [digest (first (string/split (string/trim (or body "")) #"\s+"))]
            (if (and digest (re-matches #"[0-9a-fA-F]{64}" digest))
              (string/lower-case digest)
              (throw (ex-info "Malformed release checksum" {}))))
      :else (throw (ex-info "Unable to fetch release checksum" {:status status})))))


(defn- invoke-later [f]
  (SwingUtilities/invokeLater
   (reify Runnable
     (run [_] (f)))))


(defn- invoke-now [f]
  (if (SwingUtilities/isEventDispatchThread)
    (f)
    (SwingUtilities/invokeAndWait
     (reify Runnable
       (run [_] (f))))))


(defn msg [^JTextArea message-label ^String message]
  (invoke-later #(.setText message-label message)))


(defn- show-warning! [^String message]
  (invoke-now
   #(JOptionPane/showMessageDialog nil message "Update failed"
                                   JOptionPane/WARNING_MESSAGE)))


(def max-retries 5)

(def retry-delay-ms 1000)

(defn with-retries [fn-to-retry & args]
  (loop [attempt 1]
    (let [result (try
                   [(apply fn-to-retry args) nil]
                   (catch Exception e
                     [nil e]))
          res-val (first result)
          excep (second result)]
      (if excep
        (if (< attempt max-retries)
          (do
            (Thread/sleep (long retry-delay-ms))
            (recur (inc attempt)))
          (throw excep))
        res-val))))


(defn download-attempt [^String url ^String filename]
  (let [response (client/get url (assoc http-timeouts :as :stream))]
    (if (= 200 (:status response))
      (with-open [^java.io.InputStream in (:body response)
                  out (io/output-stream filename)]
        (io/copy in out))
      (do (when-let [^java.io.InputStream body (:body response)]
            (.close body))
          (throw (Exception. "Failed to download"))))))


(defn download-file [^String url ^String filename]
  (with-retries download-attempt url filename))


(defn rename-file [^String old-name ^String new-name]
  (.renameTo (io/file old-name) (io/file new-name)))


(defn rename-loop [message-label ^String old-name ^String new-name]
  (when-not (rename-file old-name new-name)
    (msg message-label "App is still running. Waiting...")
    (Thread/sleep 2000)
    (recur message-label old-name new-name)))


(defn delete-file [^String filename]
  (when (.exists (io/file filename))
    (.delete (io/file filename))))


(defn move-file [^String old-name ^String new-name]
  (io/copy (io/file old-name) (io/file new-name) :replace true)
  (delete-file old-name))


(defn start-app [^String jar-name]
  (let [runtime (Runtime/getRuntime)
        cmd (str "./runtime/bin/java -Xms350m -DupdateUpdater=true -jar " jar-name)]
    (.exec runtime cmd)
    (System/exit 0)))


(def backup-jar-name "profedit.jar.bak")


(defn- verify-download! [say filename]
  (say "Verifying download checksum...")
  (if-let [expected (with-retries fetch-expected-sha256)]
    (if (= expected (sha256-hex filename))
      (say "Checksum OK.")
      (throw (ex-info "Checksum MISMATCH — update rejected." {})))
    (say "No published checksum found; skipping verification.")))


(defn updater [message-label]
  (let [say (partial msg message-label)
        jar-name "profedit.jar"
        new-jar-name "profedit-new.jar"]
    (say "Updating...")
    (say "Downloading the update file...")
    ;; Installation depends on successful verification, never on whether a
    ;; rejected temporary file could be deleted (e.g. an antivirus lock).
    (when (try
            (download-file download-url new-jar-name)
            (verify-download! say new-jar-name)
            true
            (catch Exception e
              (delete-file new-jar-name)
              (show-warning! (str (.getMessage e) " " fail-string))
              (start-app jar-name)
              false))
      (say "Update downloaded successfully.")
      (when (.exists (io/file jar-name))
        (when (and (.exists (io/file backup-jar-name))
                   (not (delete-file backup-jar-name)))
          (throw (ex-info "Unable to replace the previous backup" {})))
        (say "Waiting for the application to close...")
        (rename-loop message-label jar-name backup-jar-name)
        (say "Application closed, proceeding with the update.")
        (delete-file jar-name))
      (move-file new-jar-name jar-name)
      (say "Update completed!")
      (start-app "profedit.jar"))))


(defn -main [& _]
  (invoke-later
   #(let [message-label (create-window)]
      (future
        (try (updater message-label)
             (catch Exception e
               (let [reason (.getMessage e)]
                 (msg message-label (str reason "\n\n\n" fail-string)))))))))
