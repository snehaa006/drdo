# DRDO GIS — Offline Terrain-Aware Deployment Geometry System

Offline GIS system for DRDO-style deployment geometry generation. Two parts only:

- **Backend** — Java 17 + Spring Boot 3 (GeoTools, JTS, PostGIS)
- **Frontend** — Angular 15 (OpenLayers 7, Turf.js)

No Docker. No Python. Runs fully offline once the build dependencies are in place.

```
drdo-gis/
├── backend/          Spring Boot API  (run with Maven → java)
├── frontend/         Angular 15 app   (run with ng serve)
└── data/
    └── terrain/
        ├── dted/     DTED elevation tiles  (*.dt0 / *.dt1 / *.dt2)
        └── geotiff/  Offline GeoTIFF base map (*.tif) — india_basemap.tif ships
```

---

## Prerequisites (already-present at DRDO)

| Tool | Needed for | Check |
|------|-----------|-------|
| **Java 17** (JDK) | Building & running the backend | `java -version` → 17 |
| **Maven 3** | Building the backend | `mvn -version` |
| **Node.js 18+** & npm | Running the Angular frontend | `node -v`, `npm -v` |
| **PostgreSQL 15 + PostGIS 3.3** | Database (you already use pgAdmin) | pgAdmin connects |

> **Offline note:** Maven and npm each need their libraries the first time
> (`~/.m2` for Maven, `node_modules` for npm). On an air-gapped machine, populate
> these once from DRDO's internal Maven/npm mirror, or build on a connected
> machine and carry the folders over. After that first fetch, everything runs
> with no network. See **[OFFLINE_SETUP.md](OFFLINE_SETUP.md)**.

---

## Quick start (three terminals / steps)

### 1. Database — do this once, in **pgAdmin** (no `psql` needed)

You already have pgAdmin. Use its **Query Tool** for every SQL command below —
you do **not** need the `psql` command line at all.

1. In pgAdmin, create a database named **`drdo_gis`** (right-click *Databases →
   Create → Database*).
2. Create a login role **`drdo_user`** with password **`drdo_secret`**
   (*Login/Group Roles → Create*; on the *Definition* tab set the password, on
   *Privileges* tab enable *Can login*).
3. Open the **Query Tool on the `drdo_gis` database** and run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   CREATE EXTENSION IF NOT EXISTS postgis_topology;
   GRANT ALL ON SCHEMA public TO drdo_user;
   GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
   ```

That's the whole database setup. The backend connects to it over the network
(JDBC) — exactly the way pgAdmin does — so if pgAdmin can connect, the backend
can too. The connection settings live in
`backend/src/main/resources/application.yml` and are already set to the
name/user/password above. Change them there only if you used different values.

### 2. Backend — Spring Boot API on port 8080

```bash
cd backend
mvn spring-boot:run
```
On first start the backend runs its Liquibase migrations and **creates all the
tables itself** in `drdo_gis` — you don't create any tables by hand. It serves
the API at **http://localhost:8080/api/v1** (health: `/api/actuator/health`).

To produce a portable JAR instead (so it can be launched with only Java):
```bash
cd backend
mvn clean package                 # builds target/gis-deployment-system-1.0.0-SNAPSHOT.jar
java -jar target/gis-deployment-system-1.0.0-SNAPSHOT.jar
```
(Run the JAR from inside `backend/` so the default `../data/terrain/...` paths
resolve to the repo's `data/` folder.)

### 3. Frontend — Angular app on port 4200

```bash
cd frontend
npm install        # once (or when dependencies change)
npm start          # = ng serve
```
Open **http://localhost:4200**. The dev server calls the backend on port 8080
(CORS is already allowed for `localhost:4200`). To reach it from another PC on
the LAN: `npm start -- --host 0.0.0.0` and browse to `http://<this-pc-ip>:4200`.

---

## Terrain data

Place files under `./data/` (or point the backend at DRDO's folders — see below).

**DTED** (`.dt0/.dt1/.dt2`) — layout and file-name casing don't matter; each file
records the 1° cell it covers in its own header, so the reader finds the right
tile regardless of naming:
```
data/terrain/dted/n28/e077.dt1      # sample layout
data/terrain/dted/E077/N28.DT1      # standard DTED — also works
```

**GeoTIFF** base map — auto-discovered on startup and served to the map:
```
data/terrain/geotiff/india_basemap.tif    # ships by default (real India relief)
```
A real India base map ships so the map is recognizable out of the box. Replace it
with DRDO's aerial/satellite GeoTIFF for production. For the browser map to line
up it should be **Web-Mercator (EPSG:3857)**; reproject once (on a connected
machine) with `gdalwarp -t_srs EPSG:3857 -of COG in.tif out.tif`. When no readable
GeoTIFF is present the map falls back to a coordinate graticule so it is never blank.

To use different folders without moving files, override the paths when starting
the backend:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --gis.terrain.dted-base-path=C:/drdo/terrain/dted \
  --gis.terrain.geotiff-base-path=C:/drdo/terrain/geotiff \
  --gis.terrain.default-base-map=drdo_map.tif"
```
or, if running the JAR:
```bash
java -jar target/gis-deployment-system-1.0.0-SNAPSHOT.jar \
  --gis.terrain.dted-base-path=C:/drdo/terrain/dted \
  --gis.terrain.default-base-map=drdo_map.tif
```

---

## API reference

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

Swagger UI: **http://localhost:8080/api/swagger-ui.html**

## System flow

```
User clicks map
  → Frontend sends {lat, lon, frontage, depth, slopeThreshold, heading}
  → Backend loads nearby DTED tiles
  → TerrainEngine samples 11×11 elevation grid
  → Classifies terrain: planar / non-planar
  → If planar  → BezierEngine generates ellipse
  → If non-planar → directional slope factors → distorted anchors
                  → Catmull-Rom handles → cubic Bézier polygon
  → PolygonValidationService validates / repairs (JTS buffer(0))
  → GeoJSON returned → OpenLayers renders editable geometry
  → User drags control points → PUT /control-points → regenerated → persisted
```

## Tech stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular 15, TypeScript, SCSS, OpenLayers 7, Turf.js |
| Backend | Spring Boot 3.1, Java 17 |
| Geo libs | GeoTools 29, JTS 1.19, Proj4J 1.3 |
| Database | PostgreSQL 15 + PostGIS 3.3 |
| Build | Maven 3, Node 18, Angular CLI 15 |

All operation is **fully offline** — no internet, CDN, cloud tiles, or external
APIs are used at runtime.
