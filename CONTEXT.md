# MathAtlas — Project Context

## What it is

MathAtlas is a Clojure tool that converts LaTeX math notes into a static website. You write `.tex` files using standard LaTeX environments (theorem, definition, etc.), run one command, and get a browsable HTML site with rendered math and cross-linked objects.

The intended workflow: take notes in LaTeX as you learn math → run MathAtlas → browse your knowledge as an interlinked reference site.

---

## Pipeline

```
.tex files  →  parse  →  objects.edn  →  generate  →  docs/
```

1. **Parse** (`mathatlas.parser`): Glob all `.tex` files under `notes/` (recursively — subfolders are purely organizational). Extract recognized environments into object maps. Attach proofs to their nearest theorem/lemma. Resolve `\ref` labels into dependency links across all files.
2. **Persist** (`mathatlas.edn-io`): Serialize objects to `data/objects.edn` (pretty-printed EDN). This file can be hand-edited to add tags, concepts, a `:note`, or tweak dependencies.
3. **Graph** (`mathatlas.graph`): Build a directed dependency graph (edges: dependent → dependency).
4. **Generate** (`mathatlas.site`): Render every page as Hiccup → HTML and write to `docs/`.

There is a **build-only mode** (`--build` flag / `-M:build`) that skips parsing and reads directly from the existing `data/objects.edn`. This preserves any hand-edits to the EDN between site regenerations.

---

## Note-authoring convention

One `.tex` file per note (or a tiny cluster of 2–3 tightly related ones), grouped into per-area subfolders:

```
notes/
  algebraic-topology/
    algebraic-topology.tex
  topology/
    metric-spaces.tex
    open-sets.tex
  ...
```

Each file still needs its own `% area: <name>` comment at the top — the area comes from that comment, not the folder name, so folder structure is purely organizational. Scaffold a new note with:

```sh
scripts/new-note.sh "Algebraic Topology" covering-spaces
```

**Caution:** object IDs hash `(source-file basename + type + title)`. Keep filenames unique across the *entire* `notes/` tree — don't reuse a generic name like `definitions.tex` in two folders.

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
 :note        nil          ; hand-editable plain-language explanation ("In plain terms" callout)
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

## Generated site ("Marginalia" design)

A warm, personal-notebook aesthetic: serif italic (Lora) headings, paper tones, a terracotta accent, flat list rows (no cards/shadows), and small static SVG "sketch" diagrams in place of an interactive graph library.

### Pages

| Path | Content |
|---|---|
| `index.html` | Hero + top 5 areas + last 10 notes ("Lately") |
| `objects.html` | All notes, type-filter chips, live search |
| `areas.html` | Flat list of every area with description + count |
| `areas/<slug>.html` | Area's notes + a static margin sketch (dots/lines SVG) of its internal dependencies |
| `objects/<id>.html` | Full note: meta, title, optional "In plain terms" note card, body, collapsible proof, "Continue to" next note, "nearby ideas" sketch, cross-area callout |
| `glossary.html` | Every note alphabetized under letter headers |
| `style.css` | All CSS (written inline by the generator) |

Root pages use `root=""`, area/object pages use `root="../"`.

### Margin sketches
Computed at build time in Clojure (no client-side graph library): nodes placed on an ellipse (area sketch) or in a left/center/right layout (object sketch), rendered as inline SVG `<circle>`/`<line>` elements. See `area-sketch`, `object-sketch` in `site.clj`.

### Read progress & search
- **Read progress**: `localStorage` key `mathatlas_read_ids`, a small shared script (`read-progress-script` in `site.clj`) paints `.read-dot` elements and wires the "Mark as read" button on object pages.
- **Search**: header search box; on `objects.html` it filters live in place; on other pages, Enter navigates to `objects.html?q=...`.
- **Back link on object pages**: since a static page has one URL but many entry points, the back link defaults to the note's area and is relabeled client-side from `document.referrer` (see `back-link-script`).

---

## Design system

### Type colors
| Type | Color |
|---|---|
| theorem | `#6b7d8f` |
| lemma | `#8a6d9e` |
| definition | `#b5533c` |
| problem | `#a34a3a` |
| example | `#c9a04a` |
| remark | `#9c9080` |
| proof | `#5e8f7c` |
| corollary | `#b06a8f` |
| proposition | `#5f7a9e` |

### Colors
| Token | Hex | Use |
|---|---|---|
| Paper | `#f6f1e8` | page/header background |
| Card/inset | `#fbf7ee` | search box fill |
| Inset warm | `#efe6d3` | sketch panel, proof/note callouts |
| Ink | `#2b2620` | headings, primary text |
| Secondary | `#7a6e5a` | subtitles |
| Muted | `#a89a80` / `#8a7f6d` / `#9c8a68` | meta text |
| Hairline | `#e6ddc9` | row dividers |
| Border | `#ddd0b3` | input/button borders |
| Accent | `#b5533c` | links, counts, active states |
| Accent 2 | `#8a7256` | proof toggle |
| Sketch line | `#c9a876` | margin-diagram connecting lines |

### Typography / layout
- Headings: Lora, italic, 500/600
- UI/body: Inter 400–700
- Statement body: Lora 16px/1.85
- Main content column max-width: 720px

### Area metadata
`area-meta` in `site.clj` holds an optional `:desc` per area name. Areas not listed still work but get no description.

---

## Third-party dependencies (CDN, no install)

| Library | Version | Purpose |
|---|---|---|
| KaTeX | 0.16.9 | Math rendering |
| KaTeX auto-render | 0.16.9 | Scan DOM for math delimiters |

KaTeX delimiters configured: `$$...$$`, `$...$`, `\(...\)`, `\[...\]`, `\begin{align}`, `\begin{align*}`.
Custom macro: `\Hom → \operatorname{Hom}`.

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

# Scaffold a new note
scripts/new-note.sh "<Area Name>" <slug>
```

Default output dir: `docs/`. `docs/` is gitignored — GitHub Actions (`.github/workflows/deploy.yml`) regenerates and deploys it on every push to `main`.

---

## File structure

```
notes/
  <area-slug>/<note>.tex   One file per note, grouped by area (organizational only)
data/
  objects.edn              Parsed structured data (hand-editable)
scripts/
  new-note.sh              Scaffold a new note file
src/mathatlas/
  model.clj                Object schema, make-object, make-id
  parser.clj                LaTeX → object maps, proof attachment, ref resolution
  graph.clj                 Dependency graph (nodes + edges), find-deps/dependents
  edn_io.clj                EDN read/write
  site.clj                  All page generation, CSS, Hiccup templates, margin sketches
  core.clj                  Entry point, run-parse, run-build
docs/                       Generated static site (gitignored, built by CI)
deps.edn                    Project dependencies and aliases
```
