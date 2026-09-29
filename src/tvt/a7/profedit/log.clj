(ns tvt.a7.profedit.log
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io File]
           [java.util Locale]
           [java.util.logging FileHandler Level Logger SimpleFormatter]
           [loggingutil FileHandlerFactory]
           [java.lang Thread$UncaughtExceptionHandler]))


(def ^:private app-name "ArcherBC2")


(def ^:private ^Logger logger
  (Logger/getLogger "tvt.a7.profedit"))


(defonce ^:private *initialized? (atom false))


(defonce ^:private *file-handler (atom nil))


(defn log-directory
  "Returns the platform-appropriate directory for persistent application logs.

  The three-argument arity is kept pure so path selection can be tested without
  changing process properties or environment variables."
  ([]
   (log-directory (System/getProperty "os.name")
                  (System/getProperty "user.home")
                  (System/getenv)))
  ([os-name user-home environment]
   (let [os-name (string/lower-case (or os-name ""))
         user-home (or user-home ".")]
     (cond
       (string/includes? os-name "win")
       (io/file (or (not-empty (get environment "LOCALAPPDATA"))
                    (str (io/file user-home "AppData" "Local")))
                app-name
                "logs")

       (string/includes? os-name "mac")
       (io/file user-home "Library" "Logs" app-name)

       :else
       (io/file (or (not-empty (get environment "XDG_STATE_HOME"))
                    (str (io/file user-home ".local" "state")))
                "archerbc2"
                "logs")))))


(defn log-path-pattern []
  (.getAbsolutePath (io/file (log-directory) "archerbc2-%g.log")))


(defn- ^String event-message [event data]
  (str event (when (some? data) (str " " (pr-str data)))))


(defn debug!
  ([event] (debug! event nil))
  ([event data]
   (.log logger Level/FINE (event-message event data))))


(defn info!
  ([event] (info! event nil))
  ([event data]
   (.log logger Level/INFO (event-message event data))))


(defn warn!
  ([event] (warn! event nil))
  ([event data]
   (.log logger Level/WARNING (event-message event data))))


(defn error!
  ([event throwable] (error! event throwable nil))
  ([event ^Throwable throwable data]
   (.log logger Level/SEVERE (event-message event data) throwable)))


(defn- install-uncaught-exception-handler! []
  (let [previous-handler (Thread/getDefaultUncaughtExceptionHandler)]
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread throwable]
         (error! "uncaught-exception"
                 throwable
                 {:thread (.getName ^Thread thread)})
         (when previous-handler
           (.uncaughtException ^Thread$UncaughtExceptionHandler
                               previous-handler
                               thread
                               throwable)))))))


(defn- ^File writable-log-directory []
  (let [preferred (log-directory)
        fallback (io/file (System/getProperty "java.io.tmpdir") app-name "logs")]
    (or (some (fn [^File directory]
                (when (and (or (.isDirectory directory) (.mkdirs directory))
                           (.canWrite directory))
                  directory))
              [preferred fallback])
        (throw (Exception. "Unable to create an application log directory")))))


(defn initialize!
  "Enables console logging and a small rotating persistent log. Logging setup
  failures are reported to stderr and never prevent the application starting."
  []
  (locking *initialized?
    (when-not @*initialized?
      (try
        (let [directory (writable-log-directory)
              pattern (.getAbsolutePath (io/file directory "archerbc2-%g.log"))
              ;; A tiny Java factory selects the Java 8 int-limit overload
              ;; unambiguously even when this namespace is compiled on a newer
              ;; JDK that also exposes a long-limit overload.
              handler (FileHandlerFactory/createRotating
                       pattern (int (* 1024 1024)) (int 3) true)]
          (.setFormatter handler (SimpleFormatter.))
          (.setLevel handler Level/ALL)
          (.setLevel logger Level/ALL)
          ;; Keep the parent console handler: `java -jar` remains useful while
          ;; the GUI Windows launcher still gets a persistent file log.
          (.setUseParentHandlers logger true)
          (.addHandler logger handler)
          (reset! *file-handler handler)
          (install-uncaught-exception-handler!)
          (reset! *initialized? true)
          (info! "logging-initialized"
                 {:directory (.getAbsolutePath directory)
                  :pattern pattern
                  :os (System/getProperty "os.name")
                  :java (System/getProperty "java.version")
                  :locale (str (Locale/getDefault))}))
        (catch Throwable throwable
          (binding [*out* *err*]
            (println "ArcherBC2 could not initialize persistent logging:"
                     (.getMessage throwable)))))))
  @*initialized?)
