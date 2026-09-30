(ns tvt.a7.profedit.leak-canary
  "Watch-count leak canary — THE regression net for the patched seesaw.bind
   (doc/code-analysis-report.md §8 item 6.3 / Phase 1e).

   Run explicitly, with a display and WITHOUT -Drepl (the dev property
   auto-runs -main at app-namespace load):

       lein with-profile -dev run -m tvt.a7.profedit.leak-canary

   Asserts that the watch counts on the four long-lived atoms return to zero
   after every UI generation is disposed, across: frame rebuild cycles (the
   theme/language-switch path), profile loads (incl. a custom bc-type with
   many coefficient rows), bc-type toggles + coefficient-panel regeneration,
   dnd-frame open/close, and a wizard frame open/close.  Wizard back/forward
   navigation and the 1 Hz idle check remain manual runbook items
   (doc/testing-runbook.md §3).

   NOTE: deliberately contains no deftest and no top-level app require, so
   `lein test` can load this namespace without side effects."
  (:gen-class))

(defn- watches [a] (count (.getWatches ^clojure.lang.Atom a)))

(defn- flush-edt!
  "Pump the EDT a few turns so deferred (invoke-later) auto-GC checks and
   WINDOW_CLOSED listeners run."
  []
  (dotimes [_ 4]
    (javax.swing.SwingUtilities/invokeAndWait (fn []))
    (Thread/sleep 30)))

(def ^:private failures (atom []))

(defn- check! [label expected actual]
  (let [ok? (= expected actual)]
    (println (format "[%s] %-52s expected=%s actual=%s"
                     (if ok? "PASS" "FAIL") label expected actual))
    (when-not ok? (swap! failures conj label))))


(defn- require-success! [operation result]
  (or result (throw (ex-info "Canary profile I/O failed" {:operation operation}))))


(defn prepare-profiles! [^java.io.File dir *state save!]
  (let [fp1 (require-success! :save-g1 (save! *state (str (java.io.File. dir "p1"))))]
    (swap! *state assoc-in [:profile :bc-type] :custom)
    (swap! *state assoc-in [:profile :coef-custom]
           (mapv (fn [i] {:cd 0.5 :ma (/ (inc i) 10.0)}) (range 50)))
    [fp1 (require-success! :save-custom
                           (save! *state (str (java.io.File. dir "p2"))))]))


(defn load-profile! [*state file-path load!]
  (require-success! [:load file-path] (load! *state file-path)))

(defn -main [& _]
  (when (System/getProperty "repl")
    (println "ERROR: run without -Drepl (use: lein with-profile -dev run -m tvt.a7.profedit.leak-canary)")
    (System/exit 2))
  (require '[tvt.a7.profedit.app]
           '[tvt.a7.profedit.profile]
           '[tvt.a7.profedit.fio]
           '[tvt.a7.profedit.wizard]
           '[tvt.a7.profedit.frames]
           '[seesaw.core])
  (let [app-ns    (find-ns 'tvt.a7.profedit.app)
        *pa       @(ns-resolve app-ns '*pa)
        *status   @(resolve 'tvt.a7.profedit.profile/*status)
        *storages @(resolve 'tvt.a7.profedit.fio/*profile-storages)
        *cur-fp   @(resolve 'tvt.a7.profedit.fio/*current-fp)
        fr-main   (ns-resolve app-ns 'fr-main)
        dispose!  @(resolve 'seesaw.core/dispose!)
        save!     @(resolve 'tvt.a7.profedit.fio/save!)
        load!     @(resolve 'tvt.a7.profedit.fio/load!)
        assoc-in-prof! @(resolve 'tvt.a7.profedit.profile/assoc-in-prof!)
        counts    (fn [] {:pa (watches *pa) :status (watches *status)
                          :storages (watches *storages) :cur-fp (watches *cur-fp)})
        zero      {:pa 0 :status 0 :storages 0 :cur-fp 0}
        on-edt    (fn [f] (javax.swing.SwingUtilities/invokeAndWait f))]

    ;; -- scenario 1: frame rebuild cycles (theme/language-switch analog) ----
    (dotimes [i 3]
      (let [frame (atom nil)]
        (on-edt #(reset! frame (doto ^javax.swing.JFrame (fr-main) (.pack))))
        (flush-edt!)
        (on-edt #(dispose! @frame))
        (flush-edt!)
        (check! (str "rebuild cycle " i " -> all watches zero") zero (counts))))

    ;; -- scenario 2: profile loads incl. big custom table -------------------
    (let [dir (doto (java.io.File. (System/getProperty "java.io.tmpdir")
                                   (str "canary-" (System/nanoTime)))
                (.mkdirs))]
      (with-redefs-fn {(resolve 'seesaw.core/alert) (fn [& _] nil)}
        (fn []
          (let [paths (atom nil)
                _ (on-edt #(reset! paths (prepare-profiles! dir *pa save!)))
                [fp1 fp2] @paths
                frame (atom nil)]
            (on-edt #(reset! frame (doto ^javax.swing.JFrame (fr-main) (.pack))))
            (flush-edt!)
            (let [base (counts)]
              (dotimes [_ 5]
                (on-edt #(load-profile! *pa fp1 load!)) (flush-edt!)
                (on-edt #(load-profile! *pa fp2 load!)) (flush-edt!))
              (check! "10 profile loads -> counts at 1-frame baseline"
                      base (counts))
              ;; bc-type toggles regenerate the coefficient panel
              (dotimes [_ 3]
                (on-edt #(assoc-in-prof! *pa [:bc-type] :g7)) (flush-edt!)
                (on-edt #(assoc-in-prof! *pa [:bc-type] :custom)) (flush-edt!))
              (check! "bc-type toggles -> counts at 1-frame baseline"
                      base (counts)))
            (on-edt #(dispose! @frame))
            (flush-edt!)
            (check! "after frame dispose -> all watches zero" zero (counts))))))

    ;; -- scenario 3: dnd frame open/close -----------------------------------
    (let [make-dnd @(resolve 'tvt.a7.profedit.frames/make-dnd-frame)]
      (dotimes [_ 3]
        (let [frame (atom nil)]
          (on-edt #(reset! frame (make-dnd *pa nil)))
          (flush-edt!)
          (on-edt #(dispose! @frame))
          (flush-edt!)))
      (check! "dnd open/close x3 -> all watches zero" zero (counts)))

    ;; -- scenario 4: wizard frame open/close (per-run *w-state) -------------
    (let [start-wizard! @(resolve 'tvt.a7.profedit.wizard/start-wizard!)
          make-frame-wizard @(resolve 'tvt.a7.profedit.frames/make-frame-wizard)
          frame (atom nil)]
      (on-edt #(reset! frame (start-wizard!
                              (fn [] (doto ^javax.swing.JFrame (fr-main) (.pack)))
                              make-frame-wizard
                              *pa)))
      (flush-edt!)
      (on-edt #(dispose! @frame))
      (flush-edt!)
      (check! "wizard open/close -> all watches zero" zero (counts)))

    (println (format "%n=== canary: %d failures ===" (count @failures)))
    (System/exit (if (seq @failures) 1 0))))
