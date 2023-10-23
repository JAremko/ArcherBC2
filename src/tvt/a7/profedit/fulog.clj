(ns tvt.a7.profedit.fulog
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def ^:private url
  (str "https://aelrii29fl.execute-api.eu-central-1.amazonaws.com/"
       "archerbc2-update-log"))


(defn push [data-map]
  (let [headers {"Content-Type" "application/json"}
        body (json/generate-string data-map)
        response (try
                   (http/post url {:headers headers :body body})
                   (catch Exception _ nil))]

    (if (and response (= 200 (:status response)))
      (:body response)
      nil)))
