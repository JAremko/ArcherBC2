(ns tvt.a7.profedit.fulog
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [tvt.a7.profedit.log :as log]))

(def ^:private url
  (str "https://aelrii29fl.execute-api.eu-central-1.amazonaws.com/"
       "archerbc2-update-log"))


(defn push [data-map]
  (let [headers {"Content-Type" "application/json"}
        body (json/generate-string data-map)
        response (try
                   (http/post url {:headers headers
                                   :body body
                                   :socket-timeout 5000
                                   :conn-timeout 5000})
                   (catch Exception e
                     (log/debug! "firmware-telemetry-failed"
                                 {:message (.getMessage e)})
                     nil))]

    (if (and response (= 200 (:status response)))
      (:body response)
      nil)))
