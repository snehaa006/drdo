# DRDO GIS — Offline Terrain-Aware Deployment Geometry System

Offline GIS system for DRDO-style deployment geometry generation.

- **Backend** — Java 17 + Spring Boot 3 (GeoTools, JTS, PostGIS)
- **Frontend** — Angular 15 (OpenLayers 7, Turf.js)
- **Database** — PostgreSQL + PostGIS (managed in pgAdmin)

**No Docker. No Python. No internet.** A prebuilt jar ships in this repo, so on the
target machine you only need **Java** and **PostgreSQL** — nothing is downloaded or
built there.

```
drdo-gis/
├── deploy/manual/
│   ├── backend.jar            ← the whole app (API + web UI) in one file
│   ├── run.bat / run.sh       ← start it (double-click run.bat on Windows)
│   └── application.properties ← edit DB settings here (no rebuild needed)
├── backend/          Spring Boot source (only needed to rebuild the jar)
├── frontend/         Angular 15 source (already built into the jar)
└── data/terrain/
    ├── dted/         DTED elevation tiles (*.dt0 / *.dt1 / *.dt2)
    └── geotiff/      GeoTIFF base map (*.tif) — india_basemap.tif ships
```

---

## The fast path (this is all DRDO needs)

Two things must be present on the machine: **Java 17+** (`java -version`) and a
running **PostgreSQL with the PostGIS extension** (the one you already open in
pgAdmin). Then:

### 1. Database — once, in pgAdmin (no `psql` needed)

Use pgAdmin's **Query Tool** for everything below — you never need the `psql`
command line.

1. Create a database named **`drdo_gis`** (right-click *Databases → Create → Database*).
2. Create a login role **`drdo_user`** with password **`drdo_secret`**
   (*Login/Group Roles → Create*; set the password on *Definition*, turn on
   *Can login?* on *Privileges*).
3. Open the **Query Tool on `drdo_gis`** and run this **as the `postgres`
   superuser** (enabling an extension needs superuser — do it once here so the app
   never has to):
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   CREATE EXTENSION IF NOT EXISTS postgis_topology;
   GRANT ALL ON SCHEMA public TO drdo_user;
   GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
   ```

That is the whole database setup — **do not create any tables**, the app creates
them itself. If you used a different name/user/password, put them in
`deploy/manual/application.properties`.

> **"Is the database connected to the terminal / running on a port?"** You don't
> connect a terminal to it. PostgreSQL runs as a background service on a port (5432
> by default). The app connects to that port over the network (JDBC), exactly the
> way pgAdmin does. If pgAdmin can connect, the app can too.

### 2. Start the app — one command

- **Windows:** double-click **`deploy\manual\run.bat`** (or run it in a terminal).
- **Linux / macOS:** `./deploy/manual/run.sh`

Wait for the line `Started GisDeploymentApplication`, then open
**http://localhost:8080** in a browser. That's it — the **same** jar serves the web
UI *and* the API on port 8080. On first start it creates all its tables
automatically (Liquibase).

No Maven, no Node, no `npm install`, no internet. Just Java + PostgreSQL.

---

## Terrain data

Files live under `./data/` (the run scripts point the app there automatically). To
use DRDO's own folders instead, either edit `deploy/manual/application.properties`
or append the path when starting:

```bash
./deploy/manual/run.sh --gis.terrain.dted-base-path=/mnt/drdo/dted
```
```bat
run.bat --gis.terrain.dted-base-path=C:/drdo/dted
```

**DTED** (`.dt0/.dt1/.dt2`) — layout and file-name casing don't matter; each file
records the 1° cell it covers in its own header, so the reader finds the right tile
regardless of naming (`n28/e077.dt1`, `E077/N28.DT1`, or one flat folder all work).
A point with no covering tile is treated as flat (elevation 0) with a logged
warning, so a partial DTED set still works for its area.

**GeoTIFF base map** — `data/terrain/geotiff/india_basemap.tif` ships and is
auto-discovered on startup. Replace it with DRDO's aerial/satellite `.tif` for
production; it should be **Web-Mercator (EPSG:3857)** so it lines up in the browser
(reproject once on a connected machine: `gdalwarp -t_srs EPSG:3857 -of COG in.tif
out.tif`). When no readable GeoTIFF is present the map falls back to a coordinate
graticule so it is never blank.

---

## API reference

The API is under `http://localhost:8080/api/v1`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST   | /api/v1/deployments | Create deployment + compute geometry |
| GET    | /api/v1/deployments | List all deployments |
| GET    | /api/v1/deployments/{uid} | Get deployment detail |
| GET    | /api/v1/deployments/{uid}/geojson | Get GeoJSON geometry |
| PUT    | /api/v1/deployments/{uid}/control-points | Update control points |
| DELETE | /api/v1/deployments/{uid} | Delete deployment |
| GET    | /api/v1/terrain/deployment/{uid} | Get terrain analysis |
| POST   | /api/v1/terrain/deployment/{uid}/recompute | Re-analyse terrain |
| PUT    | /api/v1/geometry/deployment/{uid}/edit | Apply geometry edit |
| POST   | /api/v1/tiles/scan | Scan & register GeoTIFF tiles |
| GET    | /api/v1/tiles/bbox | List tiles in bounding box |
| GET    | /api/v1/tiles/geotiff | List available offline GeoTIFF base maps (+ bounds) |
| GET    | /api/v1/tiles/geotiff/{name} | Stream a GeoTIFF raster for the map base layer |

Health check: **http://localhost:8080/api/actuator/health** ·
Swagger UI: **http://localhost:8080/api/swagger-ui.html**

## System flow

```
User clicks map (or types Lat/Lon)
  → Frontend sends {lat, lon, frontage, depth, slopeThreshold, heading}
  → Backend loads nearby DTED tiles
  → TerrainEngine samples an elevation grid
  → Classifies terrain: planar / non-planar
  → If planar  → BezierEngine generates ellipse
  → If non-planar → directional slope factors → distorted anchors
                  → Catmull-Rom handles → cubic Bézier polygon
  → PolygonValidationService validates / repairs (JTS buffer(0))
  → GeoJSON returned → OpenLayers renders editable geometry
  → User drags control points → PUT /control-points → regenerated → persisted
```

---

## Rebuilding from source (optional — only with internet or an internal mirror)

You do **not** need this to run at DRDO. It's only for changing the code. Building
needs the third-party libraries, which are not shipped:

- **Backend:** `cd backend && mvn clean package` → `target/gis-deployment-system-1.0.0-SNAPSHOT.jar`.
  Needs Maven and a network (or a populated `~/.m2`). GeoTools comes from the OSGeo
  repository declared in `backend/pom.xml`, **not** Maven Central — a mirror must
  include it. The Angular UI is committed under `backend/src/main/resources/static/`,
  so this jar already contains the web UI (Node is **not** required to rebuild the
  backend). Copy the result over `deploy/manual/backend.jar`.
- **Frontend:** `cd frontend && npm install && npm run build` → `dist/`. Needs Node 18+.
  Copy `dist/drdo-gis-frontend/*` into `backend/src/main/resources/static/` and
  rebuild the backend to bundle the new UI.

For live frontend development you can still run the two dev servers separately:
`cd backend && mvn spring-boot:run` and `cd frontend && npm start` (UI on
http://localhost:4200, calling the API on 8080 with CORS).

See **[OFFLINE_SETUP.md](OFFLINE_SETUP.md)** and **[DRDO_ONSITE_RUNBOOK.md](DRDO_ONSITE_RUNBOOK.md)**
for the full offline guide and the error catalog.

## Tech stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular 15, TypeScript, SCSS, OpenLayers 7, Turf.js |
| Backend | Spring Boot 3.1, Java 17 |
| Geo libs | GeoTools 29, JTS 1.19, Proj4J 1.3 |
| Database | PostgreSQL + PostGIS |
| Build (optional) | Maven 3, Node 18, Angular CLI 15 |

All operation is **fully offline** — no internet, CDN, cloud tiles, or external
APIs are used at runtime.
