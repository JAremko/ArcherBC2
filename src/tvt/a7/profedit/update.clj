(ns tvt.a7.profedit.update
  (:require [clj-http.client :as client]
            [tvt.a7.profedit.profile :as prof]
            [clojure.java.io :as io]
            [seesaw.core :as ssc]
            [tvt.a7.profedit.config :refer [update-conf]]
            [j18n.core :as j18n])
  (:import [java.io BufferedReader InputStreamReader]
           [java.lang ProcessBuilder String]))


(def ^:private script-path
  (:windows-script-path update-conf))


(def ^:private github-api-url
  (:github-api-url update-conf))


(def ^:private version-resource-path
  (:version-resource-path update-conf))


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

<<<<<<< HEAD
(defn- update-app [download-url]
  (let [cmd "cmd"
        arg (str "/c " script-path)
        ^"[Ljava.lang.String;" cmd-array (into-array String [cmd arg download-url])]
    (let [pb (java.lang.ProcessBuilder. cmd-array)]
      (.start pb))))
=======

(defn- update-app []
  (let [cmd "cmd.exe"
        arg (str "/c start " script-path)
        ^"[Ljava.lang.String;" cmd-array (into-array String [cmd arg])]
    (-> cmd-array
        (java.lang.ProcessBuilder.)
        (.start))))

>>>>>>> 0d06439 (buf fixes)

(defn- ask-to-update [frame]
  (ssc/confirm frame
               (j18n/resource ::ask-if-should-update)
               :title (j18n/resource ::software-update)
               :type :info
               :option-type :yes-no))


(defn check-for-update [frame]
  (try
    (when-let [current-version (get-current-version)]
      (let [latest-version (get-latest-tag)]
        (when (not= latest-version current-version)
          (when (ask-to-update frame)
            (println "Update available, starting the update process.")
            (update-app)
            (println "Exiting the application to allow the update.")
            (.exit (Runtime/getRuntime) 0)))))
    (catch Exception e (prof/status-err! (.getMessage e)) nil)))
