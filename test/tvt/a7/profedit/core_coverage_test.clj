(ns tvt.a7.profedit.core-coverage-test
  "Branch-focused tests for the deterministic core measured by Cloverage."
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [seesaw.core :as sc]
            [tvt.a7.profedit.asi :as asi]
            [tvt.a7.profedit.cdf :as cdf]
            [tvt.a7.profedit.config :as config]
            [tvt.a7.profedit.fio :as fio]
            [tvt.a7.profedit.log :as log]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.rosetta :as ros]
            [tvt.a7.profedit.update :as update])
  (:import [java.io ByteArrayInputStream File]
           [java.net URI]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files]
           [java.util Base64 Locale]
           [java.util.logging Handler Level Logger LogRecord]))


(defn- temp-dir ^File [prefix]
  (.toFile (Files/createTempDirectory prefix
                                      (make-array java.nio.file.attribute.FileAttribute 0))))


(defn- child ^File [^File parent ^String name]
  (File. parent name))


(defn- utf8-bytes [s]
  (.getBytes ^String s StandardCharsets/UTF_8))


(deftest profile-state-helper-contracts
  (let [original-status @prof/*status
        state {:profile {:distance 10.0 :name "old"}}
        *state (atom state)]
    (try
      (testing "pure accessors and updates"
        (is (= 10.0 (prof/get-in-prof state [:distance])))
        (is (= 10.0 (prof/get-in-prof* *state [:distance])))
        (is (= (:profile state) (prof/state->cur-prof state)))
        (is (= (:profile state) (prof/state->cur-prof (:profile state))))
        (is (identical? state (prof/assoc-in-prof state [:name] "old")))
        (is (= "new" (prof/get-in-prof
                      (prof/assoc-in-prof state [:name] "new") [:name])))
        (is (identical? state
                        (prof/assoc-in-prof state [:distance]
                                            ::prof/distance 0.5)))
        (is (= 25.0 (prof/get-in-prof
                     (prof/assoc-in-prof state [:distance]
                                         ::prof/distance 25.0)
                     [:distance]))))
      (testing "atom updates report valid changes and reject invalid values"
        (prof/status-err! "before")
        (prof/assoc-in-prof! *state [:name] "new")
        (is (= "new" (prof/get-in-prof* *state [:name])))
        (is (prof/status-ok?))
        (prof/assoc-in-prof! *state [:distance] ::prof/distance 30.0)
        (is (= 30.0 (prof/get-in-prof* *state [:distance])))
        (is (prof/status-ok?))
        (prof/assoc-in-prof! *state [:distance] ::prof/distance -1.0)
        (is (= 30.0 (prof/get-in-prof* *state [:distance])))
        (is (prof/status-err?))
        (is (string? (prof/status-text))))
      (testing "error formatting covers range, length, formatter and explain paths"
        (is (string? (prof/format-spec-err ::prof/distance -1.0)))
        (is (string? (prof/format-spec-err ::prof/profile-name "")))
        (is (string? (prof/format-spec-err ::prof/distance "bad"
                                           (constantly -1.0))))
        (with-redefs [prof/val-explain (fn [_ _] "predicate detail")]
          (is (= "predicate detail"
                 (prof/format-spec-err ::prof/non-empty-string "")))))
      (finally
        (reset! prof/*status original-status)))))


(deftest file-helper-and-json-contracts
  (let [dir (temp-dir "archerbc2-fio-")
        a7p (child dir "PROFILE.A7P")
        txt (child dir "profile.txt")
        json-base (.getAbsolutePath (child dir "exported"))
        *state (atom prof/example)]
    (spit a7p "x")
    (spit txt "x")
    (testing "watch registration and removal"
      (let [seen (atom [])]
        (fio/add-current-fp-watcher ::coverage #(swap! seen conj %))
        (reset! fio/*current-fp (.getAbsolutePath a7p))
        (is (= [(.getAbsolutePath a7p)] @seen))
        (fio/remove-current-fp-watcher ::coverage)
        (reset! fio/*current-fp nil)
        (is (= 1 (count @seen)))))
    (testing "path and name helpers"
      (is (fio/a7p-file? a7p))
      (is (not (fio/a7p-file? txt)))
      (is (= "x.a7p" (fio/ensure-extension "x" "a7p")))
      (is (= "x.a7p" (fio/ensure-extension "x.a7p" ".a7p")))
      (is (fio/ascii-only-name? (.getAbsolutePath a7p)))
      (is (not (fio/ascii-only-name? (.getAbsolutePath (child dir "профіль.a7p"))))))
    (testing "safe execution exposes errors without propagating them"
      (is (= 3 (fio/safe-exec! + 1 2)))
      (with-redefs [sc/alert (fn [& _] nil)]
        (is (nil? (fio/safe-exec! #(throw (Exception. "expected")))))
        (is (= "expected" (prof/status-text)))))
    (testing "JSON export/import keeps a valid profile"
      (let [json-path (str json-base ".json")]
        (is (= json-path (fio/export! *state json-base)))
        (is (.exists (io/file json-path)))
        (swap! *state assoc-in [:profile :profile-name] "changed")
        (is (= json-path (fio/import! *state json-path)))
        (is (= (get-in prof/example [:profile :profile-name])
               (get-in @*state [:profile :profile-name])))))
    (testing "side-load updates only the supplied state"
      (let [binary (child dir "side.a7p")
            payload (select-keys prof/example [:profile])
            *side (atom {:unrelated true})]
        (fio/write-byte-array-to-file (.getAbsolutePath binary)
                                      (ros/proto-bin-ser payload))
        (is (s/valid? ::prof/profile
                      (:profile (fio/side-load! *side (.getAbsolutePath binary)))))
        (is (= (get-in prof/example [:profile :profile-name])
               (get-in @*side [:profile :profile-name])))
        (is (:unrelated @*side))))))


(deftest config-file-and-user-directory-contracts
  (let [dir (temp-dir "archerbc2-config-")
        config-file (child dir "config.edn")
        missing-parent (child (child dir "missing") "config.edn")
        old-home (System/getProperty "user.home")]
    (try
      (System/setProperty "user.home" (.getAbsolutePath dir))
      (is (.isDirectory (io/file (fio/get-user-profiles-dir))))
      (is (.endsWith ^String (fio/get-config-file-path) ".profedit.edn"))
      (is (= (fio/get-user-profiles-dir) (fio/get-cur-fp-dir)))
      (let [profile-path (.getAbsolutePath (child dir "profiles/a.a7p"))]
        (reset! fio/*current-fp profile-path)
        (is (= (.getParent (io/file profile-path)) (fio/get-cur-fp-dir))))
      (is (true? (fio/write-config config-file config/default-config)))
      (is (= config/default-config (fio/read-config config-file)))
      (with-redefs [sc/alert (fn [& _] nil)]
        (is (nil? (fio/read-config (child dir "absent.edn"))))
        (is (nil? (fio/write-config missing-parent {:a 1}))))
      (finally
        (reset! fio/*current-fp nil)
        (System/setProperty "user.home" old-home)))))


(deftest device-manifest-and-firmware-contracts
  (let [root (temp-dir "archerbc2-device-")
        info (child root "info")
        profiles (child root "profiles")
        profile-file (child profiles "one.a7p")]
    (.mkdirs info)
    (.mkdirs profiles)
    (spit profile-file "profile")
    (spit (child profiles "ignored.tmp") "tmp")
    (spit (child info "uuid.txt") "uuid-1\n")
    (spit (child info "info.txt")
          (str "serial_number = \"serial-1\"\n"
               "device_class = \"Archer Test\"\n"
               "name_device = \"Test Device\"\n"
               "serial_core = \"core-1\"\n"
               "serial_lrf = \"lrf-1\"\n"
               "data_manufacture = \"2026\"\n"
               "firmware = \"0.39\"\n"))
    (testing "a valid mounted-device directory becomes a complete manifest"
      (let [manifest (#'fio/device-dir-manifest root)]
        (is (= "uuid-1" (:uuid manifest)))
        (is (= "Archer Test" (:device-class manifest)))
        (is (= "0.39" (:version manifest)))
        (is (= [(.getAbsolutePath profile-file)] (:profiles manifest)))))
    (testing "malformed or incomplete devices are ignored"
      (is (nil? (#'fio/device-dir-manifest (child root "absent"))))
      (spit (child info "info.txt") "not = [valid")
      (is (nil? (#'fio/device-dir-manifest root))))
    (testing "firmware resource names decode and select newest safely"
      (let [encoder (Base64/getUrlEncoder)
            encode #(.encodeToString encoder (utf8-bytes %))
            device (encode "Archer Test")
            old (encode "0.39")
            new (encode "0.85")
            old-uri (URI. "file:/firmware/old/CS10.upg")
            new-uri (URI. "file:/firmware/new/CS10.upg")
            tree (#'fio/make-firmware-tree
                  {(str "firmware/" device "/" old "/CS10.upg") [old-uri]
                   (str "firmware/" device "/" new "/CS10.upg") [new-uri]})]
        (is (= #{"0.39" "0.85"} (set (keys (get tree "Archer Test")))))
        (with-redefs-fn {#'fio/firmware-tree tree}
          (fn []
            (is (= "0.85" (:version (#'fio/get-newest-firmware "Archer Test"))))
            (is (= [new-uri]
                   (get-in (first (#'fio/matching-storages-with-newest-firmware
                                   [{:device-class "Archer Test"
                                     :version "0.39"}]))
                           [:newest-firmware :path])))
            (is (empty? (#'fio/matching-storages-with-newest-firmware
                         [{:device-class "Unknown" :version "0.1"}])))))))))


(deftest firmware-copy-and-load-wrapper-contracts
  (let [source-dir (temp-dir "archerbc2-fw-source-")
        target-dir (temp-dir "archerbc2-fw-target-")
        source (child source-dir "CS10.upg")
        entry {:path (.getAbsolutePath target-dir)
               :newest-firmware {:path [(.toURI source)]}}]
    (spit source "firmware-bytes")
    (fio/copy-newest-firmware entry)
    (is (= "firmware-bytes" (slurp (child target-dir "CS10.upg"))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (fio/copy-newest-firmware
                  (assoc entry :path (.getAbsolutePath (child target-dir "missing"))))))
    (let [loaded (atom [])
          statuses (atom [])
          *state (atom prof/example)
          file (child target-dir "profile.a7p")]
      (with-redefs [fio/load! (fn [_ path] (swap! loaded conj path))
                    prof/status-ok! #(swap! statuses conj %)]
        (fio/load-from-fp! *state (.getAbsolutePath file))
        (fio/load-from! *state file)
        (is (= [(.getAbsolutePath file) (.getAbsolutePath file)] @loaded))
        (is (= 2 (count @statuses)))))))


(deftest valid-drg-import-converts-and-sorts-data
  (let [file (File/createTempFile "archerbc2-valid-" ".drg")
        *state (atom prof/example)]
    (.deleteOnExit file)
    (spit file (str "Bullet weight 0.0162 diameter 0.0086 length 0.0400 units\n"
                    "0.40 0.50\n"
                    "0.80 1.00\n"))
    (cdf/apply-drg-file-to-state! *state (.getAbsolutePath file))
    (let [profile (:profile @*state)]
      (is (= :custom (:bc-type profile)))
      (is (< (Math/abs (- 250.00420608 (:b-weight profile))) 1.0e-6))
      (is (< (Math/abs (- 0.33858286 (:b-diameter profile))) 1.0e-6))
      (is (< (Math/abs (- 1.574804 (:b-length profile))) 1.0e-6))
      (is (= [{:cd 0.8 :ma 1.0} {:cd 0.4 :ma 0.5}]
             (:coef-custom profile))))))


(deftest configuration-branches-and-cache-contracts
  (let [original-locale (Locale/getDefault)
        config-atom (var-get #'config/*config)
        original-config @config-atom]
    (try
      (is (= ["en" "EN"] (config/loc-key->pair :english)))
      (is (= ["en" "EN"] (config/loc-key->pair :unknown)))
      (is (= "abc" (String. ^bytes
                            (config/input-stream->bytes
                             (ByteArrayInputStream. (utf8-bytes "abc")))
                            StandardCharsets/UTF_8)))
      (testing "icon resolution is cached and missing resources use placeholders"
        (let [calls (atom 0)]
          (with-redefs-fn {#'config/*icon-cache (atom {})
                           #'io/resource (fn [_] nil)
                           #'sc/icon (fn [_] (swap! calls inc))}
            (fn []
              (is (identical? (config/key->icon :save)
                              (config/key->icon :save)))
              (is (some? (config/banner-source "missing.png")))
              (is (zero? @calls))))))
      (testing "valid and invalid configuration saves"
        (let [writes (atom []) reports (atom [])]
          (with-redefs [fio/write-config (fn [path value]
                                           (swap! writes conj [path value]))
                        asi/pop-report! #(swap! reports conj %)]
            (reset! config-atom config/default-config)
            (config/save-config! "valid.edn")
            (is (= 1 (count @writes)))
            (reset! config-atom {:locale :invalid :color-theme :sol-dark})
            (config/save-config! "invalid.edn")
            (is (= 1 (count @reports))))))
      (testing "load handles valid, invalid, and absent files"
        (let [saved (atom 0) reports (atom 0)]
          (with-redefs [fio/read-config (constantly config/default-config)]
            (config/load-config! "valid.edn")
            (is (= config/default-config @config-atom)))
          (with-redefs [fio/read-config (constantly {:bad true})
                        asi/pop-report! (fn [_] (swap! reports inc))]
            (config/load-config! "invalid.edn")
            (is (= 1 @reports)))
          (with-redefs [fio/read-config (constantly nil)
                        config/save-config! (fn [_] (swap! saved inc))]
            (config/load-config! "missing.edn")
            (is (= config/default-config @config-atom))
            (is (= 1 @saved)))))
      (testing "locale selection validates, persists and falls back by OS locale"
        (let [saved (atom 0) reports (atom 0)]
          (with-redefs [config/save-config! (fn [_] (swap! saved inc))
                        fio/get-config-file-path (constantly "config.edn")
                        asi/pop-report! (fn [_] (swap! reports inc))]
            (config/set-locale! :english)
            (is (= :english (config/get-locale)))
            (is (= "en" (.getLanguage (Locale/getDefault))))
            (is (= "EN" (.getCountry (Locale/getDefault))))
            (config/set-locale! :not-a-locale)
            (is (= 1 @reports))
            (is (= 1 @saved))))
        (reset! config-atom {})
        (Locale/setDefault (Locale. "uk" "UA"))
        (is (= :ukrainian (config/get-locale)))
        (Locale/setDefault (Locale. "de" "DE"))
        (is (= :english (config/get-locale))))
      (finally
        (reset! config-atom original-config)
        (Locale/setDefault original-locale)))))


(deftest logging-initialization-and-severity-contracts
  (let [dir (temp-dir "archerbc2-log-")
        logger ^Logger (var-get #'log/logger)
        initialized (var-get #'log/*initialized?)
        file-handler (var-get #'log/*file-handler)
        old-handler (Thread/getDefaultUncaughtExceptionHandler)
        records (atom [])
        handler (proxy [Handler] []
                  (publish [record] (swap! records conj record))
                  (flush [])
                  (close []))]
    (try
      (.setLevel handler Level/ALL)
      (.addHandler logger handler)
      (with-redefs [log/log-directory (fn [] dir)]
        (reset! initialized false)
        (is (true? (log/initialize!)))
        (is (.contains ^String (log/log-path-pattern) "archerbc2-"))
        (is (true? (log/initialize!))))
      (log/debug! "debug-no-data")
      (log/info! "info-no-data")
      (log/warn! "warn-no-data")
      (log/error! "error-no-data" (Exception. "boom"))
      (log/error! "error-with-data" (Exception. "boom") {:safe true})
      (let [uncaught (Thread/getDefaultUncaughtExceptionHandler)]
        (.uncaughtException uncaught (Thread/currentThread) (Exception. "uncaught")))
      (let [messages (map #(.getMessage ^LogRecord %) @records)]
        (doseq [event ["logging-initialized" "debug-no-data" "info-no-data"
                       "warn-no-data" "error-no-data" "error-with-data"
                       "uncaught-exception"]]
          (is (some #(.contains ^String % event) messages) event)))
      (testing "setup failure is non-fatal and reported to stderr"
        (let [output (java.io.StringWriter.)]
          (reset! initialized false)
          (with-redefs-fn {#'log/writable-log-directory
                           (fn [] (throw (Exception. "no log directory")))}
            (fn []
              (binding [*err* output]
                (is (false? (log/initialize!))))))
          (is (.contains (str output) "no log directory"))))
      (finally
        (when-let [^Handler h @file-handler]
          (.removeHandler logger h)
          (.close h))
        (reset! file-handler nil)
        (reset! initialized false)
        (.removeHandler logger handler)
        (Thread/setDefaultUncaughtExceptionHandler old-handler)))))


(deftest updater-network-file-and-future-contracts
  (testing "latest-tag lookup handles success, empty results, status errors and exceptions"
    (with-redefs [client/get (fn [& _] {:status 200 :body [{:name "2.0.0"}]})]
      (is (= "2.0.0" (#'update/get-latest-tag "1.0.0"))))
    (with-redefs [client/get (fn [& _] {:status 200 :body []})]
      (is (= "1.0.0" (#'update/get-latest-tag "1.0.0"))))
    (with-redefs [client/get (fn [& _] {:status 503})]
      (is (= "1.0.0" (#'update/get-latest-tag "1.0.0"))))
    (with-redefs [client/get (fn [& _] (throw (Exception. "offline")))]
      (is (= "1.0.0" (#'update/get-latest-tag "1.0.0")))))
  (let [version-file (File/createTempFile "archerbc2-version-" ".txt")]
    (spit version-file "1.21.9\n")
    (with-redefs [io/resource (fn [_] (.toURL (.toURI version-file)))]
      (is (= "1.21.9" (update/get-current-version))))
    (with-redefs [io/resource (constantly nil)]
      (is (nil? (update/get-current-version)))))
  (testing "delete helper terminates for success and exhausted retry budget"
    (let [file (File/createTempFile "archerbc2-delete-" ".tmp")]
      (is (#'update/delete-file-with-retries file 0))
      (is (not (.exists file))))
    (with-redefs [io/delete-file (fn [_] (throw (Exception. "locked")))]
      (is (false? (#'update/delete-file-with-retries "locked" 0)))))
  (testing "the update-cleanup property gates deletion"
    (let [calls (atom 0)
          old (System/getProperty "updateUpdater")]
      (try
        (with-redefs-fn {#'update/delete-file-with-retries
                         (fn [& _] (swap! calls inc))}
          (fn []
            (System/setProperty "updateUpdater" "false")
            (#'update/maybe-delete-update-jar)
            (System/setProperty "updateUpdater" "true")
            (#'update/maybe-delete-update-jar)))
        (is (= 1 @calls))
        (finally
          (if old
            (System/setProperty "updateUpdater" old)
            (System/clearProperty "updateUpdater"))))))
  (testing "background check is nil-safe and reports unexpected failures"
    (with-redefs [update/get-current-version (constantly nil)]
      (is (nil? @(update/check-for-update))))
    (let [errors (atom []) statuses (atom [])]
      (with-redefs [update/get-current-version #(throw (Exception. "check failed"))
                    log/error! (fn [& args] (swap! errors conj args))
                    prof/status-err! #(swap! statuses conj %)]
        (is (nil? @(update/check-for-update)))
        (is (= 1 (count @errors)))
        (is (= ["check failed"] @statuses))))))
