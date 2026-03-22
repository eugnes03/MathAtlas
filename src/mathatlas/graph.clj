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

(defn compute-depth
  "Compute the depth of a node in the dependency DAG.
   Depth = length of the longest dependency chain starting from this node.
   Leaf nodes (no dependencies) have depth 0."
  [graph id]
  (let [memo (atom {})]

    (letfn [(depth [node-id]
              (if-let [cached (@memo node-id)]
                cached
                (let [deps (find-dependencies graph node-id)
                      d (if (empty? deps)
                          0
                          (inc (apply max (map depth deps))))]
                  (swap! memo assoc node-id d)
                  d)))]

      (depth id))))


  
  
  
