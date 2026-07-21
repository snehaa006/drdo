#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  DRDO GIS — start the whole system (backend API + web UI) from ONE jar.
#  Requirements on this machine:  Java 17+  and  a running PostgreSQL+PostGIS.
#  NO Maven, NO Node, NO Python, NO Docker are needed.
#  When it prints "Started", open:  http://localhost:8080
#  Stop it with Ctrl+C.
#
#  DRDO's terrain elsewhere? append the path, e.g.:
#     ./run.sh --gis.terrain.dted-base-path=/mnt/drdo/dted
# ─────────────────────────────────────────────────────────────────────────────
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: 'java' was not found on PATH. Install Java 17+ (Temurin) first." >&2
  exit 1
fi

echo "Starting DRDO GIS ...  the UI will be at  http://localhost:8080  (Ctrl+C to stop)"
exec java -jar "$SCRIPT_DIR/backend.jar" \
  --spring.config.additional-location="optional:file:$SCRIPT_DIR/application.properties" \
  --gis.terrain.dted-base-path="$REPO_ROOT/data/terrain/dted" \
  --gis.terrain.geotiff-base-path="$REPO_ROOT/data/terrain/geotiff" \
  "$@"
