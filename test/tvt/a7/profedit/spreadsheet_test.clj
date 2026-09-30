(ns tvt.a7.profedit.spreadsheet-test
  (:require [clojure.test :refer [deftest is testing]]
            [tvt.a7.profedit.distances :as distances]
            [tvt.a7.profedit.profile :as prof]
            [tvt.a7.profedit.widgets :as widgets]
            [tvt.a7.profedit.spreadsheet :as spreadsheet])
  (:import [java.io File]
           [java.util Calendar Date]
           [org.apache.poi.ss.usermodel Cell CellType Sheet Workbook]))


(deftest workbook-create-save-load-contract
  (let [file (File/createTempFile "archerbc2-spreadsheet-" ".xlsx")
        ^Workbook created (spreadsheet/create-workbook
                           "Distances"
                           [["Distance" "Enabled"] [100.5 true] [200.0 false]])]
    (.deleteOnExit file)
    (try
      (let [^Sheet sheet (first (spreadsheet/sheet-seq created))]
        (is (= "Distances" (.getSheetName sheet)))
        (is (identical? sheet (spreadsheet/auto-size-all-columns! sheet))))
      (is (= (.getAbsolutePath file)
             (spreadsheet/save-workbook-into-file! file created)))
      (finally
        (.close created)))
    (let [^Workbook loaded (spreadsheet/load-workbook-from-file file)]
      (try
        (let [^Sheet sheet (first (spreadsheet/sheet-seq loaded))
              rows (vec (spreadsheet/row-seq sheet))]
          (testing "headers and typed cells survive the OOXML round trip"
            (is (= ["Distance" "Enabled"]
                   (mapv str (spreadsheet/cell-seq (first rows)))))
            (is (= 100.5 (.getNumericCellValue
                          ^Cell (first (spreadsheet/cell-seq (second rows))))))
            (is (true? (.getBooleanCellValue
                        ^Cell (second (spreadsheet/cell-seq (second rows))))))
            (is (false? (.getBooleanCellValue
                         ^Cell (second (spreadsheet/cell-seq (nth rows 2))))))))
        (finally
          (.close loaded))))))


(deftest workbook-requires-name-data-pairs
  (is (thrown-with-msg? IllegalArgumentException #"name/row-data pairs"
                        (spreadsheet/create-workbook "orphan"))))


(deftest workbook-cell-value-branches
  (let [calendar (doto (Calendar/getInstance) (.setTimeInMillis 0))
        ^Workbook workbook (spreadsheet/create-workbook
                            "Types" [[nil (Date. 0) calendar :fallback]])]
    (try
      (let [cells (-> workbook spreadsheet/sheet-seq first
                      spreadsheet/row-seq first spreadsheet/cell-seq vec)]
        (is (= CellType/BLANK (.getCellType ^Cell (nth cells 0))))
        (is (= CellType/NUMERIC (.getCellType ^Cell (nth cells 1))))
        (is (= CellType/NUMERIC (.getCellType ^Cell (nth cells 2))))
        (is (= ":fallback" (.getStringCellValue ^Cell (nth cells 3)))))
      (finally
        (.close workbook)))))


(deftest distance-import-closes-workbook-on-success-and-failure
  (doseq [[header values valid?] [["Distance" [100.0 200.0] true]
                                 ["Distance" ["invalid"] false]
                                 [nil [] false]]]
    (let [closed? (atom false)
          workbook (proxy [org.apache.poi.xssf.usermodel.XSSFWorkbook] []
                     (close [] (reset! closed? true) (let [^org.apache.poi.xssf.usermodel.XSSFWorkbook this this]
                                                   (proxy-super close))))
          state (atom prof/example)]
      (when header
        (let [sheet (.createSheet workbook "Distances")]
          (.setCellValue (.createCell (.createRow sheet 0) 0) ^String header)
          (doseq [[i value] (map-indexed vector values)]
            (.setCellValue (.createCell (.createRow sheet (int (inc i))) 0) (str value)))))
      (try
        (with-redefs [widgets/load-excel-from-chooser (constantly workbook)]
          (#'distances/import-from-excel state))
        (is @closed? "the import owns and closes the workbook, including on errors")
        (is (= valid? (prof/status-ok?)))
        (when (= values ["invalid"])
          (is (re-find #"1.*3000" (prof/status-text))))
        (if valid?
          (is (= [100.0 200.0] (get-in @state [:profile :distances])))
          (is (= prof/example @state)))
        (finally (.close workbook))))))
