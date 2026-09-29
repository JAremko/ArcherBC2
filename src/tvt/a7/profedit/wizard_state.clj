(ns tvt.a7.profedit.wizard-state)


(def ^:private coefficient-keys [:coef-g1 :coef-g7 :coef-custom])


(defn state-summary
  "Returns diagnostic wizard state without profile names or notes."
  [state]
  (let [profile (:profile state)]
    {:bc-type (:bc-type profile)
     :coefficient-rows
     (into {}
           (map (fn [coefficient-key]
                  [coefficient-key
                   (mapv (fn [row] (vec (sort (keys row))))
                         (get profile coefficient-key))]))
           coefficient-keys)
     :unexpected-root-coefficient-keys
     (vec (filter #(contains? state %) coefficient-keys))}))


(defn ensure-single-bc-mv [state bc-c-key]
  (update-in state
             [:profile bc-c-key 0]
             (fn [row]
               (let [row (or row {})]
                 (if (contains? row :mv)
                   row
                   (assoc row :mv 0.0))))))
