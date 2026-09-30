(ns tvt.a7.profedit.update-test
  (:require [clj-http.client :as http]
            [clojure.test :refer [deftest is]]
            [seesaw.core :as sc]
            [tvt.a7.profedit.update :as update]))


(deftest equivalent-release-tags-do-not-prompt-for-an-update
  (doseq [[current latest expected-prompts]
          [["1.21.9" "v1.21.9" 0]
           ["v1.21.9" "1.21.9" 0]
           ["1.21.9" "1.21.9" 0]
           ["1.21.9" "v1.21.10" 1]]]
    (let [prompts (atom 0)]
      (with-redefs-fn {#'update/get-current-version (constantly current)
                       #'update/maybe-delete-update-jar (fn [])
                       #'update/maybe-recreate-update-jar (fn [])
                       #'http/get (fn [& _] {:status 200 :body [{:name latest}]})
                       #'update/ask-to-update (fn [] (swap! prompts inc) false)}
        (fn [] @(update/check-for-update) (sc/invoke-now nil)))
      (is (= expected-prompts @prompts) (str current " -> " latest)))))
