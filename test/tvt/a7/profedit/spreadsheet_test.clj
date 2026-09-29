(ns tvt.a7.profedit.spreadsheet-test
  (:require [clojure.test :refer [deftest is testing]]
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
