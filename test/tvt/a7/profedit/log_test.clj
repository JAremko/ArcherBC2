(ns tvt.a7.profedit.log-test
  (:require [clojure.test :refer [deftest is testing]]
            [tvt.a7.profedit.log :as log]
            [tvt.a7.profedit.profile :as prof])
  (:import [java.util.logging Handler Level Logger LogRecord]))


(deftest platform-log-directory-test
  (testing "Windows uses LOCALAPPDATA when available"
    (is (= (str (java.io.File. "/local/ArcherBC2/logs"))
           (str (log/log-directory "Windows 10"
                                   "/users/test"
                                   {"LOCALAPPDATA" "/local"})))))

  (testing "Windows has a user-home fallback"
    (is (= (str (java.io.File. "/users/test/AppData/Local/ArcherBC2/logs"))
           (str (log/log-directory "Windows 10" "/users/test" {})))))

  (testing "Windows ignores an empty LOCALAPPDATA value"
    (is (= (str (java.io.File. "/users/test/AppData/Local/ArcherBC2/logs"))
           (str (log/log-directory "Windows 10"
                                   "/users/test"
                                   {"LOCALAPPDATA" ""})))))

  (testing "macOS uses the conventional user log directory"
    (is (= (str (java.io.File. "/users/test/Library/Logs/ArcherBC2"))
           (str (log/log-directory "Mac OS X" "/users/test" {})))))

  (testing "Linux honors XDG_STATE_HOME"
    (is (= (str (java.io.File. "/state/archerbc2/logs"))
           (str (log/log-directory "Linux"
                                   "/users/test"
                                   {"XDG_STATE_HOME" "/state"})))))

  (testing "Linux has a user-home fallback"
    (is (= (str (java.io.File. "/users/test/.local/state/archerbc2/logs"))
           (str (log/log-directory "Linux" "/users/test" {})))))

  (testing "OS detection is case-insensitive"
    (is (= (str (java.io.File. "/local/ArcherBC2/logs"))
           (str (log/log-directory "WINDOWS 10"
                                   "/users/test"
                                   {"LOCALAPPDATA" "/local"}))))))


(deftest status-messages-are-sent-to-the-application-logger
  (let [logger (Logger/getLogger "tvt.a7.profedit")
        original-level (.getLevel logger)
        original-status @prof/*status
        records (atom [])
        handler (proxy [Handler] []
                  (publish [record] (swap! records conj record))
                  (flush [])
                  (close []))]
    (try
      (.setLevel logger Level/ALL)
      (.setLevel handler Level/ALL)
      (.addHandler logger handler)
      (prof/status-ok! "routine-message")
      (prof/status-err! "validation-message")
      (let [messages (map #(.getMessage ^LogRecord %) @records)]
        (is (some #(.contains ^String % "status-update") messages))
        (is (some #(.contains ^String % "routine-message") messages))
        (is (some #(.contains ^String % "status-error") messages))
        (is (some #(.contains ^String % "validation-message") messages)))
      (finally
        (reset! prof/*status original-status)
        (.removeHandler logger handler)
        (.setLevel logger original-level)))))
