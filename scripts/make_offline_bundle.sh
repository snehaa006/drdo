#!/usr/bin/env bash
# Regenerates the offline deployment bundle (run on a machine WITH network).
# Produces:
#   deploy/images/drdo-gis-images.tar.gz   (3 pre-built linux/amd64 images)
#   deploy/manual/{backend.jar,frontend-dist/,serve_frontend.py}
#
# Prereqs: Docker (with buildx), Java 17, Node 18, Maven.
# Usage:   scripts/make_offline_bundle.sh [arch]     # arch defaults to amd64
set -euo pipefail
cd "$(dirname "$0")/.."
ARCH="${1:-amd64}"
PLATFORM="linux/${ARCH}"

echo "==> [1/5] Building backend jar"
( cd backend && mvn -q -DskipTests clean package )

echo "==> [2/5] Building frontend dist"
( cd frontend && npm install --no-audit --no-fund && npm run build -- --configuration production )
cp -r frontend/dist/drdo-gis-frontend deploy/manual/frontend-dist
cp backend/target/gis-deployment-system-1.0.0-SNAPSHOT.jar deploy/manual/backend.jar

echo "==> [3/5] Building ${PLATFORM} images from the pre-built artifacts"
docker buildx build --platform "$PLATFORM" -f backend/Dockerfile.offline  -t drdo-gis-backend:latest  --load backend/
docker buildx build --platform "$PLATFORM" -f frontend/Dockerfile.offline -t drdo-gis-frontend:latest --load frontend/

echo "==> [4/5] Pulling ${PLATFORM} PostGIS"
docker pull --platform "$PLATFORM" postgis/postgis:15-3.3

echo "==> [5/5] Saving images to deploy/images/drdo-gis-images.tar.gz"
mkdir -p deploy/images
docker save drdo-gis-backend:latest drdo-gis-frontend:latest postgis/postgis:15-3.3 \
  | gzip -c > deploy/images/drdo-gis-images.tar.gz

echo "Done. Bundle ready:"
ls -lah deploy/images/drdo-gis-images.tar.gz
