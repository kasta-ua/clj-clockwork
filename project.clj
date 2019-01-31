(defproject ua.kasta/clockwork "1.1.0"
  :description "Devtools library - server-side component"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/kasta-ua/clj-clockwork"

  :source-paths ["src"]

  :dependencies
  [[org.slf4j/slf4j-api "1.7.25"]
   [ch.qos.logback/logback-classic "1.2.3"]
   [ring/ring-codec "1.1.1"]
   [metosin/jsonista "0.2.2"]])
