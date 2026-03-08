# MathAtlas

A personal math knowledge base that turns LaTeX notes into a browsable static website. Write definitions, theorems, and proofs in `.tex` files; MathAtlas parses them into structured data, resolves cross-references, and generates interlinked HTML pages rendered with KaTeX.

## How it works

1. **Parse** — Scans `.tex` files for recognized environments (`definition`, `theorem`, `lemma`, `corollary`, `proposition`, `example`, `remark`, `proof`, `problem`). Each block becomes a structured object with a stable ID.
2. **Resolve** — `\ref` / `\eqref` / `\cref` labels are resolved across all files into explicit `depends-on` links between objects.
3. **Persist** — Objects are written to `data/objects.edn`, which you can hand-edit to add tags, concepts, or tweak dependencies.
4. **Generate** — A dependency graph is built and the full static site is emitted to `docs/`.

## Notes format

Each `.tex` file should declare its mathematical area at the top:

```tex
% area: Representation Theory

\begin{definition}[Group Representation]
\label{def:representation}
A \textit{representation} of a group $G$ on a vector space $V$ ...
\end{definition}

\begin{theorem}[Schur's Lemma]
\label{thm:schur}
Let $(\rho, V)$ and $(\sigma, W)$ be irreducible representations
(\ref{def:irreducible}) of $G$ ...
\end{theorem}

\begin{proof}
...
\end{proof}
```

Proofs are automatically attached to the nearest preceding theorem/lemma/corollary/proposition and do not appear as separate pages.

## Usage

**Full parse + site generation** (reads `.tex` files, writes EDN, generates site):

```sh
clj -M:run
# or with custom directories:
clj -M:run notes docs
```

**Build-only** (regenerate site from existing `data/objects.edn`, preserving any hand edits):

```sh
clj -M:build
# or with custom output directory:
clj -M:build docs
```

Output is written to `docs/` by default. Open `docs/index.html` in a browser to view the site.

## Generated pages

| Page | Description |
|---|---|
| `index.html` | Overview and stats |
| `objects.html` | All objects, filterable by type and area |
| `courses.html` | Objects grouped by area |
| `objects/<id>.html` | Individual object with rendered LaTeX, proof, and dependency links |

## Dependencies

- [Clojure](https://clojure.org/) 1.11.1
- [Hiccup](https://github.com/weavejester/hiccup) 1.0.5 — HTML generation
- [babashka/fs](https://github.com/babashka/fs) 0.5.20 — filesystem utilities
- [KaTeX](https://katex.org/) (CDN) — math rendering in the browser

## Project structure

```
notes/          LaTeX source files
data/
  objects.edn   Parsed + hand-editable structured data
src/mathatlas/
  model.clj     Object schema and ID generation
  parser.clj    LaTeX → object maps
  graph.clj     Dependency graph construction
  edn_io.clj    EDN read/write
  site.clj      Static site generation (Hiccup)
  core.clj      Entry point
docs/           Generated static site (deployed to GitHub Pages)
```
