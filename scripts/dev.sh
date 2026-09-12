#!/usr/bin/env bash
# Live-reload dev loop: rebuild the site on every note save and auto-refresh
# the browser. Requires fswatch (brew install fswatch).
#
# usage: scripts/dev.sh [port]
set -euo pipefail
PORT="${1:-8000}"
cd "$(dirname "$0")/.."

export MATHATLAS_DEV=1

echo "→ Initial build..."
clojure -M:run

echo "→ Serving docs/ on http://localhost:$PORT"
(cd docs && python3 -m http.server "$PORT") &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null' EXIT

echo "→ Watching notes/ and src/ for changes..."
fswatch -o notes src | while read -r _; do
  echo "→ Change detected, rebuilding..."
  clojure -M:run || echo "  Build failed, waiting for next change."
done
