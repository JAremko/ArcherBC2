(ns tvt.a7.profedit.rosetta
  (:require [clojure.spec.alpha :as s]
            [tvt.a7.profedit.asi :as ass]
            [pronto.core :as p]
            [pronto.utils :as u]
            [tvt.a7.profedit.reticle :as reticle]
            [clojure.string :as strings]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [cheshire.generate :as json-gen]
            [tvt.a7.profedit.profile :as prof])
  (:import profedit.Profedit$Payload))


(s/def ::pld (s/keys :req-un [::prof/profiles ::reticle/reticles]))


(p/defmapper proto-payload-mapper [Profedit$Payload]
  :key-name-fn u/->kebab-case
  :enum-value-fn {"LEFT" :left
                  "RIGHT" :right
                  "G1" :g1
                  "G7" :g7
                  "CUSTOM" :custom
                  "VALUE" :value
                  "INDEX" :index})


(defn make-pld [state reticles]
  (-> state (select-keys [:profiles]) (assoc-in [:reticles] reticles)))


(defn- valid-pld? [pld] (s/valid? ::pld pld))


(defn- remove-unused-coeffs [profile bc-type]
  (->> profile
       (remove #(and (strings/starts-with? (name (first %)) "coef-")
                     (not= (first %) bc-type)))
       (into {})))


(defn- sw-pos-pack-dist-source [profile]
  (let [sw-pos-keys (filter #(strings/starts-with? (name %) "sw-pos-")
                            (keys profile))]
    (reduce (fn [acc sw-key]
              (let [sw-pos-map (get acc sw-key)]
                (if (= (:distance-from sw-pos-map) :index)
                  (assoc acc sw-key (assoc sw-pos-map :distance 0))
                  (assoc acc sw-key (assoc sw-pos-map :c-idx 255)))))
            profile
            sw-pos-keys)))


(defn dehydrate-profile [profile]
  (let [bc-type (keyword (str "coef-" (name (:bc-type profile))))]
    (-> profile
        (remove-unused-coeffs bc-type)
        (sw-pos-pack-dist-source))))


(defn dehydrate-pld [pld]
  (let [updated-profiles (map dehydrate-profile (:profiles pld))]
    (assoc pld :profiles updated-profiles)))


(defn- hydrate-switch-pos [sp]
  (if (= (:distance-from sp) :value)
    (assoc sp :c-idx 0) ;; When index is unused it's out of bounds.
    (assoc sp :distance 1))) ;; Unused distances are 0 but we have a rule
                             ;; that distance can't be less than 1(meter)


(defn- hydrate-profile [profile]
  (-> profile
      (update :sw-pos-a hydrate-switch-pos)
      (update :sw-pos-b hydrate-switch-pos)
      (update :sw-pos-c hydrate-switch-pos)
      (update :sw-pos-d hydrate-switch-pos)))


(defn- hydrate-pld [pld]
  (update pld :profiles #(mapv hydrate-profile %)))


(defn proto-bin-ser [pld]
  (->> pld
       (dehydrate-pld)
       (p/clj-map->proto-map proto-payload-mapper Profedit$Payload)
       (p/proto-map->bytes)))


(defn proto-bin-deser [proto-bin]
  (try (->> proto-bin
            (p/bytes->proto-map proto-payload-mapper Profedit$Payload)
            (p/proto-map->clj-map)
            (hydrate-pld))
       (catch Exception _ nil)))


(defn- encode-base64 [byte-array]
  (let [encoder (java.util.Base64/getEncoder)]
    (.encodeToString encoder byte-array)))


(defn- decode-base64 [base64-string]
  (let [decoder (java.util.Base64/getDecoder)]
    (.decode decoder ^String base64-string)))


(json-gen/add-encoder clojure.lang.Keyword
                      (fn [c ^com.fasterxml.jackson.core.JsonGenerator jgen]
                        (.writeString jgen (str "{{^keyword}}"
                                                (name c)))))


(json-gen/add-encoder (Class/forName "[B")
                      (fn [c  ^com.fasterxml.jackson.core.JsonGenerator jgen]

                        (.writeString jgen (str "{{^base64}}"
                                           (encode-base64 c)))))


(defn- convert-string-encoded
  [m]
  (walk/postwalk
   (fn [x]
     (cond
       (strings/starts-with? x "{{^keyword}}")
       (keyword (strings/replace x "{{^keyword}}" ""))
       (strings/starts-with? x "{{^base64}}")
       (decode-base64 (strings/replace x "{{^base64}}" ""))
       :else x))
   m))


(defn json-ser [pld] (json/generate-string
                       pld {:pretty true
                            :escape-non-ascii true}))


(defn json-deser [str] (convert-string-encoded (json/parse-string str true)))


(defn- report-bad-pld! [bad-pld]
  (prof/status-err! (if (some? bad-pld)
                      (do ((ass/pop-report! (prof/val-explain ::pld bad-pld)
                                            ::pld))
                          "Error reported")
                      "Failed to parse data"))
  nil)


(defn expr! [ser-fn pld]
  (if (valid-pld? pld)
    (ser-fn pld)
    (report-bad-pld! pld)))


(defn impr! [deser-fn foregin-pld]
  (let [pld (deser-fn foregin-pld)]
    (if (valid-pld? pld)
      pld
      (report-bad-pld! pld))))


;; (impr json-deser (expr json-ser (make-pld prof/example [])))


;; (->>
;;  {:foo :bar :baz (byte-array [(byte 0x43)
;;                               (byte 0x6c)
;;                               (byte 0x6f)
;;                               (byte 0x6a)
;;                               (byte 0x75)
;;                               (byte 0x72)
;;                               (byte 0x65)
;;                               (byte 0x21)])}
;;  json-ser
;;  json-deser
;;  :baz
;;  String.
;;  )
