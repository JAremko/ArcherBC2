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
   [clojure.spec.alpha :as s]))


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


(defn- noop-stage [profile]
  profile)


(defn start-wizard [frame-cons *state]
  (when-let [new-profile (some-> prof/example
                                 (:profile)
                                 (stage-wrapper noop-stage))]
    (swap! *state #(assoc % :profile new-profile))
    (w/save-as-chooser *state))
  (sc/show! (frame-cons)))
