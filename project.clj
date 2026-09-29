(def protobuf-version "3.23.2")


(defproject Profedit "1.21.9"

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
                 [com.moandjiezana.toml/toml4j "0.7.2"]
                 [com.github.weisj/darklaf-core "3.0.2"]
                 [org.apache.logging.log4j/log4j-core "2.21.0"]
                 [instaparse "1.4.12"]
                 [org.apache.poi/poi-ooxml "5.2.3"
                  :exclusions [commons-io
                               org.apache.commons/commons-compress]]]

  :uberjar-name "profedit.jar"

  :profiles {:dev
             {:jvm-opts ["-Drepl=true"]
              :global-vars {*warn-on-reflection* true *assert* true}
              :dependencies [[metosin/malli "0.16.4"
                              :exclusions [org.clojure/clojure]]]
              :plugins [[com.appsflyer/lein-protodeps "1.0.5"]
                        [lein-cloverage "1.2.4"]
                        ;; lein dependency-check --output-directory /tmp
                        [com.livingsocial/lein-dependency-check "1.4.0"]
                        ;; lein ns-dep-graph
                        [lein-ns-dep-graph "0.4.0-SNAPSHOT"]]}
             :test
             {:jvm-opts ["-Drepl=false"]}
             :lint
             {:dependencies [[clj-kondo/clj-kondo "2026.01.19"
                              :exclusions [org.clojure/clojure]]]
              :jvm-opts ["-Drepl=false"]}
             :uberjar
             {:aot :all
              :pedantic? :abort
              :local-repo "lib"
              :jvm-opts
              ["-Dclojure.compiler.elide-meta=[:doc :file :line :added]"
               "-Dclojure.compiler.direct-linking=true"]
              :global-vars {*warn-on-reflection* true *assert* false}}}

  :cloverage {:fail-threshold 80
              :high-watermark 80
              :html? true
              :lcov? true
              ;; The measured gate covers deterministic application logic.
              ;; Swing assembly/entrypoints and the vendored Seesaw
              ;; compatibility namespaces have lifecycle/regression tests,
              ;; including the manual live-display leak canary, but are not
              ;; stable line-coverage targets on headless CI hosts.
              :ns-exclude-regex
              [#"seesaw\..*"
               #"tvt\.a7\.profedit\.(actions|app|asi|ballistic|calc|distances|frames|fulog|nullableinp|repl|util|widgets|wizard)"
               ;; Pronto's defmapper expands this small namespace into 7,029
               ;; synthetic forms. Its real serialization paths remain covered
               ;; by binary, JSON, checksum, malformed-data and generated
               ;; round-trip tests; excluding instrumentation keeps the forms
               ;; half of Cloverage's threshold meaningful.
               #"tvt\.a7\.profedit\.rosetta"]
              :output "target/coverage"
              :summary? true}

  :aliases {"lint" ["with-profile" "+lint" "run" "-m" "clj-kondo.main"
                     "--lint" "src" "test"
                     ".github/workflows/updater/src"
                     ".github/workflows/updater/test"
                     "--fail-level" "warning"
                     "--cache" "false"]}

  :lein-protodeps {:output-path "src/java"
                   :proto-version ~protobuf-version
                   :repos {:examples {:repo-type :filesystem
                                      :config {:path "."}
                                      :proto-paths ["resources"]
                                      :dependencies [resources/proto]}}})
