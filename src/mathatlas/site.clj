(ns mathatlas.site
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [html5]]
            [clojure.string :as str]
            [mathatlas.graph :as graph]))

;; ---------------------------------------------------------------------------
;; Data
;; ---------------------------------------------------------------------------

(def type-colors
  {:theorem     "#3B82F6"
   :lemma       "#8B5CF6"
   :definition  "#10B981"
   :problem     "#EF4444"
   :example     "#F59E0B"
   :remark      "#6B7280"
   :proof       "#14B8A6"
   :corollary   "#EC4899"
   :proposition "#6366F1"})

(def area-meta
  {"Category Theory"       {:color "#7C3AED"
                             :desc  "Functors, natural transformations, limits, adjunctions, and universal properties."}
   "Topology"              {:color "#2563EB"
                             :desc  "Open sets, continuity, compactness, connectedness, and fundamental groups."}
   "Representation Theory" {:color "#6366F1"
                             :desc  "Group actions on vector spaces, characters, modules, and Schur functors."}
   "Neural Networks"       {:color "#059669"
                             :desc  "Architectures, optimization, generalization, and learning theory."}
   "Probability Theory"    {:color "#D97706"
                             :desc  "Measure-theoretic probability, distributions, and stochastic processes."}})

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn obj-color [obj]
  (get type-colors (:type obj) "#888"))

(defn type-label [t]
  (-> t name str/capitalize))

(defn truncate [s n]
  (if (> (count s) n) (str (subs s 0 n) "…") s))

(defn area-slug [area]
  (-> area str/lower-case (str/replace #"\s+" "-") (str/replace #"[^a-z0-9-]" "")))

(defn area-color [area]
  (get-in area-meta [area :color] "#6b7280"))

(defn strip-text-commands
  "Remove LaTeX text-mode command wrappers, keeping their content.
   Used for plain-text previews where HTML output isn't possible."
  [s]
  (-> s
      (str/replace #"\\textbf\{([^}]*)\}"        "$1")
      (str/replace #"\\textit\{([^}]*)\}"        "$1")
      (str/replace #"\\emph\{([^}]*)\}"          "$1")
      (str/replace #"\\texttt\{([^}]*)\}"        "$1")
      (str/replace #"\\text\{([^}]*)\}"          "$1")
      (str/replace #"\\begin\{(enumerate|itemize)\}" "")
      (str/replace #"\\end\{(enumerate|itemize)\}"   "")
      (str/replace #"\\item\s*"                  "• ")
      (str/replace #"\\label\{[^}]*\}"           "")
      (str/replace #"\\(?:ref|eqref|autoref|cref)\{[^}]*\}" "")))

(defn render-body
  "HTML-escape raw LaTeX then convert common text-mode commands to HTML.
   Returns a plain HTML string; math delimiters are left intact for KaTeX."
  [latex]
  (-> latex
      (str/replace "&"   "&amp;")
      (str/replace "<"   "&lt;")
      (str/replace ">"   "&gt;")
      (str/replace #"\\textbf\{([^}]*)\}"  "<strong>$1</strong>")
      (str/replace #"\\textit\{([^}]*)\}"  "<em>$1</em>")
      (str/replace #"\\emph\{([^}]*)\}"    "<em>$1</em>")
      (str/replace #"\\texttt\{([^}]*)\}"  "<code>$1</code>")
      (str/replace #"\\text\{([^}]*)\}"    "$1")
      (str/replace #"\\label\{[^}]*\}" "")
      (str/replace #"(?s)\\begin\{enumerate\}(.*?)\\end\{enumerate\}"
                   (fn [[_ items]]
                     (str "<ol class=\"latex-list\">"
                          (str/replace items #"\\item\s*" "<li>")
                          "</ol>")))
      (str/replace #"(?s)\\begin\{itemize\}(.*?)\\end\{itemize\}"
                   (fn [[_ items]]
                     (str "<ul class=\"latex-list\">"
                          (str/replace items #"\\item\s*" "<li>")
                          "</ul>")))))

;; ---------------------------------------------------------------------------
;; KaTeX
;; ---------------------------------------------------------------------------

(defn katex-script []
  ;; auto-render is loaded after katex.min.js; this fires once the DOM is ready.
  [:script
   "document.addEventListener('DOMContentLoaded', function () {
  renderMathInElement(document.body, {
    delimiters: [
      {left: '$$',              right: '$$',              display: true},
      {left: '$',               right: '$',               display: false},
      {left: '\\\\(',           right: '\\\\)',           display: false},
      {left: '\\\\[',           right: '\\\\]',           display: true},
      {left: '\\\\begin{align}',  right: '\\\\end{align}',  display: true},
      {left: '\\\\begin{align*}', right: '\\\\end{align*}', display: true}
    ],
    macros: {
      '\\\\Hom': '\\\\operatorname{Hom}'
    },
    throwOnError: false
  });
});"])

;; ---------------------------------------------------------------------------
;; Layout shell
;; ---------------------------------------------------------------------------

(defn nav-bar [root]
  [:nav
   [:a.brand {:href (str root "index.html")} "Math" [:span "Atlas"]]
   [:div {:style "flex:1"}]
   [:div.nav-links
    [:a {:href (str root "objects.html")} "Objects"]
    [:a {:href (str root "areas.html")} "Areas"]]])

(defn page-shell
  "Wrap `body` forms in a full HTML5 page with nav, KaTeX, and stylesheet."
  [title root & body]
  (html5
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str title " — MathAtlas")]
     [:link {:rel "stylesheet"
             :href "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css"}]
     [:link {:rel "stylesheet" :href (str root "style.css")}]]
    [:body
     (nav-bar root)
     [:main.container body]
     [:script {:src "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"}]
     [:script {:src "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"}]
     (katex-script)
     [:script {:src "https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"}]
     [:script "mermaid.initialize({startOnLoad:true, theme:'neutral', securityLevel:'loose'});"]
     [:script {:src "https://cdn.jsdelivr.net/npm/dagre@0.8.5/dist/dagre.min.js"}]
     [:script {:src "https://cdn.jsdelivr.net/npm/cytoscape@3/dist/cytoscape.min.js"}]
     [:script {:src "https://cdn.jsdelivr.net/npm/cytoscape-dagre@2/cytoscape-dagre.js"}]]))

;; ---------------------------------------------------------------------------
;; Shared components
;; ---------------------------------------------------------------------------

(defn obj-card
  "Summary card used on index, objects, and courses pages."
  [obj root]
  (let [color (obj-color obj)
        title (not-empty (:title obj))]
    [:div.card {:data-type (name (:type obj))
                :style     (str "border-left-color:" color)}
     [:a {:href (str root "objects/" (:id obj) ".html")}
      [:div.card-header
       [:span.type-badge {:style (str "background:" color)}
        (type-label (:type obj))]
       (when title [:span.card-title title])]
      [:div.card-meta
       [:a.area-link {:href (str root "areas/" (area-slug (:area obj)) ".html")}
        (:area obj)]
       " · " (:source-file obj)]
      [:div.card-body (truncate (strip-text-commands (:latex obj)) 220)]]]))

;; ---------------------------------------------------------------------------
;; Pages
;; ---------------------------------------------------------------------------

(defn index-page [objects root]
  (let [by-type (group-by :type objects)
        by-area (group-by :area objects)]
    (page-shell "Home" root
      [:h1 "MathAtlas"]
      [:p.subtitle "A structured knowledge base of mathematical objects."]
      [:div.stats
       [:div.stat [:div.stat-num (count objects)]  [:div.stat-label "Objects"]]
       [:div.stat [:div.stat-num (count by-type)]  [:div.stat-label "Types"]]
       [:div.stat [:div.stat-num (count by-area)]  [:div.stat-label "Areas"]]]
      [:h2 "Recent Objects"]
      (map #(obj-card % root) (take 10 objects))
      [:p.view-all
       [:a {:href (str root "objects.html")} "View all objects →"]])))

(defn objects-page [objects root]
  (let [types (distinct (map :type objects))]
    (page-shell "All Objects" root
      [:h1 "All Objects"]
      [:p.subtitle (str (count objects) " objects across all areas")]
      [:div.filters
       [:button.filter-btn.active
        {:data-type "all" :data-color "#1c1c2e" :onclick "filterCards('all')"}
        "All"]
       (map (fn [t]
              [:button.filter-btn
               {:data-type  (name t)
                :data-color (get type-colors t "#888")
                :onclick    (str "filterCards('" (name t) "')")}
               (type-label t)])
            types)]
      (map #(obj-card % root) objects)
      [:script
       "function filterCards(type) {
  document.querySelectorAll('.card').forEach(function (c) {
    c.style.display = (type === 'all' || c.dataset.type === type) ? '' : 'none';
  });
  document.querySelectorAll('.filter-btn').forEach(function (b) {
    var active = b.dataset.type === type;
    b.classList.toggle('active', active);
    b.style.background  = active ? (b.dataset.color || '#1c1c2e') : '';
    b.style.borderColor = active ? (b.dataset.color || '#1c1c2e') : '';
    b.style.color       = active ? 'white' : '';
  });
}"])))

(defn areas-index-page [objects root]
  (let [by-area   (group-by :area objects)
        all-areas (distinct (concat (keys area-meta) (keys by-area)))]
    (page-shell "Areas" root
      [:h1 "Areas"]
      [:p.subtitle "Choose a mathematical area to explore."]
      [:div.areas-grid
       (map (fn [area]
              (let [n     (count (get by-area area []))
                    color (area-color area)
                    desc  (get-in area-meta [area :desc] "")]
                [:a.area-card {:href  (str root "areas/" (area-slug area) ".html")
                               :style (str "--accent:" color)}
                 [:div.area-card-body
                  [:div.area-card-name area]
                  (when (not-empty desc) [:div.area-card-desc desc])
                  [:div.area-card-count
                   (str n " object" (when (not= 1 n) "s"))]]]))
            all-areas)])))

(defn- json-str [s]
  (-> (or s "")
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")
      (str/replace "\r" "")))

(defn- area-graph [area-objects gr root]
  (when (seq area-objects)
    (let [area-ids (set (map :id area-objects))
          nodes-js (str/join ","
                    (map (fn [obj]
                           (str "{\"id\":\"" (:id obj)
                                "\",\"label\":\"" (json-str (or (not-empty (:title obj)) (type-label (:type obj))))
                                "\",\"type\":\"" (name (:type obj))
                                "\",\"color\":\"" (get type-colors (:type obj) "#888")
                                "\",\"href\":\"" root "objects/" (:id obj) ".html\"}"))
                         area-objects))
          edges-js (str/join ","
                    (map (fn [{:keys [from to]}]
                           (str "{\"source\":\"" to "\",\"target\":\"" from "\"}"))
                         (filter (fn [{:keys [from to]}]
                                   (and (area-ids from) (area-ids to)))
                                 (:edges gr))))
          present-types (->> area-objects (map :type) distinct
                             (filter #(contains? type-colors %)))]
      [:div.area-graph-section
       [:div.area-graph-label "Knowledge Graph"]
       [:div#area-graph]
       [:div#graph-tooltip]
       [:div.graph-legend
        (map (fn [t]
               [:span.legend-item
                {:style (str "--dot:" (get type-colors t))}
                (type-label t)])
             present-types)]
       [:script
        (str "(function(){
  var nodes=[" nodes-js "];
  var edges=[" edges-js "];
  function init(){
    if(typeof cytoscape==='undefined'||typeof cytoscapeDagre==='undefined'){setTimeout(init,100);return;}
    cytoscape.use(cytoscapeDagre);
    var cy=cytoscape({
      container:document.getElementById('area-graph'),
      elements:{
        nodes:nodes.map(function(n){return{data:n};}),
        edges:edges.map(function(e){return{data:e};})
      },
      layout:{name:'dagre',rankDir:'LR',nodeSep:60,rankSep:100,padding:40,edgeSep:20},
      style:[
        {selector:'node',style:{
          'background-color':'data(color)',
          'width':20,'height':20,
          'border-width':2,'border-color':'data(color)','border-opacity':0.3
        }},
        {selector:'edge',style:{
          'width':1.5,'line-color':'#d1d5db',
          'target-arrow-color':'#d1d5db','target-arrow-shape':'triangle',
          'curve-style':'bezier','arrow-scale':0.8,'opacity':0.8
        }},
        {selector:'edge:hover',style:{
          'line-color':'#9ca3af','target-arrow-color':'#9ca3af','width':2.5,'opacity':1
        }}
      ]
    });
    var tooltip=document.getElementById('graph-tooltip');
    cy.on('mouseover','node',function(e){
      var d=e.target.data();
      tooltip.innerHTML='<span class=\"gtt-type\">'+d.type+'</span>'+d.label;
      tooltip.style.display='block';
      e.target.animate({'style':{'width':28,'height':28,'border-opacity':1}},{duration:120});
    });
    cy.on('mousemove','node',function(e){
      var oe=e.originalEvent;
      tooltip.style.left=(oe.clientX+14)+'px';
      tooltip.style.top=(oe.clientY-12)+'px';
    });
    cy.on('mouseout','node',function(e){
      tooltip.style.display='none';
      e.target.animate({'style':{'width':20,'height':20,'border-opacity':0.3}},{duration:120});
    });
    cy.on('tap','node',function(e){window.location.href=e.target.data('href');});
  }
  init();
})();")]])))

(defn area-detail-page [area objects gr root]
  (let [color (area-color area)
        desc  (get-in area-meta [area :desc] "")]
    (page-shell area root
      [:a.back {:href (str root "areas.html")} "← Areas"]
      [:div.area-detail-header {:style (str "border-top-color:" color)}
       [:h1 area]
       (when (not-empty desc) [:p.subtitle desc])
       [:p.area-detail-count {:style (str "color:" color)}
        (str (count objects) " object" (when (not= 1 (count objects)) "s"))]]
      (if (empty? objects)
        [:p.empty-state "No objects in this area yet."]
        (list
          (area-graph objects gr root)
          [:h2 "Objects"]
          (map #(obj-card % root) objects))))))

(defn- mermaid-safe [s]
  (str/replace (or s "") #"\"" "'"))

(defn- mermaid-graph [obj deps dependents root]
  (when (or (seq deps) (seq dependents))
    (let [nid      #(str "n" (:id %))
          nlabel   #(str (nid %) "[\"" (mermaid-safe (or (not-empty (:title %)) (type-label (:type %)))) "\"]")
          all-nodes (->> (concat deps [obj] dependents)
                         (group-by :id) vals (map first))
          lines (concat
                  (map #(str "  " (nlabel %) (when (= (:id %) (:id obj)) ":::current")) all-nodes)
                  (map #(str "  " (nid %) " --> " (nid obj)) deps)
                  (map #(str "  " (nid obj) " --> " (nid %)) dependents)
                  (keep #(when-not (= (:id %) (:id obj))
                           (str "  click " (nid %) " \"" root "objects/" (:id %) ".html\" \"_self\""))
                        all-nodes))
          diagram (str/join "\n"
                    (concat ["flowchart LR"
                             "  classDef current fill:#6366f1,color:#fff,stroke:#4f46e5"]
                            lines))]
      [:div.dep-graph
       [:div.relation-label "Dependency Graph"]
       [:div.mermaid diagram]])))

(defn- resolve-refs
  "Replace \\ref{label} with the title of the referenced object, or strip if not found."
  [latex objects-by-id]
  (let [label->obj (->> (vals objects-by-id)
                        (keep #(when-let [l (:label %)] [l %]))
                        (into {}))]
    (str/replace latex #"\\(?:ref|eqref|autoref|cref)\{([^}]+)\}"
                 (fn [[_ lbl]]
                   (if-let [obj (get label->obj lbl)]
                     (or (not-empty (:title obj)) (type-label (:type obj)))
                     "")))))

(defn- relation-list [label items root]
  (when (seq items)
    [:div.relation-section
     [:div.relation-label label]
     [:ul.relation-list
      (map (fn [obj]
             [:li [:a {:href (str root "objects/" (:id obj) ".html")}
                   (or (not-empty (:title obj)) (type-label (:type obj)))]])
           items)]]))

(defn object-page [obj gr objects-by-id root]
  (let [color      (obj-color obj)
        title      (not-empty (:title obj))
        deps       (keep objects-by-id (graph/find-dependencies gr (:id obj)))
        dependents (keep objects-by-id (graph/find-dependents   gr (:id obj)))
        ph         "___MATHATLAS_BODY___"
        ph2        "___MATHATLAS_PROOF___"
        shell      (page-shell (or title (type-label (:type obj))) root
                    [:a.back {:href (str root "objects.html")} "← All Objects"]
                    [:div.obj-header
                     [:div.obj-type {:style (str "color:" color)} (type-label (:type obj))]
                     (when title [:h1.obj-title title])
                     [:div.obj-meta (:area obj) " · " (:source-file obj)]]
                    [:div.obj-body ph]
                    (when (:proof-latex obj)
                      [:div.proof-section
                       [:div.proof-label "Proof"]
                       [:div.obj-body ph2]])
                    (relation-list "Depends on" deps       root)
                    (relation-list "Used in"    dependents root)
                    (mermaid-graph obj deps dependents root))]
    (-> shell
        (str/replace ph  (render-body (resolve-refs (:latex obj) objects-by-id)))
        (str/replace ph2 (render-body (resolve-refs (or (:proof-latex obj) "") objects-by-id))))))

;; ---------------------------------------------------------------------------
;; CSS
;; ---------------------------------------------------------------------------

(def stylesheet
  "/* ===== MathAtlas ===== */
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  background: #f0f2f5;
  color: #111827;
  line-height: 1.6;
  font-size: 15px;
}

/* --- Nav --- */
nav {
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 2rem;
  height: 56px;
  display: flex;
  align-items: center;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.brand {
  color: #111827;
  font-size: 1rem;
  font-weight: 700;
  text-decoration: none;
  letter-spacing: -0.01em;
}
.brand span { color: #6366f1; }
.nav-links { display: flex; gap: 0.25rem; }
.nav-links a {
  color: #6b7280;
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0.4rem 0.75rem;
  border-radius: 6px;
  transition: background 0.1s, color 0.1s;
}
.nav-links a:hover { background: #f3f4f6; color: #111827; }

/* --- Layout --- */
.container { max-width: 860px; margin: 0 auto; padding: 2rem 1.5rem; }

h1 {
  font-size: 1.6rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-bottom: 0.3rem;
}
h2 {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #9ca3af;
  margin: 2rem 0 0.75rem;
}

.subtitle { color: #6b7280; font-size: 0.9rem; margin-bottom: 2rem; }
.view-all { margin-top: 1.25rem; }
.view-all a {
  font-size: 0.875rem;
  font-weight: 500;
  color: #6366f1;
  text-decoration: none;
}
.view-all a:hover { text-decoration: underline; }
a { color: #6366f1; }
a:hover { color: #4f46e5; }

/* --- Stats --- */
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
  margin-bottom: 2.5rem;
  max-width: 420px;
}
.stat {
  background: #fff;
  border-radius: 12px;
  padding: 1.25rem 1rem;
  text-align: center;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07), 0 1px 2px rgba(0,0,0,0.04);
}
.stat-num {
  font-size: 2rem;
  font-weight: 700;
  color: #6366f1;
  line-height: 1;
  letter-spacing: -0.03em;
}
.stat-label {
  font-size: 0.7rem;
  color: #9ca3af;
  margin-top: 0.3rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.07em;
}

/* --- Cards --- */
.card {
  background: #fff;
  border-radius: 10px;
  padding: 1rem 1.25rem;
  margin-bottom: 0.6rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07), 0 1px 2px rgba(0,0,0,0.04);
  transition: box-shadow 0.15s, transform 0.1s;
  border-top: 3px solid transparent;
}
.card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  transform: translateY(-1px);
}
.card > a { text-decoration: none; color: inherit; display: block; }

.card-header { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem; }
.type-badge {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #fff;
  padding: 0.15rem 0.5rem;
  border-radius: 20px;
  white-space: nowrap;
}
.card-title { font-size: 0.95rem; font-weight: 600; color: #111827; }
.card-meta { font-size: 0.75rem; color: #9ca3af; margin-bottom: 0.4rem; }
.card-body { font-size: 0.83rem; color: #6b7280; line-height: 1.5; }

/* --- Filters --- */
.filters { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-bottom: 1.5rem; }
.filter-btn {
  font-size: 0.78rem;
  font-weight: 500;
  padding: 0.3rem 0.8rem;
  border: 1.5px solid #e5e7eb;
  border-radius: 20px;
  background: #fff;
  cursor: pointer;
  color: #6b7280;
  transition: all 0.15s;
}
.filter-btn:hover { border-color: #6366f1; color: #6366f1; }
.filter-btn.active { background: #6366f1; color: #fff; border-color: #6366f1; }

/* --- Object detail page --- */
.back {
  font-size: 0.82rem;
  font-weight: 500;
  color: #9ca3af;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-bottom: 1.75rem;
  transition: color 0.1s;
}
.back:hover { color: #111827; }

.obj-header {
  background: #fff;
  border-radius: 12px;
  padding: 1.5rem 1.75rem;
  margin-bottom: 1.25rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
  border-top: 4px solid #6366f1;
}
.obj-type {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  margin-bottom: 0.4rem;
}
.obj-title { font-size: 1.5rem; font-weight: 700; letter-spacing: -0.02em; margin-bottom: 0.5rem; }
.obj-meta { font-size: 0.78rem; color: #9ca3af; }

.obj-body {
  background: #f9fafb;
  border-radius: 12px;
  padding: 1.5rem 1.75rem;
  font-family: ui-monospace, 'Cascadia Code', 'Fira Code', monospace;
  font-size: 0.84rem;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-x: auto;
  color: #374151;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
  border: 1px solid #e5e7eb;
}

/* --- Lists --- */
.latex-list { padding-left: 1.75rem; margin: 0.5rem 0; }
.latex-list li { margin-bottom: 0.4rem; }

/* --- Proof section --- */
.proof-section { margin-top: 1rem; }
.proof-label {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  color: #14B8A6;
  margin-bottom: 0.4rem;
}

/* --- Dependency graph --- */
.dep-graph {
  background: #fff;
  border-radius: 12px;
  padding: 1.25rem 1.75rem;
  margin-top: 1rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
  border: 1px solid #e5e7eb;
  overflow-x: auto;
}
.dep-graph .mermaid { margin-top: 0.75rem; }

/* --- Relations (depends-on / used-in) --- */
.relation-section { margin-top: 1rem; }
.relation-label {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  color: #9ca3af;
  margin-bottom: 0.4rem;
}
.relation-list { list-style: none; padding: 0; }
.relation-list li { margin-bottom: 0.3rem; }
.relation-list a {
  font-size: 0.88rem;
  color: #6366f1;
  text-decoration: none;
}
.relation-list a:hover { text-decoration: underline; }
.relation-list li::before { content: \"→ \"; color: #d1d5db; }

/* --- Areas index grid --- */
.areas-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem;
  margin-top: 0.5rem;
}
.area-card {
  display: block;
  background: #fff;
  border-radius: 12px;
  border-top: 4px solid var(--accent, #6b7280);
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
  text-decoration: none;
  color: inherit;
  transition: box-shadow 0.15s, transform 0.1s;
}
.area-card:hover {
  box-shadow: 0 6px 24px rgba(0,0,0,0.11);
  transform: translateY(-2px);
}
.area-card-body { padding: 1.25rem 1.5rem; }
.area-card-name {
  font-size: 1rem;
  font-weight: 700;
  color: #111827;
  margin-bottom: 0.4rem;
}
.area-card-desc {
  font-size: 0.8rem;
  color: #6b7280;
  line-height: 1.55;
  margin-bottom: 0.85rem;
}
.area-card-count {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--accent, #6b7280);
  font-family: ui-monospace, monospace;
}
.area-link {
  color: #9ca3af;
  text-decoration: none;
  font-size: inherit;
}
.area-link:hover { color: #374151; text-decoration: underline; }

/* --- Area detail page --- */
.area-detail-header {
  background: #fff;
  border-radius: 12px;
  border-top: 4px solid #6b7280;
  padding: 1.5rem 1.75rem;
  margin-bottom: 1.25rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
}
.area-detail-count {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.07em;
  font-family: ui-monospace, monospace;
  margin-top: 0.5rem;
}
.empty-state {
  text-align: center;
  color: #9ca3af;
  padding: 3rem;
  font-style: italic;
}

/* --- Area knowledge graph --- */
.area-graph-section { margin-bottom: 1.5rem; }
.area-graph-label {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  color: #9ca3af;
  margin-bottom: 0.5rem;
}
#area-graph {
  width: 100%;
  height: 480px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
  cursor: pointer;
}
#graph-tooltip {
  position: fixed;
  background: #111827;
  color: #fff;
  font-size: 0.78rem;
  font-family: 'Inter', system-ui, sans-serif;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  pointer-events: none;
  display: none;
  z-index: 1000;
  white-space: nowrap;
  box-shadow: 0 4px 14px rgba(0,0,0,0.35);
}
.gtt-type {
  font-size: 0.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.07em;
  opacity: 0.65;
  margin-right: 0.35rem;
}
.graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.6rem;
  margin-bottom: 0.25rem;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.72rem;
  color: #6b7280;
  font-weight: 500;
}
.legend-item::before {
  content: '';
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--dot, #888);
}
")

;; ---------------------------------------------------------------------------
;; Site generation
;; ---------------------------------------------------------------------------

(defn- write-page [path content]
  (let [f (java.io.File. path)]
    (.mkdirs (.getParentFile f))
    (spit f content)))

(defn generate-site [objects gr output-dir]
  (let [by-area      (group-by :area objects)
        all-areas    (distinct (concat (keys area-meta) (keys by-area)))
        objects-by-id (into {} (map (juxt :id identity) objects))]
    (write-page (str output-dir "/style.css")    stylesheet)
    (write-page (str output-dir "/index.html")   (index-page        objects ""))
    (write-page (str output-dir "/objects.html") (objects-page      objects ""))
    (write-page (str output-dir "/areas.html")   (areas-index-page  objects ""))
    (doseq [area all-areas]
      (write-page (str output-dir "/areas/" (area-slug area) ".html")
                  (area-detail-page area (get by-area area []) gr "../")))
    (doseq [obj objects]
      (write-page (str output-dir "/objects/" (:id obj) ".html")
                  (object-page obj gr objects-by-id "../")))
    (println (str "  Wrote " (+ 3 (count all-areas) (count objects) 1) " files."))))
