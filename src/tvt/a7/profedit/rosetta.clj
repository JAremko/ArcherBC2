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


(defn proto-bin-ser [pld]
  (->> pld
       (p/clj-map->proto-map proto-payload-mapper
       Profedit$Payload)
       p/proto-map->bytes))


(defn proto-bin-deser [proto-bin]
  (try (->> proto-bin
            (p/bytes->proto-map proto-payload-mapper
            Profedit$Payload)
            p/proto-map->clj-map)
       (catch Exception _ nil)))


(defn- encode-base64 [byte-array]
  (let [encoder (java.util.Base64/getEncoder)]
    (.encodeToString encoder byte-array)))


(defn- decode-base64 [base64-string]
  (let [decoder (java.util.Base64/getDecoder)]
    (.decode decoder base64-string)))


(json-gen/add-encoder clojure.lang.Keyword
                      (fn [c jsonGenerator]
                        (.writeString jsonGenerator
                                      (str "{{^keyword}}" (name c)))))


(json-gen/add-encoder (Class/forName "[B")
                      (fn [c jsonGenerator]
                        (.writeString jsonGenerator
                                      (str "{{^base64}}"
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
;; => "Clojure!"
