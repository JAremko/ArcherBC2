(ns tvt.a7.profedit.polling-test
  (:require [clojure.test :refer [deftest is]]
            [me.raynes.fs :as fs]
            [seesaw.invoke :as invoke]
            [tvt.a7.profedit.fio :as fio]
            [tvt.a7.profedit.polling :as polling])
  (:import [java.util.concurrent CountDownLatch ThreadPoolExecutor]))


(defn- await-uninterruptibly [^CountDownLatch latch]
  (loop []
    (when-not (try (.await latch) true (catch InterruptedException _ false))
      (recur))))


(deftest stuck-probes-do-not-block-healthy-results-or-multiply-workers
  (let [scanner (polling/scanner 2 4)
        release (CountDownLatch. 1)
        calls (atom 0)
        probes (array-map :stuck #(do (swap! calls inc) (await-uninterruptibly release) :done)
                          :healthy (constantly :ok))]
    (try
      (let [result (polling/scan! scanner probes 100)]
        (is (= {:value :ok} (:healthy result)))
        (is (not (contains? result :stuck))))
      (dotimes [_ 5]
        (is (= {} (polling/scan! scanner (select-keys probes [:stuck]) 1))))
      (is (= 1 @calls) "timing out never schedules another copy of blocked native I/O")
      (.countDown release)
      (is (= {:stuck {:value :done}}
             (polling/scan! scanner (select-keys probes [:stuck]) 1000)))
      (finally (.countDown release) (polling/shutdown! scanner)))))


(deftest worker-and-queue-capacity-remain-bounded
  (let [scanner (polling/scanner 1 1)
        release (CountDownLatch. 1)
        probe #(await-uninterruptibly release)]
    (try
      (polling/scan! scanner (array-map :running probe :queued probe) 0)
      (dotimes [_ 5]
        (is (= {} (polling/scan! scanner {:overflow probe} 0))))
      (is (= 2 (count @(:tasks scanner))))
      (is (= 1 (.getMaximumPoolSize ^ThreadPoolExecutor (:executor scanner))))
      (finally (.countDown release) (polling/shutdown! scanner)))))


(deftest probe-errors-and-abandoned-results-are-recoverable
  (let [scanner (polling/scanner 1 2)]
    (try
      (is (instance? IllegalStateException
                     (get-in (polling/scan! scanner {:device #(throw (IllegalStateException.))} 1000)
                             [:device :error])))
      (is (= {:device {:value nil}}
             (polling/scan! scanner {:device (constantly nil)} 1000)))
      (let [done (promise)]
        (polling/scan! scanner {:obsolete #(deliver done true)} 0)
        (is (= true (deref done 1000 :timeout)))
        ;; Wait for the FutureTask's completion, not just the body promise.
        (when-let [task (get @(:tasks scanner) :obsolete)] @task)
        (polling/retain! scanner #{})
        (is (empty? @(:tasks scanner))))
      (finally (polling/shutdown! scanner)))))


(deftest stale-invalidation-preserves-the-newly-loaded-file
  (doseq [replacement ["new.a7p" nil]]
    (let [scanner (polling/scanner 3 4)
          queued (atom [])
          current (atom "old.a7p")]
      (try
        (with-redefs-fn {#'fio/storage-scanner (delay scanner)
                         #'fio/*current-fp current
                         #'fio/get-mount-points (constantly [])
                         #'fio/profile-storages (constantly #{})
                         #'fs/readable? (constantly false)
                         #'invoke/invoke-later* #(swap! queued conj %)}
          (fn []
            (#'fio/updater-tick! (fn [_]) (atom {}))
            (is (= 1 (count @queued)))
            (when replacement (reset! current replacement))
            (doseq [callback @queued] (callback))
            (is (= replacement @current))))
        (finally (polling/shutdown! scanner))))))


(deftest polling-isolates-blocked-device-metadata-and-file-readability
  (let [scanner (polling/scanner 4 8)
        release (CountDownLatch. 1)
        blocked-calls (atom 0)
        state (atom {})
        current (atom "blocked.a7p")
        healthy {:path "healthy" :profiles ["one.a7p"]}
        local {:path "local" :profiles []}]
    (try
      (with-redefs-fn {#'fio/storage-scanner (delay scanner)
                       #'fio/*current-fp current
                       #'fio/mount-scan-timeout-ms 100
                       #'fio/get-mount-points (constantly ["blocked" "healthy"])
                       #'fio/profile-storages (constantly #{local})
                       #'fio/cached-device-dir-manifest
                       (fn [path]
                         (if (= path "blocked")
                           (do (swap! blocked-calls inc) (await-uninterruptibly release) nil)
                           healthy))
                       #'fs/readable? (fn [_] (await-uninterruptibly release) true)
                       #'invoke/invoke-later* (fn [_])}
        (fn []
          ;; The first tick may spend its budget waiting on readability. The
          ;; already submitted device results must survive until the next tick.
          (dotimes [_ 3] (#'fio/updater-tick! (fn [_]) state))
          (is (= #{local healthy} (:storages @state)))
          (is (= 1 @blocked-calls))
          (is (= "blocked.a7p" @current))))
      (finally (.countDown release) (polling/shutdown! scanner)))))


(deftest blocked-enumeration-does-not-stop-local-storage-discovery
  (let [scanner (polling/scanner 2 4)
        release (CountDownLatch. 1)
        state (atom {})
        local {:path "local" :profiles []}]
    (try
      (with-redefs-fn {#'fio/storage-scanner (delay scanner)
                       #'fio/*current-fp (atom nil)
                       #'fio/mount-scan-timeout-ms 50
                       #'fio/get-mount-points #(do (await-uninterruptibly release) [])
                       #'fio/profile-storages (constantly #{local})
                       #'invoke/invoke-later* (fn [_])}
        (fn []
          (#'fio/updater-tick! (fn [_]) state)
          (is (= #{local} (:storages @state)))))
      (finally (.countDown release) (polling/shutdown! scanner)))))
