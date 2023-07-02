(ns tvt.a7.profedit.wizard
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.distances :refer [make-dist-panel]]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.ballistic :as ball]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [seesaw.forms :as sf]
   [tvt.a7.profedit.asi :as ass]
   [seesaw.border :refer [empty-border]]
   [j18n.core :as j18n]
   [clojure.spec.alpha :as s]
   [tvt.a7.profedit.app :as app]))


(defn- valid-profile? [profile]
  (s/valid? ::prof/profile profile))


(defn- report-invalid-profile! [profile]
  (ass/pop-report! (prof/val-explain ::prof/profile profile))
  nil)


(defn- stage-wrapper [profile mod-fn]
  (if (valid-profile? profile)
    (let [updated-profile (mod-fn profile)]
      (if (or (valid-profile? updated-profile)
              (nil? updated-profile))
        updated-profile
        (report-invalid-profile! profile)))
    (report-invalid-profile! profile)))


(alias 'app 'tvt.a7.profedit.app)


(defn- wizard-frame []
  (sc/frame
   :icon (conf/key->icon :icon-frame)
   :id :frame-main
   :on-close
   (if (System/getProperty "repl") :dispose :exit)
   :content (sc/label "Hello! I'm Wizard!")))


;; TODO We probably should define wizard frame constructor in the main or create
;; a frames namespace since they will share a lot. Then a we can pass both the
;; wizard and main frame constructors to the wizard action. Also blocking main
;; thread isn't cool so I guess final action should be bound to a button.
;; Actually the wizard constructor probably should accept body with forms and
;; always generate cancel and next buttons that will be bound to corresponding
;; functions also passed to the wizard frame constructor. Cancel button will
;; simply exit program for now. The next button function should take wizard
;; frame constructor and be able to construct next frame or output spec report
(defn- noop-stage [profile]
  (sc/show! (sc/pack! (wizard-frame)))
  (println "ok")
  profile)


(defn start-wizard [frame-cons *state]
  (when-let [new-profile (some-> prof/example
                                 (:profile)
                                 (stage-wrapper noop-stage))]
    (swap! *state #(assoc % :profile new-profile))
    (w/save-as-chooser *state))
  (sc/show! (frame-cons)))
