(ns tvt.a7.profedit.polling
  "Bounded daemon workers for filesystem probes that may ignore interruption."
  (:import [java.util.concurrent ArrayBlockingQueue Callable ExecutionException
            FutureTask RejectedExecutionException ThreadFactory ThreadPoolExecutor
            TimeUnit TimeoutException]))


(defn scanner [workers queue-size]
  {:tasks (atom {})
   :executor
   (ThreadPoolExecutor.
    (int workers) (int workers) (long 0) TimeUnit/MILLISECONDS
    (ArrayBlockingQueue. (int queue-size))
    ^ThreadFactory (reify ThreadFactory
      (newThread [_ runnable]
        (doto (Thread. ^Runnable runnable "profile-storage-probe")
          (.setDaemon true)))))})


(defn- submit! [{:keys [tasks executor]} key probe]
  (locking tasks
    (or (get @tasks key)
        (let [task (FutureTask. ^Callable (reify Callable (call [_] (probe))))]
          (try
            (.execute ^ThreadPoolExecutor executor task)
            (swap! tasks assoc key task)
            task
            ;; A full pool/queue must not block polling or spawn more workers.
            (catch RejectedExecutionException _ nil))))))


(defn- result [^FutureTask task deadline]
  (try
    {:value (.get task (max 0 (- deadline (System/nanoTime))) TimeUnit/NANOSECONDS)}
    (catch TimeoutException _ nil)
    (catch ExecutionException e {:error (.getCause e)})))


(defn scan!
  "Submit keyed probes before waiting, with one shared deadline. Return only
   completed results, including errors. Timed-out probes stay in flight: native
   filesystem calls need not honor cancellation, so never duplicate them."
  [{:keys [tasks] :as scanner} probes timeout-ms]
  (let [deadline (+ (System/nanoTime) (* 1000000 (long timeout-ms)))
        submitted (mapv (fn [[key probe]] [key (submit! scanner key probe)]) probes)]
    (reduce (fn [results [key task]]
              (if-let [value (when task (result task deadline))]
                (do (swap! tasks dissoc key)
                    (assoc results key value))
                results))
            {} submitted)))


(defn retain!
  "Discard completed probes for devices/files no longer requested. Unfinished
   probes still occupy their slot until the underlying filesystem call returns."
  [{:keys [tasks]} active-keys]
  (swap! tasks
         (fn [entries]
           (into {} (remove (fn [[key ^FutureTask task]]
                              (and (not (contains? active-keys key)) (.isDone task))))
                 entries))))


(defn shutdown! [{:keys [executor tasks]}]
  (.shutdownNow ^ThreadPoolExecutor executor)
  (doseq [^FutureTask task (vals @tasks)] (.cancel task true)))
