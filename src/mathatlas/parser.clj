(ns mathatlas.parser
  (:require [clojure.string :as str]
            [mathatlas.model :as model]))

(defn- extract-area
  "Read mathematical area from a  '% area: <name>'  comment anywhere in the file."
  [content]
  (some-> (re-find #"(?m)^%\s*area:\s*(.+)$" content)
          second
          str/trim))

(defn- extract-label
  "Find \\label{key} in a LaTeX block body."
  [body]
  (some-> (re-find #"\\label\{([^}]+)\}" body) second str/trim))

(defn- extract-refs
  "Find all \\ref / \\eqref / \\autoref / \\cref targets in a LaTeX body."
  [body]
  (->> (re-seq #"\\(?:ref|eqref|autoref|cref)\{([^}]+)\}" body)
       (mapv second)
       distinct
       vec))

(defn- parse-blocks
  "Extract all recognised math environment blocks from raw LaTeX.

  Supports:
    \\begin{theorem}[Optional Title]
    ...body (may span multiple lines and contain nested environments)...
    \\end{theorem}

  The backreference \\{\\1\\} ensures open/close tags match, which means
  nested environments of *different* types (e.g. align inside theorem)
  are captured correctly as part of the body."
  [content source-file area]
  (let [pattern #"(?s)\\begin\{(\w+)\}(?:\[([^\]]*)\])?(.*?)\\end\{\1\}"]
    (->> (re-seq pattern content)
         (keep (fn [[_ env title body]]
                 (let [kw (keyword env)]
                   (when (model/object-types kw)
                     (-> (model/make-object kw
                                            (some-> title str/trim not-empty)
                                            (str/trim body)
                                            source-file
                                            (or area "Uncategorized"))
                         (assoc :label (extract-label body)
                                :refs  (extract-refs body))))))))))

(def ^:private provable-types
  "Environment types that can have an associated proof."
  #{:theorem :lemma :proposition :corollary})

(defn- attach-proofs
  "Attach each \\begin{proof} block to the nearest preceding provable object
   (theorem, lemma, etc.) as :proof-latex. The proof is then removed from the
   top-level object list — it lives inside its theorem, not as a separate page.
   Orphaned proofs (no preceding provable) are kept as standalone objects."
  [objects]
  (let [[acc pending]
        (reduce
          (fn [[acc pending] obj]
            (cond
              (= :proof (:type obj))
              (if pending
                [acc (assoc pending :proof-latex (:latex obj))]
                [(conj acc obj) nil])

              (provable-types (:type obj))
              [(cond-> acc pending (conj pending)) obj]

              :else
              [(cond-> acc pending (conj pending) true (conj obj)) nil]))
          [[] nil]
          objects)]
    (cond-> acc pending (conj pending))))

(defn resolve-dependencies
  "Resolve \\ref labels to object IDs and populate :depends-on.
   Call this after loading ALL objects across all files so cross-file
   references resolve correctly."
  [objects]
  (let [label->id (->> objects
                       (keep #(when-let [l (:label %)] [l (:id %)]))
                       (into {}))]
    (mapv (fn [obj]
            (->> (:refs obj)
                 (keep label->id)
                 (remove #{(:id obj)})
                 vec
                 (assoc obj :depends-on)))
          objects)))

(defn parse-file
  "Parse a single .tex file and return a seq of math object maps."
  [file-path]
  (let [path-str (str file-path)
        content  (slurp path-str)
        area     (or (extract-area content) "Uncategorized")
        filename (.getName (java.io.File. path-str))]
    (-> (parse-blocks content filename area)
        attach-proofs)))
