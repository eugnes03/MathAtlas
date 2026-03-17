# MathAtlas — Project Context

## What it is

MathAtlas is a Clojure tool that converts LaTeX math notes into a static website. You write `.tex` files using standard LaTeX environments (theorem, definition, etc.), run one command, and get a browsable HTML site with rendered math, cross-linked objects, and interactive dependency graphs.

The intended workflow: take notes in LaTeX as you learn math → run MathAtlas → browse your knowledge as an interlinked reference site.

---

## Pipeline

```
.tex files  →  parse  →  objects.edn  →  generate  →  docs/
```

1. **Parse** (`mathatlas.parser`): Glob all `.tex` files in `notes/`. Extract recognized environments into object maps. Attach proofs to their nearest theorem/lemma. Resolve `\ref` labels into dependency links across all files.
2. **Persist** (`mathatlas.edn-io`): Serialize objects to `data/objects.edn` (pretty-printed EDN). This file can be hand-edited to add tags, concepts, or tweak dependencies.
3. **Graph** (`mathatlas.graph`): Build a directed dependency graph (edges: dependent → dependency).
4. **Generate** (`mathatlas.site`): Render every page as Hiccup → HTML and write to `docs/`.

There is a **build-only mode** (`--build` flag / `-M:build`) that skips parsing and reads directly from the existing `data/objects.edn`. This preserves any hand-edits to the EDN between site regenerations.

---

## Data model

Every parsed environment becomes an **object map**:

```clojure
{:id          "a3f1c2b4"   ; 8-char hex, stable hash of (source-file + type + title)
 :type        :definition  ; keyword
 :title       "Group Representation"
 :latex       "..."        ; raw LaTeX body
 :source-file "example.tex"
 :area        "Representation Theory"
 :proof-latex nil          ; attached proof body (theorems/lemmas only)
 :label       "def:representation"  ; from \label{...}
 :refs        ["def:representation" ...]  ; all \ref targets in body
 :depends-on  ["a1b2c3d4" ...]  ; resolved to IDs after full parse
 :concepts    []           ; hand-editable
 :tags        []           ; hand-editable
 :created-at  "2026-03-07"}
```

IDs are deterministic: `(format "%08x" (bit-and (hash (str source-file type title)) 0xFFFFFFFF))`. Stable across re-runs so links don't break.

---

## Recognized environments

```
:theorem  :lemma  :definition  :problem
:example  :remark :proof       :corollary  :proposition
```

Any `\begin{env}...\end{env}` block where `env` is not in this set is ignored. Proofs are special: they are not emitted as top-level objects — they are attached to the nearest preceding theorem/lemma/corollary/proposition as `:proof-latex`, then removed from the list. Orphaned proofs (no preceding provable) are kept as standalone objects.

---

## LaTeX authoring format

```tex
% area: Category Theory          ← declares the area for the whole file

\begin{definition}[Category]
\label{def:category}
A \emph{category} $\mathcal{C}$ consists of ...
\end{definition}

\begin{theorem}[Schur's Lemma]
\label{thm:schur}
... relies on \ref{def:category} ...
\end{theorem}

\begin{proof}
...
\end{proof}
```

- Area is read from `% area: <name>` comment. Falls back to `"Uncategorized"` if missing.
- `\label{key}` sets the object's label for cross-referencing.
- `\ref{}`, `\eqref{}`, `\cref{}`, `\autoref{}` are all parsed as dependency references.
- The parser uses a regex with backreference `\{\1\}` so nested environments of *different* types (e.g. `align` inside `theorem`) are captured correctly as part of the body.

Supported LaTeX text commands (converted to HTML on object pages): `\textbf`, `\textit`, `\emph`, `\texttt`, `\text`, `\begin{enumerate}`, `\begin{itemize}`, `\item`. Math delimiters are left untouched for KaTeX to handle.

---

## Cross-reference resolution

After parsing all files, `resolve-dependencies` builds a `label → id` map from every object that has a `:label`. It then replaces each `:refs` entry (label strings) with the corresponding object ID, stored in `:depends-on`. This works cross-file.

On individual object pages, `\ref{label}` occurrences in the rendered body are replaced with the title of the referenced object (or its type if untitled).

---

## Generated site

### Pages

| Path | Content |
|---|---|
| `index.html` | Stats (object count, type count, area count) + 10 most recent objects |
| `objects.html` | All objects with type-filter buttons |
| `areas.html` | Grid of area cards |
| `areas/<slug>.html` | Objects in one area + interactive Cytoscape graph |
| `objects/<id>.html` | Full object: rendered LaTeX, proof, depends-on list, used-in list, Mermaid graph |
| `style.css` | All CSS (written inline by the generator) |

Root pages use `root=""`, object/area pages use `root="../"`.

### Object detail page features

- Rendered LaTeX body (HTML-escaped + KaTeX auto-render)
- Proof section (if `:proof-latex` is set)
- "Depends on" list: objects this one references
- "Used in" list: objects that reference this one
- Mermaid `flowchart LR` dependency graph (current node highlighted in indigo, clickable nodes)

### Area detail page features

- Area description (from hardcoded `area-meta`)
- Interactive Cytoscape.js DAG (dagre layout, left-to-right, colored nodes by type, hover tooltip, click to navigate)
- Graph legend
- All objects in the area as cards

---

## Design system

### Type colors
| Type | Color |
|---|---|
| theorem | `#3B82F6` blue |
| lemma | `#8B5CF6` violet |
| definition | `#10B981` green |
| problem | `#EF4444` red |
| example | `#F59E0B` amber |
| remark | `#6B7280` gray |
| proof | `#14B8A6` teal |
| corollary | `#EC4899` pink |
| proposition | `#6366F1` indigo |

Primary accent color throughout the UI: `#6366f1` (indigo).

### Hardcoded area metadata
These areas have colors and descriptions built into `site.clj`:
- **Category Theory** — `#7C3AED`
- **Topology** — `#2563EB`
- **Representation Theory** — `#6366F1`
- **Neural Networks** — `#059669`
- **Probability Theory** — `#D97706`

Areas not in this list still work but get a fallback gray color and no description.

### Typography / layout
- Font: Inter (Google Fonts CDN)
- Max content width: 860px, centered
- Object body: monospace font, pre-wrap, light gray background
- Cards: white, subtle shadow, colored left border, lift on hover

---

## Third-party dependencies (CDN, no install)

| Library | Version | Purpose |
|---|---|---|
| KaTeX | 0.16.9 | Math rendering |
| KaTeX auto-render | 0.16.9 | Scan DOM for math delimiters |
| Mermaid | 10 | Dependency graph on object pages |
| Cytoscape.js | 3 | Interactive graph on area pages |
| cytoscape-dagre | 2 | DAG layout for Cytoscape |
| dagre | 0.8.5 | Layout engine for cytoscape-dagre |

KaTeX delimiters configured: `$$...$$`, `$...$`, `\(...\)`, `\[...\]`, `\begin{align}`, `\begin{align*}`.
Custom macro: `\Hom → \operatorname{Hom}`.

---

## Current notes

### `notes/example.tex` — Representation Theory
- `def:representation` — Group Representation
- `def:subrepresentation` — Subrepresentation
- `def:irreducible` — Irreducible Representation
- `thm:schur` — Schur's Lemma (with proof)
- `thm:maschke` — Maschke's Theorem
- `ex:regular` — Regular Representation
- `rem:conj-classes` — conjugacy classes remark
- `cor:dimension` — Dimension Formula
- `prob:s3` — Character Table of S₃

### `notes/category.tex` — Category Theory
- `def:category` — Category
- `rmk:morphism-notation` — Notation for Morphisms
- `def:isomorphism` — Isomorphism
- `prop:inverse-unique` — Uniqueness of Inverses (with proof)
- `cor:iso-equivalence-relation` — isomorphism is equivalence relation (with proof)
- `def:subcategory` — Subcategory
- `def:opposite-category` — Opposite Category
- `rmk:duality` — Duality Principle
- `def:functor` — Functor
- `lem:functor-preserve-iso` — Functors Preserve Isomorphisms (with proof)
- `def:functor-composition` — Composition of Functors
- `lem:functor-composition` — composition is a functor (with proof)
- `def:identity-functor` — Identity Functor
- `lem:identity-functor` — identity functor is a functor (with proof)
- `prop:functor-associative` — functor composition is associative (with proof)
- `ex:forgetful` — Forgetful functor Grp → Set
- `def:natural-transformation` — Natural Transformation

---

## Clojure dependencies

```edn
org.clojure/clojure  1.11.1
hiccup/hiccup        1.0.5
babashka/fs          0.5.20
```

---

## CLI

```sh
# Parse notes/ → write data/objects.edn → generate docs/
clj -M:run

# Custom dirs
clj -M:run <notes-dir> <output-dir>

# Regenerate site from existing EDN (preserves hand-edits)
clj -M:build
clj -M:build <output-dir>
```

Default output dir: `docs/`. The `docs/` directory is committed to git and served via GitHub Pages.

---

## File structure

```
notes/
  example.tex           Representation Theory notes
  category.tex          Category Theory notes
data/
  objects.edn           Parsed structured data (hand-editable)
src/mathatlas/
  model.clj             Object schema, make-object, make-id
  parser.clj            LaTeX → object maps, proof attachment, ref resolution
  graph.clj             Dependency graph (nodes + edges), find-deps/dependents
  edn_io.clj            EDN read/write
  site.clj              All page generation, CSS, Hiccup templates
  core.clj              Entry point, run-parse, run-build
docs/                   Generated static site (committed, GitHub Pages)
deps.edn                Project dependencies and aliases
```
