(ns tvt.a7.profedit.reticle
  (:require [clojure.spec.alpha :as s]))


(s/def ::preview some?)

(s/def ::data some?)


(s/def ::reticle (s/keys :req-un [::preview ::data]))


(s/def ::reticles (s/coll-of ::reticle :kind vector :min-count 0))
