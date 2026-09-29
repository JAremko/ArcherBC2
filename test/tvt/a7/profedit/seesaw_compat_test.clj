(ns tvt.a7.profedit.seesaw-compat-test
  "Behavioral contracts for the locally type-hinted Seesaw 1.5 namespaces."
  (:require [clojure.test :refer [deftest is]]
            [seesaw.chooser :as chooser]
            [seesaw.core :as sc]
            [seesaw.forms :as forms]
            [seesaw.table :as table]
            [seesaw.tree :as tree])
  (:import [java.awt.image BufferedImage]
           [javax.swing JComponent JFileChooser JLabel JPanel]
           [javax.swing.event TreeModelEvent TreeModelListener]
           [javax.swing.table TableModel]))


(deftest table-model-overload-free-row-mutations
  (let [^TableModel model
        (table/table-model
         :columns [{:key :name :class String} {:key :score :class Double}]
         :rows [{:name "first" :score 1.0 :extra :kept}])]
    (is (= 1 (table/row-count model)))
    (is (= String (.getColumnClass model 0)))
    (is (= Double (.getColumnClass model 1)))
    (is (= {:name "first" :score 1.0 :extra :kept}
           (table/value-at model 0)))
    (table/insert-at! model 0 {:name "inserted" :score 2.0 :extra :new})
    (is (= 2 (table/row-count model)))
    (is (= {:name "inserted" :score 2.0 :extra :new}
           (table/value-at model 0)))
    (table/update-at! model 1 {:name "updated" :score 3.0})
    (is (= {:name "updated" :score 3.0 :extra :kept}
           (table/value-at model 1)))
    (table/remove-at! model 0)
    (is (= ["updated" 3.0]
           [(.getValueAt model 0 0) (.getValueAt model 0 1)]))))


(deftest chooser-boolean-options-use-real-java-getters
  (let [^JFileChooser chooser
        (#'chooser/configure-file-chooser
         (JFileChooser.)
         {:multi? true :all-files? false :suggested-name "profile.a7p"})]
    (is (.isMultiSelectionEnabled chooser))
    (is (not (.isAcceptAllFileFilterUsed chooser)))
    (is (= "profile.a7p" (.getName (.getSelectedFile chooser))))))


(deftest forms-and-paint-delegate-remain-functional-headless
  (let [^JPanel panel (forms/forms-panel
                       "pref,4dlu,pref"
                       :items ["Label" (forms/span (JLabel. "Value") 1)])
        paints (atom 0)
        ^JComponent canvas (sc/canvas :paint (fn [_ _] (swap! paints inc)))
        image (BufferedImage. 20 20 BufferedImage/TYPE_INT_ARGB)
        graphics (.createGraphics image)]
    (try
      (is (= 2 (.getComponentCount panel)))
      (.setSize canvas 20 20)
      (.paint canvas graphics)
      (is (= 1 @paints))
      (finally
        (.dispose graphics)))))


(deftest tree-events-reach-typed-listeners
  (let [model (tree/simple-tree-model sequential? seq [:root [:child]])
        events (atom [])
        listener (reify TreeModelListener
                   (treeNodesChanged [_ event]
                     (swap! events conj [:changed event]))
                   (treeNodesInserted [_ event]
                     (swap! events conj [:inserted event]))
                   (treeNodesRemoved [_ event]
                     (swap! events conj [:removed event]))
                   (treeStructureChanged [_ event]
                     (swap! events conj [:structure event])))]
    (.addTreeModelListener ^javax.swing.tree.TreeModel model listener)
    (tree/node-changed model [[:root [:child]] [:child]])
    (tree/node-inserted model [[:root [:child]] [:child]])
    (tree/node-removed model [[:root [:child]]] 0 [:child])
    (tree/node-structure-changed model [[:root [:child]]])
    (is (= [:changed :inserted :removed :structure] (mapv first @events)))
    (is (every? #(instance? TreeModelEvent (second %)) @events))))
