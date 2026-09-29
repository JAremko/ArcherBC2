(ns updater.core-test
  (:require [clojure.test :refer [deftest is testing]]
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
