# DRDO GIS — Offline-First Terrain-Aware Deployment Geometry System

Enterprise-grade offline GIS system for DRDO-style deployment geometry generation.

## Architecture

```
drdo-gis/
├── backend/          Spring Boot 3 + GeoTools + JTS + PostGIS
├── frontend/         Angular 15 + OpenLayers 7 + Turf.js
├── docker-compose.yml
└── data/             (mount offline terrain data here)
    ├── terrain/
    │   ├── dted/     DTED Level 0/1/2 files  (*.dt0, *.dt1, *.dt2)
    │   └── geotiff/  Offline GeoTIFF imagery (*.tif, *.tiff)
```

## Quick Start

### Prerequisites
- Docker 24+ & Docker Compose 2+
- Offline DTED/GeoTIFF terrain files mounted at `./data/`

### Run
```bash
docker-compose up -d
```
- Frontend: http://localhost:4200
- Backend API: http://localhost:8080/api/v1

### Development (without Docker)

**Backend**
```bash
cd backend
# Ensure PostgreSQL+PostGIS running at localhost:5432
mvn spring-boot:run
```

**Frontend**
```bash
cd frontend
npm install
ng serve
```

## Terrain Data Setup

Place files in the `./data/` directory:

**DTED files** — follow DTED naming convention:
```
data/terrain/dted/n28/e077.dt1   # covers lat 28-29, lon 77-78
data/terrain/dted/n29/e077.dt1
```

**GeoTIFF files** — any projection supported; auto-discovered on startup:
```
data/terrain/geotiff/india_region.tif
data/terrain/geotiff/delhi_highres.tiff
```

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST   | /v1/deployments | Create deployment + compute geometry |
| GET    | /v1/deployments | List all deployments |
| GET    | /v1/deployments/{uid} | Get deployment detail |
| GET    | /v1/deployments/{uid}/geojson | Get GeoJSON geometry |
| PUT    | /v1/deployments/{uid}/control-points | Update control points |
| DELETE | /v1/deployments/{uid} | Delete deployment |
| GET    | /v1/terrain/deployment/{uid} | Get terrain analysis |
| POST   | /v1/terrain/deployment/{uid}/recompute | Re-analyse terrain |
| PUT    | /v1/geometry/deployment/{uid}/edit | Apply geometry edit |
| POST   | /v1/tiles/scan | Scan & register GeoTIFF tiles |
| GET    | /v1/tiles/bbox | List tiles in bounding box |

## System Flow

```
User clicks map
  → Frontend sends {lat, lon, frontage, depth, slopeThreshold, heading}
  → Backend loads nearby DTED tiles
  → TerrainEngine samples 11×11 elevation grid
  → Classifies terrain: planar / non-planar
  → If planar  → BezierEngine generates ellipse
  → If non-planar → directional slope factors computed
                  → anchor points distorted proportionally
                  → Catmull-Rom handles generated
                  → cubic Bézier polygon built
  → PolygonValidationService validates / repairs
  → GeoJSON returned to frontend
  → OpenLayers renders editable geometry
  → User drags control points → PUT /control-points
  → Backend regenerates Bézier curve → persists to PostGIS
```

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Planar → ellipse | Mathematically exact for flat terrain |
| Non-planar → adaptive Bézier | Smooth organic deformation toward suitable terrain |
| Distortion proportional to slope | Preserves deployment intent while fitting terrain |
| JTS buffer(0) repair | Industry-standard polygon repair strategy |
| PostGIS spatial indexes | Sub-millisecond spatial queries at scale |
| Liquibase migrations | Reproducible schema across environments |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular 15, TypeScript, SCSS, OpenLayers 7, Turf.js |
| Backend | Spring Boot 3.1, Java 17 |
| Geo libs | GeoTools 29, JTS 1.19, Proj4J 1.3 |
| Database | PostgreSQL 15 + PostGIS 3.3 |
| Build | Maven 3, Node 18, Angular CLI 15 |

All operation is **fully offline** — no internet, CDN, cloud tiles, or external APIs required.