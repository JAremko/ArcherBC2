(ns tvt.a7.profedit.rosetta
  (:require [clojure.spec.alpha :as s]
            [tvt.a7.profedit.asi :as ass]
            [pronto.core :as p]
            [pronto.utils :as u]
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


(s/def ::pld (s/keys :req-un [::prof/profile]))


(p/defmapper proto-payload-mapper [Profedit$Payload]
  :key-name-fn u/->kebab-case
  :enum-value-fn {"LEFT" :left
                  "RIGHT" :right
                  "G1" :g1
                  "G7" :g7
                  "CUSTOM" :custom
                  "VALUE" :value
                  "INDEX" :index})


(defn- valid-pld? [pld] (s/valid? ::pld pld))


(defn- remove-zero-bc
  [coll]
  (vec (remove #(zero? (:bc %)) coll)))


(defn- remove-zero-cd-ma
  [coll]
  (remove #(or (zero? (:cd %)) (zero? (:ma %))) coll))


(defn remove-zero-coef-rows
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


(defn- replace-bc-table-keys [bc-table]
  (mapv (fn [m]
          {:bc-cd (m :bc (m :cd))
           :mv (m :mv (m :ma))}) bc-table))


(defn profile->bc-type-sel [profile]
  (keyword (str "coef-" (name (:bc-type profile)))))


(defn repeating-speeds? [profile]
  (let [bc-type (:bc-type profile)
        values (case bc-type
                 :g1 (map :mv (:coef-g1 profile))
                 :g7 (map :mv (:coef-g7 profile))
                 :custom (map :ma (:coef-custom profile))
                 [])
        freqs (frequencies values)]
    (some #(> % 1) (vals freqs))))


(defn dehydrate-pld [{:keys [profile] :as pld}]
  (let [conf-profile (s/conform ::prof/profile profile)
        bc-type-sel (profile->bc-type-sel conf-profile)
        bc-table (get (remove-zero-coef-rows conf-profile) bc-type-sel)
        bc-table-with-renamed-keys (replace-bc-table-keys bc-table)
        report-err #(->> % j18n/resource str Exception. throw)]
    (assoc pld
           :profile
           (-> conf-profile
               (update :zero-x (partial * -1))
               (dissoc :coef-custom)
               (dissoc :coef-g1)
               (dissoc :coef-g7)
               (assoc :coef-rows bc-table-with-renamed-keys)
               (update :coef-rows
                       (fn [rows]
                         (cond
                           (repeating-speeds? profile)
                           (report-err ::profile-bc-repeating-speeds)
                           (empty? rows)
                           (report-err ::profile-bc-table-err)
                           :else (vec (sort-by #(- (:mv %)) rows)))))))))


(defn- replace-bc-table-keys-reverse [v new-keys]
  (mapv (fn [m]
         {(first new-keys) (m :bc-cd)
          (second new-keys) (m :mv)}) v))


(defn- hydrate-pld [{:keys [profile] :as pld}]
  (let [bc-type-sel (profile->bc-type-sel profile)
        bc-type (:bc-type profile)
        bc-table (:coef-rows profile)
        bc-table-renamed (replace-bc-table-keys-reverse
                          bc-table
                          (if (= bc-type :custom) [:cd :ma] [:bc :mv]))
        cr-dummy [{:cd 0.0 :ma 0.0}]
        g1-g7-dummy [{:bc 0.0 :mv 0.0}]]
    (assoc pld
           :profile
           (as-> profile p
             (update p :zero-x (partial * -1))
             (update p :device-uuid #(if (seq %) % ""))
             (update p :caliber #(if (seq %)
                                   %
                                   (j18n/resource ::empty-cal-ph)))
             (update p :distances (partial mapv double))
             (assoc p bc-type-sel bc-table-renamed)
             (dissoc p :coef-rows)
             (update p :coef-custom #(or % cr-dummy))
             (update p :coef-g1 #(or % g1-g7-dummy))
             (update p :coef-g7 #(or % g1-g7-dummy))
             (s/unform ::prof/profile p)))))


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
        ^ByteArrayOutputStream baos (ByteArrayOutputStream.)]
    (.write baos ^"[B" (.getBytes ^String checksum "UTF-8"))
    (.write baos ^"[B" data)
    (.toByteArray baos)))


(defn proto-bin-deser [proto-bin]
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
        deser-data (hydrate-pld (p/proto-map->clj-map data))
        calc-sum (bytes->md5 remaining-bytes)]
    (if (= checksum calc-sum)
      deser-data
      (throw (Exception. (str "Checksum doesn't match.\n"
                              "  Saved md5: " checksum "\n"
                              "  Actual md5: " calc-sum "\n"))))))


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
