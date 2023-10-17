(defproject updater "2.1.0"

  :description "Profile editor updater"

  :url "https://github.com/JAremko/ArcherBC2"

  :license {:name "GNU LESSER GENERAL PUBLIC LICENSE v3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [seesaw "1.5.0"]
                 [clj-http "3.12.3"]]

  :uberjar-name "update.jar"

  :main updater.core

  :global-vars {*warn-on-reflection* true
                *assert* true}

  :profiles {:uberjar
             {:aot :all
              :jvm-opts
              ["-Dclojure.compiler.elide-meta=[:doc :file :line :added]"
               "-Dclojure.compiler.direct-linking=true"]
              :global-vars {*warn-on-reflection* false *assert* false}}})
