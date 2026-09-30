(ns updater.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clj-http.client :as http]
            [updater.core :as updater]))


(deftest release-repository-drives-every-download-url
  (is (re-matches #"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"
                  updater/release-repository))
  (is (= (str "https://github.com/" updater/release-repository "/releases")
         updater/releases-url))
  (is (= (str updater/releases-url "/latest/download/profedit.jar")
         updater/download-url))
  (is (= (str updater/download-url ".sha256") updater/checksum-url))
  (is (.contains ^String updater/fail-string
                 (str updater/releases-url
                      "/latest/download/ArcherBC2_install.exe"))))


(deftest retries-recover-and-eventually-propagate
  (with-redefs [updater/retry-delay-ms 0]
    (testing "a transient failure is retried"
      (let [attempts (atom 0)
            result (updater/with-retries
                    (fn []
                      (if (< (swap! attempts inc) 3)
                        (throw (Exception. "transient"))
                        :ok)))]
        (is (= :ok result))
        (is (= 3 @attempts))))
    (testing "the final error is not swallowed"
      (let [attempts (atom 0)]
        (is (thrown-with-msg?
             Exception #"permanent"
             (updater/with-retries
              (fn []
                (swap! attempts inc)
                (throw (Exception. "permanent"))))))
        (is (= updater/max-retries @attempts))))))


(deftest checksum-fallback-requires-a-confirmed-missing-asset
  (with-redefs [http/get (fn [& _] {:status 404})]
    (is (nil? (#'updater/fetch-expected-sha256))))
  (doseq [status [403 429 500 503]]
    (with-redefs [http/get (fn [& _] {:status status :body "unavailable"})]
      (is (thrown? Exception (#'updater/fetch-expected-sha256)))))
  (with-redefs [http/get (fn [& _] (throw (java.net.SocketTimeoutException. "timeout")))]
    (is (thrown? java.net.SocketTimeoutException (#'updater/fetch-expected-sha256))))
  (doseq [body ["" "garbage" (apply str (repeat 63 "a"))]]
    (with-redefs [http/get (fn [& _] {:status 200 :body body})]
      (is (thrown? Exception (#'updater/fetch-expected-sha256)))))
  (let [digest (apply str (repeat 64 "a"))]
    (with-redefs [http/get (fn [& _] {:status 200 :body (str digest "  profedit.jar\n")})]
      (is (= digest (#'updater/fetch-expected-sha256))))))


(deftest verification-errors-never-replace-the-running-jar
  (doseq [response [{:status 503 :body "unavailable"}
                    {:status 200 :body "malformed"}
                    {:status 200 :body (apply str (repeat 64 "a"))}]]
    (let [operations (atom [])
          locked-file (java.io.File/createTempFile "archerbc2-locked-" ".jar")]
     (try
      (with-redefs-fn {#'http/get (fn [& _] response)
                       #'io/file (fn [& _] locked-file)
                       #'updater/retry-delay-ms 0
                       #'updater/max-retries 2
                       #'updater/msg (fn [& _])
                       #'updater/download-file (fn [& _])
                       #'updater/sha256-hex (constantly (apply str (repeat 64 "b")))
                       ;; A rejected download may remain locked on disk. Its
                       ;; existence must never authorize installation.
                       #'updater/delete-file (fn [_] false)
                       #'updater/show-warning! (fn [_] (swap! operations conj :warning))
                       #'updater/rename-loop (fn [& _] (swap! operations conj :renamed))
                       #'updater/move-file (fn [& _] (swap! operations conj :installed))
                       #'updater/start-app (fn [path] (swap! operations conj [:restart path]))}
        #(updater/updater nil))
      (is (= [:warning [:restart "profedit.jar"]] @operations))
      (finally (.delete locked-file))))))


(deftest checksum-verification-accepts-valid-and-legacy-downloads
  (let [file (java.io.File/createTempFile "archerbc2-checksum-" ".jar")]
    (try
      (spit file "downloaded bytes")
      (let [digest (#'updater/sha256-hex (.getAbsolutePath file))]
        (doseq [response [{:status 200 :body (.toUpperCase ^String digest java.util.Locale/ROOT)}
                          {:status 404}]]
          (let [messages (atom [])]
            (with-redefs [http/get (fn [& _] response)]
              (#'updater/verify-download! #(swap! messages conj %) (.getAbsolutePath file)))
            (is (= 2 (count @messages))))))
      (finally (.delete file)))))
