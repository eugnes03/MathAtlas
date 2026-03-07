(ns mathatlas.edn-io
  (:require [clojure.edn    :as edn]
            [clojure.pprint :as pprint]))

(defn write-edn
  "Serialize objects to a pretty-printed EDN file.
   This file can be hand-edited to add :depends-on, :concepts, :tags."
  [objects path]
  (let [f (java.io.File. path)]
    (.mkdirs (.getParentFile f))
    (with-open [w (java.io.FileWriter. f)]
      (pprint/pprint (vec objects) w))))

(defn read-edn
  "Load objects from an EDN file (e.g. after hand-editing dependencies)."
  [path]
  (-> path slurp edn/read-string))
