# Handoff: MathAtlas Redesign ("Marginalia")

## Overview
A visual and flow redesign of MathAtlas, your Clojure static-site generator that turns LaTeX math notes into a browsable HTML knowledge base. The redesign keeps the existing data pipeline (`.tex` → `objects.edn` → generated `docs/`) untouched and replaces the generated site's look, page set, and navigation model. It also adds a lightweight note-authoring convention so growing the notes collection doesn't mean editing giant `.tex` files.

Direction: warm, personal-notebook aesthetic ("Marginalia") — serif italic headings, paper tones, a terracotta accent — with the old force-directed dependency graph replaced by small, static "sketch" diagrams embedded in the margin of area and object pages.

## About the Design Files
The files in `prototypes/` are **HTML design references** built as interactive single-file prototypes (React-in-a-box, not the target stack) — they show exact look, copy, and behavior, not code to copy verbatim. The task is to **recreate this design inside the existing Clojure/Hiccup codebase** (`src/mathatlas/site.clj` and friends), following its existing conventions: Hiccup for markup, one big CSS string (`stylesheet`) for styles, small vanilla-JS snippets for interactivity, generated at build time by `generate-site`. Do not introduce React/a JS framework — the current stack has no client-side framework and doesn't need one for this design.

- `MathAtlas Redesign.dc.html` — **the target design.** Build every screen and behavior described in this README to match it.
- `MathAtlas Current (before).dc.html` — a faithful recreation of the *current* generated site, for side-by-side comparison of what's changing.
- `MathAtlas Redesign Options (exploration).dc.html` — earlier direction exploration, kept for context only; not something to implement.

## Fidelity
**High-fidelity.** Colors, type, spacing, and copy in `MathAtlas Redesign.dc.html` are final — match them exactly. Where the prototype's implementation approach differs from what a static Clojure generator should do (see "Implementation notes" under each screen, especially the margin sketches, search, and read-progress), follow the *visual result* but implement it the static-site way described below rather than porting the prototype's React logic literally.

---

## Design Tokens

**Fonts** (Google Fonts, same CDN pattern as today's `@import` in `stylesheet`):
- Headings / serif: `'Lora', serif` — weights 500/600, italic for all headings
- UI / body sans: `'Inter', system-ui, sans-serif` — weights 400/500/600/700
- Statement/body copy uses Lora at 16px/1.85 line-height; all UI chrome (labels, meta, buttons, nav) uses Inter

**Colors:**
| Token | Hex | Use |
|---|---|---|
| Paper (page bg) | `#f6f1e8` | body background, header background |
| Card/inset | `#fbf7ee` | search box fill |
| Inset warm | `#efe6d3` | sketch panel bg, proof callout, note callout |
| Ink (primary text) | `#2b2620` | headings, primary text |
| Secondary text | `#7a6e5a` | subtitles, descriptions |
| Muted text | `#a89a80` / `#8a7f6d` / `#9c8a68` | meta text, labels, placeholders (used somewhat interchangeably — pick one per context, see file) |
| Hairline | `#e6ddc9` | row dividers, header border |
| Border | `#ddd0b3` | input/button borders |
| Accent (primary) | `#b5533c` | links, counts, active states, "definition" type color |
| Accent (secondary) | `#8a7256` | proof toggle, secondary emphasis |
| Sketch line | `#c9a876` | margin-diagram connecting lines |

**Type-badge colors** (object type → color, same idea as today's `type-colors` map in `site.clj` but re-picked to be muted/warm instead of saturated rainbow):
```
theorem     #6b7d8f
lemma       #8a6d9e
definition  #b5533c
problem     #a34a3a
example     #c9a04a
remark      #9c9080
proof       #5e8f7c
corollary   #b06a8f
proposition #5f7a9e
```

**Radii / spacing:** pill buttons/chips 16–20px radius; sketch panels and callout boxes 8–10px radius; row padding 12–16px vertical with 1px `#e6ddc9` bottom border (no card boxes/shadows in list rows — this design is flatter and less "dashboard" than the current one); main content column max-width 720px.

---

## Screens / Views

Each becomes its own generated page (or set of pages), same as today's `index.html` / `objects.html` / `areas.html` / `areas/<slug>.html` / `objects/<id>.html` pattern. Add two new page types (glossary, and the margin sketches are inline, not new pages).

### 1. Home (`index.html`)
- Header (see "Header" below) present on every page.
- Hero: italic serif headline "A map of the math I've learned." + one-line subtitle in Inter.
- "Areas" section: top 5 areas as plain rows (name left, note count right in accent color), divided by hairlines, no cards. "Browse all areas →" link below.
- "Lately" section: last 10 objects (today shows recent by `:created-at`), same flat-row style, each row shows a read-progress dot, title, type label (colored by type), and a KaTeX-rendered one-line preview of the LaTeX body.

### 2. Areas index (`areas.html`)
- Serif italic "Areas" title.
- Every area (from `area-meta` plus any area present in data, same fallback logic as today) as a row: name + count on one line, description below if present (matches today's `area-meta` — note two areas currently have no entry there and get no description: keep that fallback behavior).

### 3. Area detail (`areas/<slug>.html`)
- Back link to Areas.
- Two-column layout: left = area name, description, flat list of its objects (title, type, read dot); right = a fixed-size (170×190) **margin sketch**: small SVG dots (one per object, colored by type, `fill-opacity:0.75`, no stroke) connected by thin lines (`#c9a876`, 1.2px) for each `:depends-on` edge *within that area*. This fully replaces today's Cytoscape.js interactive graph.
  - **Implementation note:** compute node positions in Clojure at build time — evenly spaced on an ellipse (`rx≈60, ry≈75` around center `85,95`) by object index, same as the prototype's `buildAreaSketch`. This is deterministic and needs no client-side graph library at all (delete the Cytoscape/dagre `<script>` includes and area-graph JS from `site.clj` once this ships).

### 4. All Notes (`objects.html`)
- Serif italic "All Notes" title + live count (updates with filters/search).
- Type filter chips — same interaction as today's `.filter-btn`/`filterCards`, just restyled (outline pill, filled when active, colored by type).
- A **search box in the header** (see below) filters this page's rows by substring match against title + area + type + body text, case-insensitive.
- Flat rows (no cards): read dot, title (or type label if untitled), type label, area name, one-line KaTeX-rendered preview.
- Empty state: italic muted text `No notes match "<query>".`

### 5. Object detail (`objects/<id>.html`)
Replaces today's object page. Order top to bottom:
1. Back link (labelled contextually: "Home" / "Area" / "All Notes" / "Index" depending on where the reader came from) + a **"Mark as read"** toggle, right-aligned on the same line.
2. Meta line: area name · type label (type label colored).
3. Title (serif italic, 28px) — omit if the object has no title (matches today's behavior for untitled problems/propositions).
4. **"In plain terms" note card** (new) — only if the object has a `:note` — warm inset box, small-caps label, italic Inter body. This is the "more intuitive explanation" callout.
5. Statement body — same LaTeX→HTML conversion as today (`render-body`), KaTeX-rendered, serif 16px/1.85.
6. Proof, if present — collapsed by default behind a "▸ Show proof" toggle. **Implementation note:** use a native `<details>/<summary>` element styled to match (no JS needed) rather than a JS-driven toggle — simpler and works without JS, unlike the prototype.
7. "Continue to" — if the object has any dependents, a single suggested next note (the first dependent), styled as a serif italic link. Pedagogical "keep reading" nudge.
8. Right-hand margin panel (only if the object has any deps/dependents): the **nearby-ideas sketch** — same visual language as the area sketch but laid out left→center→right (dependencies on the left, this object in the center as the larger accent-colored node, dependents on the right), with a small text label next to each neighbor node, clickable to navigate to that object. Below the sketch, if any neighbor belongs to a *different* area, a line: "Also connects to · AreaA · AreaB" (new — the explicit cross-area callout).
  - **Implementation note:** same as the area sketch — compute positions at build time in Clojure; render dots+lines as inline SVG and labels as small `<div>`s absolutely positioned over the SVG (do **not** put labels inside SVG `<text>` as literal Hiccup-interpolated strings without checking escaping — keep it simple as plain positioned HTML, which is also easier to make clickable links).

### 6. Index / Glossary (`glossary.html`) — new page
- Serif italic "Index" title.
- Every object, alphabetized by title (fallback to type label for untitled ones), grouped under bold accent-colored letter headers (A, B, C…).
- Each row: read dot, title, type label, area name — same flat-row style as other lists.
- Purpose: lets a reader who remembers a term's name but not its area jump straight to it. Scales fine to hundreds of objects; consider a sticky per-letter header or jump-nav at the top if the collection gets very large (300+).

### Header (all pages)
- 64px tall, sticky top, paper background, `#e6ddc9` bottom border.
- Left: "MathAtlas" wordmark, serif italic, links to Home.
- Center-left: search input (pill, `#fbf7ee` fill, `#ddd0b3` border) — placeholder "Search theorems, definitions…". Typing live-filters and navigates to All Notes with the query applied (matches prototype's `onSearchChange` behavior — implement as a small vanilla-JS listener that redirects to `objects.html?q=...` or filters in place if already on that page, your call based on whether you want a full static site or one JS-enhanced page for search).
- Right: nav links — Areas / All Notes / Index — current page highlighted (`#efe6d3` background pill).

---

## Interactions & Behavior

- **Search & type filters:** client-side only, no server. Reuse today's `filterCards`-style vanilla JS pattern (already in `site.clj`'s `objects-page`), extended to also match a free-text query against title/area/type/body.
- **Proof disclosure:** native `<details>/<summary>`, no JS.
- **Read progress:** `localStorage` only, no accounts/backend. Store a JSON object of `{ "<object-id>": true, ... }` under a single key (e.g. `mathatlas_read_ids`). On each page, a small inline script reads this on load and toggles a filled/outline dot next to any row whose `data-object-id` matches, plus wires the "Mark as read" control on object pages. This mirrors the prototype's `isRead`/`toggleRead` logic — port the *logic*, not the React state.
- **Margin sketches (area + object pages):** fully static, computed and inlined as SVG at Clojure build time — no client JS, no Cytoscape/Mermaid/dagre dependency at all. This is a meaningful simplification vs. today's site (you can drop those three CDN scripts entirely).
- **Cross-area links:** computed at build time from existing `:depends-on` + `:area` data, same place you already compute "Depends on"/"Used in".

## State Management
No client-side app state / no framework. The only piece of "state" that persists across page loads is the read-progress `localStorage` map, handled by a small shared JS snippet included on every generated page (similar to today's shared `katex-script`).

## Data Model Changes (`model.clj` / `objects.edn`)
Add one new optional, hand-editable field to the object schema, alongside the existing `:tags`/`:concepts`:
```clojure
:note nil  ; optional plain-language explanation, shown as the "In plain terms" callout when present
```
Nothing else in the schema changes. `:depends-on`, `:area`, `:label`, etc. all work as today and are what the margin sketches and cross-area callout are built from.

## Assets
- Google Fonts: Lora (ital/upright, 500/600) and Inter (400/500/600/700) — same `@import` pattern as today's Inter-only import in `stylesheet`.
- KaTeX 0.16.9 (same CDN + version already used) for math rendering — no version change needed.
- No new icon assets; the design uses text and simple SVG (dots + lines) only.

## Files
- `prototypes/MathAtlas Redesign.dc.html` — the target design (open in a browser to click through every screen).
- `prototypes/MathAtlas Current (before).dc.html` — recreation of the current live site, for comparison.
- `prototypes/MathAtlas Redesign Options (exploration).dc.html` — earlier direction exploration; reference only.

---

## Appendix: Easier Note Authoring (codebase convention, not a design change)

Goal: stop needing to scroll through one giant `.tex` file per area to add a single note. **No parser changes are required** — two facts about the existing pipeline make this free:

1. `mathatlas.core/load-notes` calls `(fs/glob notes-dir "**.tex")`, which is **recursive** — it finds `.tex` files at any depth under `notes/`.
2. `mathatlas.parser/extract-area` reads the area from a `% area: ...` comment **inside the file**, not from its name or folder — so folder structure is purely organizational.

**Recommended convention:** one file per note (or a tiny cluster of 2–3 tightly related ones), grouped into per-area subfolders:
```
notes/
  algebraic-topology/
    homotopy.tex
    fundamental-group.tex
    covering-spaces.tex
  lie-groups/
    group-basics.tex
    orbit-stabilizer.tex
```
Each file still needs its own `% area: ...` line at the top (it's per-file). Run `clj -M:run` exactly as today — it picks up every file automatically.

**Caution:** object IDs hash `(source-file basename + type + title)`. Keep filenames unique across the *entire* `notes/` tree (don't reuse a generic name like `definitions.tex` in two folders) to avoid accidental ID collisions.

**Optional scaffold script** (`scripts/new-note.sh`) to remove the last bit of friction:
```bash
#!/usr/bin/env bash
# usage: scripts/new-note.sh "Algebraic Topology" covering-spaces
area="$1"; slug="$2"
dir="notes/$(echo "$area" | tr '[:upper:] ' '[:lower:]-')"
mkdir -p "$dir"
cat > "$dir/$slug.tex" <<EOF
% area: $area

\begin{definition}[Title Here]
\label{def:$slug}

\end{definition}
EOF
echo "Created $dir/$slug.tex"
```
