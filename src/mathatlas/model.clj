(ns mathatlas.model)

(def object-types
  "Recognized LaTeX environment names."
  #{:theorem :lemma :definition :problem
    :example :remark :proof :corollary :proposition})

(defn make-id
  "Deterministic ID derived from source file + type + title.
   Stable across re-runs so links don't break."
  [source-file type title]
  (format "%08x" (bit-and (hash (str source-file type title)) 0xFFFFFFFF)))

(defn make-object [type title latex source-file area]
  {:id          (make-id source-file type title)
   :type        type
   :title       (or title "")
   :latex       latex
   :source-file source-file
   :area        area
   :proof-latex nil
   :concepts    []})
