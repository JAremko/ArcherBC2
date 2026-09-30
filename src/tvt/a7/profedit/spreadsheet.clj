(ns tvt.a7.profedit.spreadsheet
  "Small, typed Apache POI adapter for the workbook operations ArcherBC2 uses.

   Keeping this surface local avoids AOT-compiling the much larger reflective
   Docjure namespace into the release jar."
  (:require [clojure.java.io :as io])
  (:import [java.io File]
           [java.util Calendar Date]
           [org.apache.poi.ss.usermodel Cell Row Sheet Workbook WorkbookFactory]
           [org.apache.poi.xssf.usermodel XSSFWorkbook]))


(defn- set-cell-value! [^Cell cell value]
  (cond
    (nil? value) cell
    (string? value) (.setCellValue cell ^String value)
    (number? value) (.setCellValue cell (double value))
    (instance? Boolean value) (.setCellValue cell (boolean value))
    (instance? Date value) (.setCellValue cell ^Date value)
    (instance? Calendar value) (.setCellValue cell ^Calendar value)
    :else (.setCellValue cell ^String (str value)))
  cell)


(defn create-workbook
  "Creates an OOXML workbook from alternating sheet-name/row-data pairs."
  ^Workbook [& sheets-and-data]
  (when (odd? (count sheets-and-data))
    (throw (IllegalArgumentException.
            "create-workbook requires sheet-name/row-data pairs")))
  (let [^Workbook workbook (XSSFWorkbook.)]
    (doseq [[sheet-name rows] (partition 2 sheets-and-data)]
      (let [^Sheet sheet (.createSheet workbook (str sheet-name))]
        (doseq [[row-index values] (map-indexed vector rows)]
          (let [^Row row (.createRow sheet (int row-index))]
            (doseq [[column-index value] (map-indexed vector values)]
              (set-cell-value! (.createCell row (int column-index)) value))))))
    workbook))


(defn load-workbook-from-file ^Workbook [file]
  ;; Imports must not acquire write access to the user's source workbook.
  (WorkbookFactory/create ^File (io/file file) nil true))


(defn save-workbook-into-file! [file ^Workbook workbook]
  (with-open [output (io/output-stream file)]
    (.write workbook output))
  (.getAbsolutePath ^File (io/file file)))


(defn sheet-seq [^Workbook workbook]
  (iterator-seq (.sheetIterator workbook)))


(defn row-seq [^Sheet sheet]
  (iterator-seq (.rowIterator sheet)))


(defn cell-seq [^Row row]
  (iterator-seq (.cellIterator row)))


(defn auto-size-all-columns! [^Sheet sheet]
  (let [column-count (reduce max 0
                             (map #(int (.getLastCellNum ^Row %))
                                  (row-seq sheet)))]
    (dotimes [column column-count]
      (.autoSizeColumn sheet column)))
  sheet)
