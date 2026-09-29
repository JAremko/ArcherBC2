(ns tvt.a7.profedit.profile-schema
  "Independent Malli description of the persisted profile contract.

   Production continues to use clojure.spec for conforming fixed-point values.
   These schemas deliberately live in the test tree: they provide a second,
   structurally independent validator and generators without adding Malli to
   the shipped application."
  (:require [clojure.string :as str]
            [malli.core :as m]))


(defn- non-blank-string [max-length]
  [:and
   [:string {:min 1 :max max-length}]
   [:fn {:error/message "must contain a non-whitespace character"}
    (complement str/blank?)]])


(defn- optional-string [max-length]
  [:string {:max max-length}])


(defn- bounded-double [minimum maximum]
  [:double {:min minimum :max maximum}])


(def Distance (bounded-double 1.0 3000.0))
(def ReticleIndex [:int {:min 0 :max 255}])
(def Zoom [:int {:min 0 :max 4}])
(def ZeroDistanceIndex [:int {:min 0 :max 199}])


(def SwitchPosition
  [:map
   [:c-idx [:or [:int {:min 0 :max 200}] [:= 255]]]
   [:distance [:or [:= 0] [:= 0.0] Distance]]
   [:distance-from [:enum :index :value]]
   [:reticle-idx ReticleIndex]
   [:zoom Zoom]])


(def BallisticCoefficientRow
  [:map
   [:bc (bounded-double 0.0 10.0)]
   [:mv (bounded-double 0.0 3000.0)]])


(def CustomCoefficientRow
  [:map
   [:cd (bounded-double 0.0 10.0)]
   [:ma (bounded-double 0.0 10.0)]])


(def ProfileFields
  [:map
   [:profile-name (non-blank-string 50)]
   [:cartridge-name (non-blank-string 50)]
   [:bullet-name (non-blank-string 50)]
   [:caliber (non-blank-string 50)]
   [:device-uuid (optional-string 50)]
   [:short-name-top (non-blank-string 8)]
   [:short-name-bot (non-blank-string 8)]
   [:user-note (optional-string 1024)]
   [:zero-x (bounded-double -200.0 200.0)]
   [:zero-y (bounded-double -200.0 200.0)]
   [:distances [:vector {:min 1 :max 200} Distance]]
   [:switches [:vector {:min 4} SwitchPosition]]
   [:sc-height (bounded-double -5000.0 5000.0)]
   [:r-twist (bounded-double 0.0 100.0)]
   [:twist-dir [:enum :right :left]]
   [:c-muzzle-velocity (bounded-double 10.0 3000.0)]
   [:c-zero-temperature (bounded-double -100.0 100.0)]
   [:c-t-coeff (bounded-double 0.0 5.0)]
   [:c-zero-distance-idx ZeroDistanceIndex]
   [:c-zero-air-temperature (bounded-double -100.0 100.0)]
   [:c-zero-air-pressure (bounded-double 300.0 1500.0)]
   [:c-zero-air-humidity (bounded-double 0.0 100.0)]
   [:c-zero-w-pitch (bounded-double -90.0 90.0)]
   [:c-zero-p-temperature (bounded-double -100.0 100.0)]
   [:b-diameter (bounded-double 0.001 50.0)]
   [:b-weight (bounded-double 1.0 6553.5)]
   [:b-length (bounded-double 0.01 200.0)]
   [:coef-g1 [:vector {:max 5} BallisticCoefficientRow]]
   [:coef-g7 [:vector {:max 5} BallisticCoefficientRow]]
   [:coef-custom [:vector {:max 200} CustomCoefficientRow]]
   [:bc-type [:enum :g1 :g7 :custom]]])


(def Profile
  [:and
   ProfileFields
   [:fn {:error/message "zero-distance index must address the distance list"}
    (fn [{:keys [c-zero-distance-idx distances]}]
      (< c-zero-distance-idx (count distances)))]])


(def Payload
  [:map [:profile Profile]])


(defn valid-profile? [profile]
  (m/validate Profile profile))


(defn valid-payload? [payload]
  (m/validate Payload payload))
