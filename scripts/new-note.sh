#!/usr/bin/env bash
# usage: scripts/new-note.sh "Algebraic Topology" covering-spaces
set -euo pipefail
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
