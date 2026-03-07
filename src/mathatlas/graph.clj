(ns mathatlas.graph)

(defn build-graph
  "Build a dependency graph from a collection of objects.
   Edges point from dependent → dependency (i.e. :depends-on direction)."
  [objects]
  {:nodes (mapv #(select-keys % [:id :type :title]) objects)
   :edges (vec (for [obj    objects
                     dep-id (:depends-on obj)]
                 {:from (:id obj) :to dep-id}))})

(defn find-dependencies
  "Return the IDs this object directly depends on."
  [graph id]
  (->> (:edges graph)
       (filter #(= (:from %) id))
       (mapv :to)))

(defn find-dependents
  "Return the IDs of objects that directly depend on this object."
  [graph id]
  (->> (:edges graph)
       (filter #(= (:to %) id))
       (mapv :from)))
