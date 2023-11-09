(def protobuf-version "3.23.2")


(defproject Profedit "1.19.1"

  :description "Profile editor"

  :url "https://github.com/JAremko/profedit"

  :license {:name "GNU LESSER GENERAL PUBLIC LICENSE v3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :main tvt.a7.profedit.app

  :java-source-paths ["src/java"]
  :repl-options {:init (do
                         (use 'clojure.repl)
                         (use 'seesaw.dev))}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-http "3.12.3"]
                 [seesaw "1.5.0"]
                 [cpath-clj "0.1.2"]
                 [com.appsflyer/pronto "2.1.1"]
                 [com.google.protobuf/protobuf-java ~protobuf-version]
                 [expound "0.9.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [cheshire "5.11.0"]
                 [clojure-interop/java.nio "1.0.5"]
                 [clj-commons/fs "1.6.307"]
                 [toml "0.1.4"]
                 [com.github.weisj/darklaf-core "3.0.2"]
                 [org.apache.logging.log4j/log4j-core "2.21.0"]
                 [dk.ative/docjure "1.19.0"
                   :exclusions [commons-io
                                org.apache.commons/commons-compress]]]

  :uberjar-name "profedit.jar"

  :profiles {:dev
             {:jvm-opts ["-Drepl=true"]
              :global-vars {*warn-on-reflection* true *assert* true}
              :plugins [[com.appsflyer/lein-protodeps "1.0.5"]
                        ;; lein dependency-check --output-directory /tmp
                        [com.livingsocial/lein-dependency-check "1.4.0"]
                        ;; lein ns-dep-graph
                        [lein-ns-dep-graph "0.4.0-SNAPSHOT"]]}
             :uberjar
             {:aot :all
              :pedantic? :abort
              :local-repo "lib"
              :jvm-opts
              ["-Dclojure.compiler.elide-meta=[:doc :file :line :added]"
               "-Dclojure.compiler.direct-linking=true"]
              :global-vars {*warn-on-reflection* false *assert* false}}}

  :lein-protodeps {:output-path "src/java"
                   :proto-version ~protobuf-version
                   :repos {:examples {:repo-type :filesystem
                                      :config {:path "."}
                                      :proto-paths ["resources"]
                                      :dependencies [resources/proto]}}})
