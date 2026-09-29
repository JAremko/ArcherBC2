;  Copyright (c) Dave Ray, Meikel Brandmeyer 2011. All rights reserved.

;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this
;   distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; ArcherBC2 local EPL-1.0 fork of Seesaw 1.5.0: Java 8 type hints and
;; non-reflective equivalents only. Public API and behavior remain compatible.

(ns seesaw.forms
  (:import
    javax.swing.JPanel
    com.jgoodies.forms.builder.DefaultFormBuilder
    com.jgoodies.forms.layout.FormLayout
    com.jgoodies.forms.layout.ConstantSize)
  (:require
    seesaw.core)
  (:use
    [seesaw.options :only (bean-option default-option apply-options ignore-options option-map option-provider)]
    [seesaw.util :only (resource)]))

(defprotocol ComponentSpec
  (append [this builder] "Add the given component to the form builder"))

(extend-protocol ComponentSpec
  Object
  (append [this builder]
    (.append ^DefaultFormBuilder builder (seesaw.core/make-widget this)))
  String
  (append [this builder]
    (.append ^DefaultFormBuilder builder this))
  clojure.lang.Keyword
  (append [this builder]
    (append (resource this) builder)))

(defn span
  "Add the given component spanning several columns."
  [component column-span]
  (reify
    ComponentSpec
    (append [this builder]
      (.append ^DefaultFormBuilder builder
               (seesaw.core/make-widget component) (int column-span)))))

(defn next-line
  "Continue with the nth next line in the builder."
  ([] (next-line 1))
  ([n]
   (reify
     ComponentSpec
     (append [this builder]
       (.nextLine ^DefaultFormBuilder builder n)))))

(defn next-column
  "Continue with the nth next column in the builder."
  ([] (next-column 1))
  ([n]
   (reify
     ComponentSpec
     (append [this builder]
       (.nextLine ^DefaultFormBuilder builder n)))))

(defn title
  "Adds the given title to the form."
  [title]
  (reify
    ComponentSpec
    (append [this builder]
      (.appendTitle ^DefaultFormBuilder builder (resource title)))))

(defn separator
  "Adds a separator with an optional label to the form."
  ([]
   (reify
     ComponentSpec
     (append [this builder]
       (.appendSeparator ^DefaultFormBuilder builder))))
  ([label]
   (reify
     ComponentSpec
     (append [this builder]
       (.appendSeparator ^DefaultFormBuilder builder (resource label))))))

(defn group
  "Group the rows of the contained items into a row group."
  [& items]
  (reify
    ComponentSpec
    (append [this builder]
      (.setRowGroupingEnabled ^DefaultFormBuilder builder true)
      (doseq [item items] (append item builder))
      (.setRowGroupingEnabled ^DefaultFormBuilder builder false))))

(def ^{:private true} layout-options
  (option-map
    (default-option
      :column-groups
      (fn set-column-groups [c v]
        (cond
          (instance? FormLayout c)
            (.setColumnGroups ^FormLayout c (into-array (map int-array v)))
          :else
            (set-column-groups (.getLayout ^java.awt.Container c) v))))))

(def ^{:private true} builder-options
  (option-map
    (default-option :items
      (fn [^DefaultFormBuilder builder items]
        (doseq [item items] (append item builder))))
    (default-option :default-dialog-border?
      (fn [^DefaultFormBuilder builder value]
        (when value (.setDefaultDialogBorder builder))))
    (bean-option :default-row-spec DefaultFormBuilder)
    (bean-option :leading-column-offset DefaultFormBuilder)
    (default-option :line-gap-size
      (fn [^DefaultFormBuilder builder ^ConstantSize value]
        (.setLineGapSize builder value)))
    (default-option :paragraph-gap-size
      (fn [^DefaultFormBuilder builder ^ConstantSize value]
        (.setParagraphGapSize builder value)))))


(def ^{:private true} ignore-builder-options
  (ignore-options builder-options))

(def ^{:private true} ignore-layout-options
  (ignore-options layout-options))

(option-provider DefaultFormBuilder (merge builder-options ignore-layout-options))
(option-provider FormLayout (merge layout-options ignore-builder-options))



(defn ^JPanel forms-panel
  "Construct a panel with a FormLayout. The column spec is
  expected to be a FormLayout column spec in string form.

  The items are a list of strings, components or any of the
  combinators. For example:

      :items [\"Login\" (text) (next-line)
              \"Password\" (span (text) 3)]

  Takes the following special properties. They correspond
  to the DefaultFormBuilder option of the same name.

      :default-dialog-border?
      :default-row-spec
      :leading-column-offset
      :line-gap-size
      :paragraph-gap-size

  See http://www.jgoodies.com/freeware/forms/index.html"
  {:seesaw {:class `JPanel}}
  [column-spec & opts]
  (let [layout  (FormLayout. ^String column-spec "")
        panel   (seesaw.core/construct JPanel)
        builder (DefaultFormBuilder. layout panel)]
    (apply-options layout opts)
    (apply-options builder opts)
    (doto (.getPanel builder)
      (apply-options opts))))
