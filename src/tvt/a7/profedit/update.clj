
(ns tvt.a7.profedit.update
  (:require [clj-http.client :as client]
            [tvt.a7.profedit.profile :as prof]
            [clojure.java.io :as io]
            [seesaw.core :as ssc]
            [tvt.a7.profedit.config :refer [update-conf]]
            [j18n.core :as j18n])
  (:import [java.io BufferedReader InputStreamReader]
           [java.lang ProcessBuilder String]))


(def ^:private windows-script-path
  (:windows-script-path update-conf))

(def ^:private linux-script-path
  (:linux-script-path update-conf))

(def ^:private github-api-url
  (:github-api-url update-conf))

(def ^:private version-resource-path
  (:version-resource-path update-conf))

(def ^:private download-url-pattern
  (:download-url-pattern update-conf))


(defn- get-latest-tag []
  (let [response (client/get github-api-url {:as :json})
        latest-tag (first (:body response))]
    (:name latest-tag)))


(defn- get-current-version []
  (let [version-resource (io/resource version-resource-path)]
    (when version-resource
      (with-open [reader (BufferedReader.
                           (InputStreamReader.
                             (.openStream version-resource)))]
        (.readLine reader)))))


(defn- update-app [download-url script-path]
  (let [file (io/file script-path)
        os-name (System/getProperty "os.name")
        cmd (if (= os-name "Windows") "cmd" "/bin/bash")
        arg (if (= os-name "Windows") "/c" script-path)
        ^"[Ljava.lang.String;" cmd-array (into-array String [cmd
                                                             arg
                                                             download-url])]
    (when (.exists file)
      (.setExecutable file true)
      (let [pb (java.lang.ProcessBuilder. cmd-array)]
        (.start pb)))))


(defn- ask-to-update [frame]
  (ssc/confirm frame
               (j18n/resource ::ask-if-should-update)
               :title (j18n/resource ::software-update)
               :type :info
               :option-type :yes-no))


(defn check-for-update [frame]
  (try
    (when-let [current-version (seq (get-current-version))]
      (let [latest-version (get-latest-tag)]
        (when-not (= latest-version current-version)
          (when (ask-to-update frame)
            (let [download-url
                  (format download-url-pattern latest-version)]
              (println "Update available, starting the update process.")
              (update-app download-url
                          (if (= (System/getProperty "os.name") "Windows")
                            windows-script-path
                            linux-script-path))
              (println "Exiting the application to allow the update.")
              (.exit (Runtime/getRuntime) 0))))))
    (catch Exception e (prof/status-err! (.getMessage e)) nil)))
