(ns tvt.a7.profedit.input-commit-test
  (:require [clojure.test :refer [deftest is testing]]
            [seesaw.core :as sc]
            [seesaw.invoke :as invoke]
            [tvt.a7.profedit.actions :as actions]
            [tvt.a7.profedit.fio :as fio]
            [tvt.a7.profedit.frames :as frames]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.wizard :as wizard]
            [tvt.a7.profedit.widgets :as w])
  (:import [java.awt.event ActionEvent FocusEvent]
           [java.util Locale]))


(deftest pending-formatted-input-is-committed-synchronously
  (let [*state (atom {:profile {:c-muzzle-velocity 100.0}})
        panel (w/input-num *state
                           [:c-muzzle-velocity]
                           ::prof/c-muzzle-velocity)
        input (sc/select panel [:#input])]
    (sc/text! input "987.6")
    (is (= 100.0 (prof/get-in-prof* *state [:c-muzzle-velocity]))
        "editing text alone has not emitted focus-lost yet")
    (is (true? (w/commit-inputs! panel)))
    (is (= 987.6 (prof/get-in-prof* *state [:c-muzzle-velocity]))
        "Next can synchronously capture the pending editor value")))


(deftest invalid-pending-input-blocks-navigation-and-preserves-state
  (let [original-status @prof/*status
        *state (atom {:profile {:c-muzzle-velocity 100.0}})
        panel (w/input-num *state
                           [:c-muzzle-velocity]
                           ::prof/c-muzzle-velocity)
        input (sc/select panel [:#input])]
    (try
      (prof/status-ok! "")
      (sc/text! input "not-a-number")
      (is (false? (w/commit-inputs! panel)))
      (is (= 100.0 (prof/get-in-prof* *state [:c-muzzle-velocity])))
      (is (prof/status-err?))
      (finally
        (reset! prof/*status original-status)))))


(deftest numeric-parsing-follows-the-active-locale
  (let [original (Locale/getDefault)]
    (try
      (testing "decimal point"
        (Locale/setDefault Locale/US)
        (is (= 12.5 (w/parse-input-str "12.5" 1))))
      (testing "decimal comma"
        (Locale/setDefault Locale/GERMANY)
        (is (= 12.5 (w/parse-input-str "12,5" 1))))
      (finally
        (Locale/setDefault original)))))


(deftest wizard-rejects-invalid-edits-in-either-focus-order
  (invoke/invoke-now* (bound-fn []
   (doseq [focus-first? [true false]
           invalid ["invalid-number" "99999"]]
     (let [state (atom {:wizard {:content-idx 0}
                       :profile {:c-muzzle-velocity 800.0}})
           panel (wizard/input-num state [:c-muzzle-velocity] ::prof/c-muzzle-velocity)
           input (sc/select panel [:#input])
           advances (atom 0)
           rebuilds (atom 0)
           next! (#'frames/wiz-next-handler
                  state state nil [{:finalizer (constantly true)}] nil)
           ;; Exercise Swing's own focus processing before our listener; it
           ;; must not revert the invalid edit before Next can inspect it.
           focus! #(clojure.lang.Reflector/invokeInstanceMethod
                    input "processFocusEvent"
                    (object-array [(FocusEvent. input FocusEvent/FOCUS_LOST)]))]
       (with-redefs-fn {#'sc/to-root (constantly panel)
                        #'frames/wiz-advance! (fn [& _] (swap! advances inc))
                        #'frames/wiz-reset-content! (fn [& _] (swap! rebuilds inc))}
         (fn []
           (sc/text! input invalid)
           (when focus-first? (focus!))
           (next! (ActionEvent. input ActionEvent/ACTION_PERFORMED "next"))
           (when-not focus-first? (focus!))
           (is (zero? @advances))
           (is (zero? @rebuilds) "keep the editor available for correction")
           (is (= invalid (sc/text input)))
           (is (= 800.0 (prof/get-in-prof* state [:c-muzzle-velocity])))
           (sc/text! input "850")
           (next! (ActionEvent. input ActionEvent/ACTION_PERFORMED "next"))
           (is (= 1 @advances))
           (is (= 850.0 (prof/get-in-prof* state [:c-muzzle-velocity]))))))))))


(deftest committing-a-page-preserves-all-pending-editors
  (invoke/invoke-now* (bound-fn []
   (let [state (atom {:profile {:c-muzzle-velocity 800.0 :c-zero-temperature 15.0}})
         velocity (wizard/input-num state [:c-muzzle-velocity] ::prof/c-muzzle-velocity)
         temperature (wizard/input-num state [:c-zero-temperature] ::prof/c-zero-temperature)
         panel (sc/vertical-panel :items [velocity temperature])]
     (sc/text! (sc/select velocity [:#input]) "850")
     (sc/text! (sc/select temperature [:#input]) "25")
     (is (w/commit-inputs! panel))
     (is (= {:c-muzzle-velocity 850.0 :c-zero-temperature 25.0} (:profile @state)))))))


(deftest invalid-page-keeps-all-pending-edits-until-corrected
  (invoke/invoke-now*
   (bound-fn []
     (let [original {:profile {:c-muzzle-velocity 800.0 :c-zero-temperature 15.0}}
           state (atom original)
           velocity (wizard/input-num state [:c-muzzle-velocity] ::prof/c-muzzle-velocity)
           temperature (wizard/input-num state [:c-zero-temperature] ::prof/c-zero-temperature)
           panel (sc/vertical-panel :items [velocity temperature])
           velocity-input (sc/select velocity [:#input])]
       (sc/text! velocity-input "invalid")
       (sc/text! (sc/select temperature [:#input]) "25")
       (is (false? (w/commit-inputs! panel)))
       (is (= original @state))
       (is (= "invalid" (sc/text velocity-input)))
       (is (= "25" (sc/text (sc/select temperature [:#input]))))
       (is (prof/status-err?))
       (sc/text! velocity-input "850")
       (is (true? (w/commit-inputs! panel)))
       (is (= {:c-muzzle-velocity 850.0 :c-zero-temperature 25.0}
              (:profile @state)))))))


(deftest editor-save-and-export-actions-reject-invalid-pending-input
  (invoke/invoke-now*
   (bound-fn []
     (doseq [make-action [actions/act-save! actions/act-save-as! actions/act-export!]]
       (let [state (atom prof/example)
             panel (w/input-num state [:c-muzzle-velocity] ::prof/c-muzzle-velocity)
             input (sc/select panel [:#input])
             persisted (atom [])
             persist! (fn [state _] (swap! persisted conj (prof/get-in-prof* state [:c-muzzle-velocity])))]
         (with-redefs [invoke/invoke-later* (fn [f] (f))
                       fio/get-cur-fp (constantly "test.a7p")
                       fio/save! persist!
                       w/save-as-chooser persist!
                       w/export-to-chooser persist!
                       w/reset-tree-selection (fn [_])]
           (let [action (make-action state panel)
                 fire! #(.actionPerformed ^javax.swing.Action action
                                          (ActionEvent. panel ActionEvent/ACTION_PERFORMED "save"))]
             (sc/text! input "invalid")
             (fire!)
             (is (empty? @persisted))
             (is (= "invalid" (sc/text input)))
             (sc/text! input "850")
             (fire!)
             (is (= [850.0] @persisted)))))))))
