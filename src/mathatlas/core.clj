(ns mathatlas.core
  (:require [mathatlas.parser :as parser]
            [mathatlas.site   :as site]
            [babashka.fs      :as fs]))

(defn load-notes
  "Glob all .tex files under `notes-dir` and parse them into math objects."
  [notes-dir]
  (->> (fs/glob notes-dir "**.tex")
       (mapcat parser/parse-file)
       vec))

(defn -main [& args]
  (let [notes-dir  (or (first args)  "notes")
        output-dir (or (second args) "public")]
    (println (str "Loading .tex files from '" notes-dir "'..."))
    (let [objects (load-notes notes-dir)]
      (println (str "Parsed " (count objects) " objects."))
      (println (str "Generating site in '" output-dir "'..."))
      (site/generate-site objects output-dir)
      (println (str "Done. Open " output-dir "/index.html")))))
