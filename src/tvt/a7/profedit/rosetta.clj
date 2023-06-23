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
            [tvt.a7.profedit.profile :as prof]
            [j18n.core :as j18n])
  (:import profedit.Profedit$Payload
           (java.security MessageDigest)
           (java.io ByteArrayOutputStream ByteArrayInputStream)
           (java.math BigInteger)))


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


(defn- valid-pld? [pld] (if (s/valid? ::pld pld)
                          true
                          (do (println (s/explain-str ::pld pld))
                              false)))


(defn- remove-zero-bc
  [coll]
  (remove #(zero? (:bc %)) coll))


(defn- remove-zero-cd-ma
  [coll]
  (remove #(or (zero? (:cd %)) (zero? (:ma %))) coll))


(defn- remove-zero-coef-map
  [m]
  (let [keys-to-modify [:coef-g1 :coef-g7 :coef-custom]
        modified-keys (merge
                       (when (:coef-g1 m)
                         {:coef-g1 (remove-zero-bc (:coef-g1 m))})
                       (when (:coef-g7 m)
                         {:coef-g7 (remove-zero-bc (:coef-g7 m))})
                       (when (:coef-custom m)
                         {:coef-custom (remove-zero-cd-ma (:coef-custom m))}))]
    (merge (apply dissoc m keys-to-modify) modified-keys)))


(defn- remove-unused-coeffs [profile bc-type]
  (->> profile
       (remove-zero-coef-map)
       (remove #(and (strings/starts-with? (name (first %)) "coef-")
                     (not= (first %) bc-type)))
       (into {})))


(defn- sw-pos-pack-dist-source [profile]
  (let [sw-pos-keys (filter #(strings/starts-with? (name %) "sw-pos-")
                            (keys profile))]
    (reduce (fn [acc sw-key]
              (let [sw-pos-map (get acc sw-key)]
                (if (= (:c-idx sw-pos-map) -1)
                  (assoc acc sw-key (-> sw-pos-map
                                        (assoc :c-idx 255)
                                        (assoc :distance-from :value)))
                  (assoc acc sw-key (-> sw-pos-map
                                        (assoc :distance 0.0)
                                        (assoc :distance-from :index))))))
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
  (dissoc
   (if (= (:distance-from sp) :value)
     (assoc sp :c-idx -1)
     (assoc sp :distance 1.0))
   :distance-from))


(defn- hydrate-profile [profile]
  (-> profile
      (update :sw-pos-a hydrate-switch-pos)
      (update :sw-pos-b hydrate-switch-pos)
      (update :sw-pos-c hydrate-switch-pos)
      (update :sw-pos-d hydrate-switch-pos)))


(defn- hydrate-pld [pld]
  (update pld :profiles #(mapv hydrate-profile %)))


(defn bytes->md5 [byte-array]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm byte-array)]
    (format "%032x" (BigInteger. 1 raw))))


(defn proto-bin-ser [pld]
  (let [data (->> pld
                  (dehydrate-pld)
                  (p/clj-map->proto-map proto-payload-mapper Profedit$Payload)
                  (p/proto-map->bytes))
        checksum (bytes->md5 data)
        _ (println "SUM " checksum)
        ^ByteArrayOutputStream baos (ByteArrayOutputStream.)]
    (.write baos ^"[B" (.getBytes ^String checksum "UTF-8"))
    (.write baos ^"[B" data)
    (.toByteArray baos)))


(defn proto-bin-deser [proto-bin]
  (try
    (let [checksum-size 32
          ^ByteArrayInputStream bais (ByteArrayInputStream. proto-bin)
          checksum-bytes (byte-array checksum-size)
          _ (.read bais checksum-bytes)
          checksum (String. checksum-bytes "UTF-8")
          remaining-bytes (byte-array (- (count proto-bin) checksum-size))
          _ (.read bais remaining-bytes)
          data (p/bytes->proto-map
                proto-payload-mapper
                Profedit$Payload
                remaining-bytes)
          deser-data (hydrate-pld (p/proto-map->clj-map data))]
      (if (= checksum (bytes->md5 (p/proto-map->bytes data)))
        deser-data
        (throw (Exception. "Checksum doesn't match."))))
    (catch Exception e (println (.getMessage e)) nil)))


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
                      (do (ass/pop-report! (prof/val-explain ::pld bad-pld))
                          (j18n/resource ::reported-err))
                      (j18n/resource ::failed-to-parse-data)))
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
