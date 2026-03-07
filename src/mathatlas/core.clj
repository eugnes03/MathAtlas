(ns mathatlas.core
  (:require [mathatlas.parser  :as parser]
            [mathatlas.site    :as site]
            [mathatlas.edn-io  :as edn-io]
            [mathatlas.graph   :as graph]
            [babashka.fs       :as fs]))

(def edn-path "data/objects.edn")

(defn load-notes
  "Glob all .tex files, parse into math objects, then resolve \\ref cross-references."
  [notes-dir]
  (->> (fs/glob notes-dir "**.tex")
       (mapcat parser/parse-file)
       vec
       parser/resolve-dependencies))

(defn run-parse
  "Parse notes → write EDN → generate site."
  [notes-dir output-dir]
  (println (str "Parsing .tex files from '" notes-dir "'..."))
  (let [objects (load-notes notes-dir)]
    (println (str "Parsed " (count objects) " objects."))
    (edn-io/write-edn objects edn-path)
    (println (str "Wrote " edn-path))
    (println (str "Generating site in '" output-dir "'..."))
    (site/generate-site objects (graph/build-graph objects) output-dir)
    (println (str "Done. Open " output-dir "/index.html"))))

(defn run-build
  "Read existing EDN (with hand-edited deps/tags) → generate site."
  [output-dir]
  (if (.exists (java.io.File. edn-path))
    (do
      (println (str "Loading objects from " edn-path "..."))
      (let [objects (edn-io/read-edn edn-path)]
        (println (str "Loaded " (count objects) " objects."))
        (println (str "Generating site in '" output-dir "'..."))
        (site/generate-site objects (graph/build-graph objects) output-dir)
        (println (str "Done. Open " output-dir "/index.html"))))
    (println (str "No EDN found at " edn-path ". Run without --build first."))))

(defn -main [& args]
  (let [output-dir (or (second args) "docs")]
    (if (= (first args) "--build")
      (run-build output-dir)
      (run-parse (or (first args) "notes") output-dir))))
