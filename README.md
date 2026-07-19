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

> **Deploying on-site with no internet?** See **[OFFLINE_SETUP.md](OFFLINE_SETUP.md)** —
> it uses the pre-built image bundle in `deploy/` so nothing is downloaded on the
> target machine. All data paths are configured in **`.env`** (no code changes).

### Prerequisites
- Docker 24+ & Docker Compose 2+
- Offline DTED/GeoTIFF terrain files mounted at `./data/`

### Run
```bash
docker compose up -d
```
- Frontend: http://localhost:4200
- Backend API: http://localhost:8080/api/v1

All host-specific settings (terrain data folders, base-map file, ports, DB
credentials) live in **`.env`** and can be changed without touching any code.

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

**GeoTIFF files** — auto-discovered on startup and served to the map as the
offline base layer:
```
data/terrain/geotiff/india_basemap.tif      # shipped default (real India map)
data/terrain/geotiff/delhi_highres.tiff     # add DRDO's own imagery here
```
The GeoTIFF shown as the base layer is chosen by the **`GIS_TERRAIN_DEFAULT_BASE_MAP`**
setting (`DEFAULT_BASE_MAP` in `.env`); set it to DRDO's own file name to swap the
base map without any code change.

> For direct in-browser rendering by OpenLayers, GeoTIFFs should be
> **Web-Mercator (EPSG:3857)**, ideally Cloud-Optimized (COG). The map view
> uses EPSG:3857; OpenLayers' GeoTIFF source does not client-reproject rasters,
> so a 4326/UTM TIFF will load but may not align with the 3857 view. Reproject
> one with `gdalwarp -t_srs EPSG:3857 -of COG in.tif out.tif`. When no GeoTIFF is
> mounted, the map falls back to an offline coordinate graticule so it is never blank.

A real **India base map**, `data/terrain/geotiff/india_basemap.tif` (public-domain
Natural Earth relief, reprojected to EPSG:3857, covering 68–98°E / 6–38°N), ships
so the map is recognizable out of the box. Replace it with DRDO's real
aerial/satellite GeoTIFFs for production use. A synthetic placeholder can also be
generated fully offline (no GDAL required) with:
```bash
python3 scripts/generate_sample_geotiff.py --lon-min 75 --lon-max 79 --lat-min 28 --lat-max 31
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
| GET    | /v1/tiles/geotiff | List available offline GeoTIFF base maps (+ bounds) |
| GET    | /v1/tiles/geotiff/{name} | Stream a GeoTIFF raster for the map base layer |

## System Flow

```
User clicks map
  → Frontend sends {lat, lon, frontage, depth, slopeThreshold, heading}
  → Backend loads nearby DTED tiles
  → TerrainEngine samples 11×11 elevation grid
  → Classifies terrain: planar / non-planar
       (mean slope < user slopeThreshold AND low roughness → planar)
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
| `/v1/tiles/geotiff/{name}` serves `ResourceRegion` + honours `Range` | OpenLayers' GeoTIFF source (geotiff.js) fetches rasters in byte-range chunks and errors on a plain 200; Spring MVC (unlike WebFlux) does not add Range support for a controller returning `Resource` automatically, so it's implemented explicitly |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular 15, TypeScript, SCSS, OpenLayers 7, Turf.js |
| Backend | Spring Boot 3.1, Java 17 |
| Geo libs | GeoTools 29, JTS 1.19, Proj4J 1.3 |
| Database | PostgreSQL 15 + PostGIS 3.3 |
| Build | Maven 3, Node 18, Angular CLI 15 |

All operation is **fully offline** — no internet, CDN, cloud tiles, or external APIs required.