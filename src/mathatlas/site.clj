(ns mathatlas.site
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [html5]]
            [clojure.string :as str]
            [mathatlas.graph :as graph]))

;; ---------------------------------------------------------------------------
;; Data
;; ---------------------------------------------------------------------------

(def type-colors
  {:theorem     "#6b7d8f"
   :lemma       "#8a6d9e"
   :definition  "#b5533c"
   :problem     "#a34a3a"
   :example     "#c9a04a"
   :remark      "#9c9080"
   :proof       "#5e8f7c"
   :corollary   "#b06a8f"
   :proposition "#5f7a9e"})

(def area-meta
  {"Category Theory"       {:desc "Functors, natural transformations, limits, adjunctions, and universal properties."}
   "Topology"              {:desc "Open sets, continuity, compactness, connectedness, and fundamental groups."}
   "Representation Theory" {:desc "Group actions on vector spaces, characters, modules, and Schur functors."}
   "Neural Networks"       {:desc "Architectures, optimization, generalization, and learning theory."}
   "Probability Theory"    {:desc "Measure-theoretic probability, distributions, and stochastic processes."}
   "Algebraic Topology"    {:desc "Homotopy theory, fundamental group, homology"}})

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn obj-color [obj]
  (get type-colors (:type obj) "#8a7f6d"))

(defn type-label [t]
  (-> t name str/capitalize))

(defn truncate [s n]
  (if (> (count s) n) (str (subs s 0 n) "…") s))

(defn area-slug [area]
  (-> area str/lower-case (str/replace #"\s+" "-") (str/replace #"[^a-z0-9-]" "")))

(defn- attr-escape
  "Escape a string for safe use inside an HTML attribute value."
  [s]
  (-> (or s "")
      (str/replace "&" "&amp;")
      (str/replace "\"" "&quot;")
      (str/replace "<" "&lt;")))

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

(defn- preview-text [obj]
  (truncate (strip-text-commands (:latex obj)) 170))

(defn- search-blob [obj]
  (-> (str (:title obj) " " (:area obj) " " (name (:type obj)) " "
           (strip-text-commands (:latex obj)))
      str/lower-case
      attr-escape))

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
;; Read-progress (localStorage) — shared across every page
;; ---------------------------------------------------------------------------

(defn- read-progress-script []
  [:script
   "(function(){
  function getReadIds(){
    try { return JSON.parse(localStorage.getItem('mathatlas_read_ids') || '{}'); }
    catch (e) { return {}; }
  }
  function paintReadDots(){
    var ids = getReadIds();
    document.querySelectorAll('.read-dot').forEach(function(el){
      el.classList.toggle('is-read', !!ids[el.dataset.objectId]);
    });
  }
  function initMarkRead(){
    var btn = document.querySelector('.mark-read');
    if (!btn) return;
    var id = btn.dataset.objectId;
    function render(){
      var ids = getReadIds();
      btn.textContent = ids[id] ? '\\u2713 Marked as read' : 'Mark as read';
    }
    render();
    btn.addEventListener('click', function(e){
      e.preventDefault();
      var ids = getReadIds();
      if (ids[id]) delete ids[id]; else ids[id] = true;
      localStorage.setItem('mathatlas_read_ids', JSON.stringify(ids));
      render();
      paintReadDots();
    });
  }
  document.addEventListener('DOMContentLoaded', function(){
    paintReadDots();
    initMarkRead();
  });
})();"])

;; ---------------------------------------------------------------------------
;; Header / search
;; ---------------------------------------------------------------------------

(defn- header-search-script [root]
  [:script
   (str "(function(){
  var input = document.getElementById('global-search');
  if (!input) return;
  input.addEventListener('input', function(){
    if (typeof window.MATHATLAS_APPLY_FILTER === 'function') window.MATHATLAS_APPLY_FILTER();
  });
  input.addEventListener('keydown', function(e){
    if (e.key !== 'Enter') return;
    e.preventDefault();
    if (typeof window.MATHATLAS_APPLY_FILTER === 'function') return;
    window.location.href = '" root "objects.html?q=' + encodeURIComponent(input.value);
  });
})();")])

(defn- nav-link [root href label active?]
  [:a.nav-link {:href (str root href) :class (when active? "active")} label])

(defn header [root active-nav]
  [:header.site-header
   [:a.brand {:href (str root "index.html")} "MathAtlas"]
   [:div.search-box
    [:input#global-search {:type "text" :placeholder "Search theorems, definitions…"}]]
   [:div.spacer]
   [:nav.nav
    (nav-link root "areas.html"    "Areas"     (= active-nav :areas))
    (nav-link root "objects.html"  "All Notes" (= active-nav :objects))
    (nav-link root "glossary.html" "Index"     (= active-nav :glossary))]])

;; ---------------------------------------------------------------------------
;; Layout shell
;; ---------------------------------------------------------------------------

(defn page-shell
  "Wrap `body` forms in a full HTML5 page with header, KaTeX, and stylesheet."
  [title root active-nav & body]
  (html5
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str title " — MathAtlas")]
     [:link {:rel "stylesheet"
             :href "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css"}]
     [:link {:rel "stylesheet" :href (str root "style.css")}]]
    [:body
     (header root active-nav)
     [:main.container body]
     [:script {:src "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"}]
     [:script {:src "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"}]
     (katex-script)
     (read-progress-script)
     (header-search-script root)]))

;; ---------------------------------------------------------------------------
;; Shared row components
;; ---------------------------------------------------------------------------

(defn- read-dot [obj]
  [:span.read-dot {:data-object-id (:id obj)}])

(defn- note-row
  "Flat list row used on home, all-notes, area detail, and glossary pages.
   `opts` may include :show-area? and :show-preview?"
  [obj root {:keys [show-area? show-preview?] :or {show-area? true show-preview? false}}]
  (let [title (or (not-empty (:title obj)) (type-label (:type obj)))]
    [:a.note-row {:href           (str root "objects/" (:id obj) ".html")
                  :data-type      (name (:type obj))
                  :data-search    (search-blob obj)}
     [:div.note-row-top
      (read-dot obj)
      [:span.note-row-title title]
      [:span.type-label {:style (str "color:" (obj-color obj))} (type-label (:type obj))]
      (when show-area? [:span.note-row-area (:area obj)])]
     (when show-preview?
       [:div.note-row-preview (preview-text obj)])]))

;; ---------------------------------------------------------------------------
;; Margin sketches (computed at build time, rendered as inline SVG)
;; ---------------------------------------------------------------------------

(defn- area-sketch
  "Small dots-and-lines diagram: one dot per object on an ellipse, connected
   by lines for every :depends-on edge within the area."
  [objs]
  (let [width 170 height 190 cx 85 cy 95 rx 60 ry 75
        n     (max (count objs) 1)
        pts   (mapv (fn [i]
                       (let [angle (- (* (/ (double i) n) 2 Math/PI) (/ Math/PI 2))]
                         {:x (+ cx (* rx (Math/cos angle)))
                          :y (+ cy (* ry (Math/sin angle)))}))
                     (range (count objs)))
        idx-by-id (into {} (map-indexed (fn [i o] [(:id o) i]) objs))
        lines (for [[i obj] (map-indexed vector objs)
                    dep-id  (:depends-on obj)
                    :let    [j (idx-by-id dep-id)]
                    :when   j]
                {:x1 (:x (pts i)) :y1 (:y (pts i))
                 :x2 (:x (pts j)) :y2 (:y (pts j))})
        nodes (map-indexed (fn [i o] {:cx (:x (pts i)) :cy (:y (pts i)) :fill (obj-color o)})
                            objs)]
    {:width width :height height :lines lines :nodes nodes}))

(defn- area-sketch-panel [objs]
  (when (seq objs)
    (let [{:keys [width height lines nodes]} (area-sketch objs)]
      [:div.sketch-panel
       [:div.sketch-label "sketch of this area"]
       [:svg {:width width :height height :viewBox (str "0 0 " width " " height)}
        (map (fn [{:keys [x1 y1 x2 y2]}]
               [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2 :stroke "#c9a876" :stroke-width "1.2"}])
             lines)
        (map (fn [{:keys [cx cy fill]}]
               [:circle {:cx cx :cy cy :r 4 :fill fill :fill-opacity "0.75"}])
             nodes)]])))

(defn- object-sketch
  "Left \u2192 center \u2192 right diagram: dependencies on the left, this
   object centered and enlarged, dependents on the right."
  [obj deps dependents root]
  (let [width  210 cx 105 left-x 18 right-x 192
        left-n  (count deps)
        right-n (count dependents)
        rows    (max left-n right-n 1)
        height  (max 110 (+ 30 (* rows 42)))
        mid-y   (/ height 2)
        side-y  (fn [n i] (if (= n 1) mid-y (+ (- mid-y (/ (* (dec n) 42) 2)) (* i 42))))
        side    (fn [items x align]
                   (map-indexed
                     (fn [i item]
                       (let [y (side-y (count items) i)]
                         {:node  {:cx x :cy y :fill (obj-color item)
                                  :href (str root "objects/" (:id item) ".html")}
                          :line  {:x1 x :y1 y :x2 cx :y2 mid-y}
                          :label {:text  (truncate (or (not-empty (:title item)) (type-label (:type item))) 15)
                                  :href  (str root "objects/" (:id item) ".html")
                                  :align align
                                  :top   (- y 15)
                                  :side  (if (= align "left") (+ x 10) (+ (- width x) 10))}}))
                     items))
        left-parts  (side deps left-x "left")
        right-parts (side dependents right-x "right")]
    {:width  width
     :height height
     :center {:cx cx :cy mid-y :fill "#b5533c"}
     :lines  (map :line (concat left-parts right-parts))
     :nodes  (map :node (concat left-parts right-parts))
     :labels (map :label (concat left-parts right-parts))}))

(defn- object-sketch-panel [obj deps dependents cross-areas root]
  (when (or (seq deps) (seq dependents))
    (let [{:keys [width height center lines nodes labels]} (object-sketch obj deps dependents root)]
      [:div.nearby-panel
       [:div.sketch-label "nearby ideas"]
       [:div.sketch-wrap
        [:svg {:width width :height height :viewBox (str "0 0 " width " " height)}
         (map (fn [{:keys [x1 y1 x2 y2]}]
                [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2 :stroke "#c9a876" :stroke-width "1.3"}])
              lines)
         (map (fn [{:keys [cx cy fill href]}]
                [:a {:href href} [:circle {:cx cx :cy cy :r 5 :fill fill}]])
              nodes)
         [:circle {:cx (:cx center) :cy (:cy center) :r 8 :fill (:fill center)}]]
        (map (fn [{:keys [text href align top side]}]
               [:a.sketch-node-label
                {:href href
                 :style (str "top:" top "px;" (if (= align "left") "left:" "right:") side "px;text-align:" align ";")}
                text])
             labels)]
       (when (seq cross-areas)
         [:div.cross-areas
          "Also connects to"
          (map (fn [a] (list " · " [:a {:href (str root "areas/" (area-slug a) ".html")} a]))
               cross-areas)])])))

;; ---------------------------------------------------------------------------
;; Pages
;; ---------------------------------------------------------------------------

(defn index-page [objects root]
  (let [by-area   (group-by :area objects)
        all-areas (distinct (concat (keys area-meta) (keys by-area)))
        home-areas (take 5 all-areas)
        lately     (take 10 objects)]
    (page-shell "Home" root :home
      [:div.hero
       [:div.hero-title "A map of the math I've learned."]
       [:div.hero-sub "Definitions, theorems and proofs, written down as I go — organized by area, and open for anyone else studying the same things to wander through."]]
      [:div.section-label "Areas"]
      (map (fn [area]
             (let [n (count (get by-area area []))]
               [:a.area-row {:href (str root "areas/" (area-slug area) ".html")}
                [:span.area-row-name area]
                [:span.area-row-count (str n (if (= n 1) " note" " notes"))]]))
           home-areas)
      [:p.browse-link [:a {:href (str root "areas.html")} "Browse all areas →"]]
      [:div.section-label {:style "margin-top:36px"} "Lately"]
      (map #(note-row % root {:show-area? false :show-preview? true}) lately))))

(defn areas-index-page [objects root]
  (let [by-area   (group-by :area objects)
        all-areas (distinct (concat (keys area-meta) (keys by-area)))]
    (page-shell "Areas" root :areas
      [:div.page-title "Areas"]
      (map (fn [area]
             (let [n    (count (get by-area area []))
                   desc (get-in area-meta [area :desc] "")]
               [:a.area-row.area-row-tall {:href (str root "areas/" (area-slug area) ".html")}
                [:div.area-row-line
                 [:span.area-row-name.area-row-name-lg area]
                 [:span.area-row-count (str n (if (= n 1) " note" " notes"))]]
                (when (not-empty desc) [:div.area-row-desc desc])]))
           all-areas))))

(defn objects-page [objects root]
  (let [types (distinct (map :type objects))]
    (page-shell "All Notes" root :objects
      [:div.page-title "All Notes"]
      [:div#notes-count.count-label (str (count objects) (if (= 1 (count objects)) " note" " notes"))]
      [:div.filters
       [:button.filter-chip.active {:data-type "all"} "All"]
       (map (fn [t]
              [:button.filter-chip
               {:data-type (name t) :style (str "--chip-color:" (get type-colors t "#8a7f6d"))}
               (type-label t)])
            types)]
      (map #(note-row % root {:show-area? true :show-preview? true}) objects)
      [:div#empty-state.empty-state {:style "display:none"}
       "No notes match \"" [:span#empty-query] "\"."]
      [:script
       "(function(){
  var typeFilter = 'all';
  function applyFilter(){
    var q = (document.getElementById('global-search').value || '').trim().toLowerCase();
    var count = 0;
    document.querySelectorAll('.note-row').forEach(function(row){
      var matchesType = typeFilter === 'all' || row.dataset.type === typeFilter;
      var matchesQuery = !q || row.dataset.search.indexOf(q) !== -1;
      var show = matchesType && matchesQuery;
      row.style.display = show ? '' : 'none';
      if (show) count++;
    });
    document.getElementById('notes-count').textContent =
      count + (count === 1 ? ' note' : ' notes') + (q ? ' matching \"' + q + '\"' : '');
    document.getElementById('empty-state').style.display = count === 0 ? '' : 'none';
    document.getElementById('empty-query').textContent = q;
  }
  window.MATHATLAS_APPLY_FILTER = applyFilter;
  document.querySelectorAll('.filter-chip').forEach(function(btn){
    btn.addEventListener('click', function(){
      typeFilter = btn.dataset.type;
      document.querySelectorAll('.filter-chip').forEach(function(b){
        var active = b === btn;
        b.classList.toggle('active', active);
        b.style.background  = active ? (b.dataset.type === 'all' ? '#2b2620' : b.style.getPropertyValue('--chip-color')) : '';
        b.style.borderColor = active ? (b.dataset.type === 'all' ? '#2b2620' : b.style.getPropertyValue('--chip-color')) : '';
        b.style.color       = active ? '#fff' : '';
      });
      applyFilter();
    });
  });
  var params = new URLSearchParams(window.location.search);
  var q = params.get('q');
  if (q) document.getElementById('global-search').value = q;
  applyFilter();
})();"])))

(defn area-detail-page [area objects root]
  (let [desc (get-in area-meta [area :desc] "")]
    (page-shell area root :areas
      [:a.back-link {:href (str root "areas.html")} "← Areas"]
      [:div.area-detail-layout
       [:div.area-main
        [:div.page-title {:style "margin-bottom:6px"} area]
        (when (not-empty desc) [:p.area-detail-desc desc])
        (if (empty? objects)
          [:p.empty-state "No notes in this area yet."]
          (map #(note-row % root {:show-area? false :show-preview? false}) objects))]
       (area-sketch-panel objects)])))

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

(defn- back-link-script
  "A generated object page has one fixed URL but many possible entry points
   (home, an area, all-notes, the index). Since there's no server to carry
   that context, infer it client-side from document.referrer and relabel
   the otherwise-default 'Area' back link accordingly."
  [root]
  [:script
   (str "(function(){
  var back = document.querySelector('.back-link');
  if (!back) return;
  var ref = document.referrer || '';
  function set(href, label){ back.href = href; back.textContent = '\\u2190 ' + label; }
  if (/index\\.html(?:[?#]|$)/.test(ref)) set('" root "index.html', 'Home');
  else if (/glossary\\.html/.test(ref)) set('" root "glossary.html', 'Index');
  else if (/objects\\.html/.test(ref)) set('" root "objects.html', 'All Notes');
})();")])

(defn object-page
  "Render an object's detail page. The back link defaults to the object's
   own area and is relabeled client-side based on document.referrer (see
   back-link-script) since a static page has no server-side notion of
   'where the reader came from'."
  [obj gr objects-by-id root]
  (let [color       (obj-color obj)
        title       (not-empty (:title obj))
        deps        (keep objects-by-id (graph/find-dependencies gr (:id obj)))
        dependents  (keep objects-by-id (graph/find-dependents   gr (:id obj)))
        next-obj    (first dependents)
        cross-areas (->> (concat deps dependents)
                         (map :area)
                         (remove #(= % (:area obj)))
                         distinct)
        back-href   (str root "areas/" (area-slug (:area obj)) ".html")
        ph          "___MATHATLAS_BODY___"
        ph2         "___MATHATLAS_PROOF___"
        ph3         "___MATHATLAS_NOTE___"
        shell       (page-shell (or title (type-label (:type obj))) root nil
                     [:div.obj-topline
                      [:a.back-link {:href back-href} "← Area"]
                      [:button.mark-read {:data-object-id (:id obj)} "Mark as read"]]
                     [:div.obj-detail-layout
                      [:div.obj-main
                       [:div.obj-meta (:area obj) " · "
                        [:span {:style (str "color:" color)} (type-label (:type obj))]]
                       (when title [:h1.obj-title title])
                       (when (:note obj)
                         [:div.note-card
                          [:div.note-card-label "In plain terms"]
                          [:div.note-card-body ph3]])
                       [:div.obj-body ph]
                       (when (:proof-latex obj)
                         [:details.proof-details
                          [:summary.proof-summary "Show proof"]
                          [:div.proof-body ph2]])
                       (when next-obj
                         [:div.continue-section
                          [:div.continue-label "Continue to"]
                          [:a.continue-link {:href (str root "objects/" (:id next-obj) ".html")}
                           (or (not-empty (:title next-obj)) (type-label (:type next-obj))) " →"]])]
                      (object-sketch-panel obj deps dependents cross-areas root)]
                     (back-link-script root))]
    (-> shell
        (str/replace ph  (render-body (resolve-refs (:latex obj) objects-by-id)))
        (str/replace ph2 (render-body (resolve-refs (or (:proof-latex obj) "") objects-by-id)))
        (str/replace ph3 (render-body (resolve-refs (or (:note obj) "") objects-by-id))))))

(defn glossary-page [objects root]
  (let [sorted (sort-by (fn [o] (str/lower-case (or (not-empty (:title o)) (type-label (:type o))))) objects)
        groups (group-by (fn [o] (str/upper-case (subs (or (not-empty (:title o)) (type-label (:type o))) 0 1)))
                          sorted)]
    (page-shell "Index" root :glossary
      [:div.page-title "Index"]
      (map (fn [letter]
             [:div.glossary-group
              [:div.glossary-letter letter]
              (map #(note-row % root {:show-area? true :show-preview? false}) (get groups letter))])
           (sort (keys groups))))))

;; ---------------------------------------------------------------------------
;; CSS ("Marginalia")
;; ---------------------------------------------------------------------------

(def stylesheet
  "/* ===== MathAtlas — Marginalia ===== */
@import url('https://fonts.googleapis.com/css2?family=Lora:ital,wght@0,500;0,600;1,500;1,600&family=Inter:wght@400;500;600;700&display=swap');

:root {
  --paper: #f6f1e8;
  --card: #fbf7ee;
  --inset: #efe6d3;
  --ink: #2b2620;
  --secondary: #7a6e5a;
  --muted: #a89a80;
  --muted-2: #8a7f6d;
  --muted-3: #9c8a68;
  --hairline: #e6ddc9;
  --border: #ddd0b3;
  --accent: #b5533c;
  --accent-2: #8a7256;
  --sketch-line: #c9a876;
}

* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: 'Lora', serif;
  background: var(--paper);
  color: var(--ink);
  line-height: 1.6;
  font-size: 16px;
}

a { color: inherit; }

/* --- Header --- */
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--paper);
  border-bottom: 1px solid var(--hairline);
  padding: 0 2rem;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 22px;
}
.brand {
  font-family: 'Lora', serif;
  font-style: italic;
  font-weight: 600;
  font-size: 1.2rem;
  color: var(--ink);
  text-decoration: none;
  white-space: nowrap;
}
.search-box {
  flex: 1;
  max-width: 360px;
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 8px 16px;
  background: var(--card);
}
.search-box input {
  border: none;
  outline: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: var(--ink);
  width: 100%;
}
.spacer { flex: 1; }
.nav { display: flex; gap: 6px; font-family: 'Inter', sans-serif; }
.nav-link {
  font-size: 13px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 6px;
  text-decoration: none;
  color: var(--muted);
}
.nav-link:hover { color: var(--ink); }
.nav-link.active { color: var(--ink); background: var(--inset); }

/* --- Layout --- */
.container { max-width: 720px; margin: 0 auto; padding: 3rem 1.5rem 5rem; }

.page-title {
  font-family: 'Lora', serif;
  font-style: italic;
  font-weight: 600;
  font-size: 26px;
  color: var(--ink);
  margin-bottom: 26px;
}

.hero { margin-bottom: 40px; }
.hero-title {
  font-family: 'Lora', serif;
  font-style: italic;
  font-weight: 600;
  font-size: 34px;
  color: var(--ink);
  margin-bottom: 10px;
}
.hero-sub {
  font-family: 'Inter', sans-serif;
  font-size: 14.5px;
  color: var(--secondary);
  max-width: 520px;
}

.section-label {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  letter-spacing: .1em;
  text-transform: uppercase;
  color: var(--muted);
  font-weight: 600;
  margin-bottom: 14px;
}

/* --- Area rows --- */
.area-row {
  display: block;
  padding: 12px 0;
  border-bottom: 1px solid var(--hairline);
  text-decoration: none;
  cursor: pointer;
}
.area-row-tall { padding: 16px 0; }
.area-row-line, .area-row:not(.area-row-tall) {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.area-row-name { font-size: 17px; color: var(--ink); }
.area-row-name-lg { font-size: 19px; }
.area-row-count {
  font-size: 12px;
  color: var(--accent);
  font-family: 'Inter', sans-serif;
}
.area-row-desc {
  font-size: 13px;
  color: var(--muted-2);
  font-family: 'Inter', sans-serif;
  margin-top: 4px;
  max-width: 520px;
}
.browse-link { margin-top: 14px; }
.browse-link a {
  font-size: 13px;
  color: var(--accent);
  font-family: 'Inter', sans-serif;
  text-decoration: none;
}
.browse-link a:hover { text-decoration: underline; }

/* --- Note rows (flat list, no cards) --- */
.note-row {
  display: block;
  padding: 13px 0;
  border-bottom: 1px solid var(--hairline);
  text-decoration: none;
  cursor: pointer;
}
.note-row-top { display: flex; align-items: baseline; gap: 8px; margin-bottom: 3px; }
.note-row-title { font-size: 16px; color: var(--ink); }
.type-label {
  font-size: 10.5px;
  font-family: 'Inter', sans-serif;
  text-transform: uppercase;
  letter-spacing: .05em;
}
.note-row-area {
  font-size: 11px;
  color: #c2b191;
  font-family: 'Inter', sans-serif;
}
.note-row-preview {
  font-size: 13px;
  color: var(--muted-2);
  font-family: 'Inter', sans-serif;
}

/* --- Read progress dot --- */
.read-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: transparent;
}
.read-dot.is-read { background: var(--accent); border-color: var(--accent); }

/* --- Filters --- */
.filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 24px; font-family: 'Inter', sans-serif; }
.filter-chip {
  font-size: 12px;
  font-weight: 500;
  padding: 5px 12px;
  border-radius: 16px;
  cursor: pointer;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--secondary);
}
.filter-chip.active { background: var(--ink); border-color: var(--ink); color: #fff; }
.count-label {
  font-size: 13px;
  color: var(--muted-2);
  font-family: 'Inter', sans-serif;
  margin-bottom: 20px;
}
.empty-state {
  padding: 40px 0;
  color: var(--muted);
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-style: italic;
}

/* --- Area detail --- */
.back-link {
  font-size: 12px;
  color: var(--muted);
  font-family: 'Inter', sans-serif;
  text-decoration: none;
}
.back-link:hover { color: var(--ink); }
.area-detail-layout { display: flex; gap: 28px; margin-top: 16px; }
.area-main { flex: 1; min-width: 0; }
.area-detail-desc {
  font-size: 13px;
  color: var(--muted-2);
  font-family: 'Inter', sans-serif;
  margin-bottom: 22px;
  max-width: 440px;
}

/* --- Sketch panels --- */
.sketch-panel {
  width: 180px;
  flex: none;
  background: var(--inset);
  border-radius: 10px;
  padding: 16px;
  align-self: flex-start;
}
.sketch-label {
  font-size: 9px;
  letter-spacing: .08em;
  text-transform: uppercase;
  color: var(--muted-3);
  font-family: 'Inter', sans-serif;
  margin-bottom: 10px;
}
.nearby-panel { width: 190px; flex: none; }
.sketch-wrap { position: relative; }
.sketch-node-label {
  position: absolute;
  font-size: 9px;
  color: var(--muted-3);
  font-family: 'Inter', sans-serif;
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
  line-height: 1.2;
}
.cross-areas {
  margin-top: 14px;
  font-size: 11.5px;
  color: var(--muted-3);
  font-family: 'Inter', sans-serif;
  line-height: 1.6;
}
.cross-areas a { color: var(--accent); text-decoration: none; }

/* --- Object detail --- */
.obj-topline { display: flex; justify-content: space-between; align-items: baseline; }
.obj-detail-layout { display: flex; gap: 28px; margin-top: 16px; }
.obj-main { flex: 1; min-width: 0; }
.mark-read {
  font-size: 12px;
  font-family: 'Inter', sans-serif;
  color: var(--accent-2);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}
.obj-meta {
  font-size: 11px;
  color: var(--muted);
  font-family: 'Inter', sans-serif;
  margin: 16px 0 8px;
}
.obj-title {
  font-family: 'Lora', serif;
  font-style: italic;
  font-weight: 600;
  font-size: 28px;
  color: var(--ink);
  margin-bottom: 18px;
}

.note-card {
  background: var(--inset);
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 20px;
}
.note-card-label {
  font-size: 10px;
  letter-spacing: .08em;
  text-transform: uppercase;
  color: var(--muted-3);
  font-family: 'Inter', sans-serif;
  font-weight: 600;
  margin-bottom: 6px;
}
.note-card-body {
  font-size: 14.5px;
  line-height: 1.7;
  color: #4a4130;
  font-family: 'Inter', sans-serif;
  font-style: italic;
}

.obj-body { font-size: 16px; line-height: 1.85; color: #3a3327; margin-bottom: 20px; }
.latex-list { padding-left: 1.6rem; margin: 0.5rem 0; }
.latex-list li { margin-bottom: 0.4rem; }

.proof-details { margin-bottom: 20px; }
.proof-summary {
  font-size: 12.5px;
  color: var(--accent-2);
  font-family: 'Inter', sans-serif;
  cursor: pointer;
  list-style: none;
}
.proof-summary::-webkit-details-marker { display: none; }
.proof-summary::before { content: '▸ '; }
.proof-details[open] .proof-summary::before { content: '▾ '; }
.proof-body {
  background: var(--inset);
  border-left: 3px solid var(--accent-2);
  padding: 14px 18px;
  margin-top: 10px;
  font-size: 14.5px;
  line-height: 1.8;
  color: #4a4130;
}

.continue-section {
  margin-top: 32px;
  padding-top: 16px;
  border-top: 1px solid var(--hairline);
}
.continue-label {
  font-size: 10px;
  letter-spacing: .08em;
  text-transform: uppercase;
  color: var(--muted);
  font-family: 'Inter', sans-serif;
  margin-bottom: 6px;
}
.continue-link {
  font-family: 'Lora', serif;
  font-style: italic;
  font-size: 16px;
  color: var(--accent);
  text-decoration: none;
}

/* --- Glossary --- */
.glossary-group { margin-bottom: 18px; }
.glossary-letter {
  font-size: 11px;
  letter-spacing: .1em;
  color: var(--accent);
  font-family: 'Inter', sans-serif;
  font-weight: 700;
  margin-bottom: 4px;
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
  (let [by-area       (group-by :area objects)
        all-areas     (distinct (concat (keys area-meta) (keys by-area)))
        objects-by-id (into {} (map (juxt :id identity) objects))]
    (write-page (str output-dir "/style.css")     stylesheet)
    (write-page (str output-dir "/index.html")    (index-page       objects ""))
    (write-page (str output-dir "/objects.html")  (objects-page     objects ""))
    (write-page (str output-dir "/areas.html")    (areas-index-page objects ""))
    (write-page (str output-dir "/glossary.html") (glossary-page    objects ""))
    (doseq [area all-areas]
      (write-page (str output-dir "/areas/" (area-slug area) ".html")
                  (area-detail-page area (get by-area area []) "../")))
    (doseq [obj objects]
      (write-page (str output-dir "/objects/" (:id obj) ".html")
                  (object-page obj gr objects-by-id "../")))
    (println (str "  Wrote " (+ 4 (count all-areas) (count objects)) " files."))))
