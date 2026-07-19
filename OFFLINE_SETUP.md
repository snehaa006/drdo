# DRDO GIS — Offline Setup Guide (on-site, no internet)

This guide sets up the whole system on a machine with **no network access**.
Everything needed is already inside this project folder — nothing is downloaded
on-site.

There are two ways to run it. Use **Path A (Docker)** if the machine has Docker;
it is the easiest and includes the database. Use **Path B (manual)** if there is
no Docker.

> **You only ever edit `.env`.** All data locations (DTED, GeoTIFF, base map,
> ports, DB credentials) are configured there — no code changes, ever.

---

## 0. What you carry in

Copy this **entire project folder** to the DRDO machine (USB / internal transfer).
The important offline pieces are:

| Path | What it is |
|------|-----------|
| `docker-compose.offline.yml` | Runs the pre-built containers (no build). |
| `deploy/images/drdo-gis-images.tar.gz` | The 3 pre-built images (backend, frontend, PostGIS). **~700 MB.** |
| `deploy/manual/` | No-Docker fallback: `backend.jar`, `frontend-dist/`, `serve_frontend.py`. |
| `.env` | The one file you edit on-site. |
| `data/terrain/geotiff/india_basemap.tif` | Real India base map (swap for DRDO's). |
| `data/terrain/dted/` | Where DTED tiles go (or point `.env` at DRDO's folder). |

The pre-built images are **linux/amd64** (standard Windows/Linux/Intel machines).
If the DRDO machine is an Apple-Silicon Mac, use **Path B** instead — its
`backend.jar` runs on any CPU.

---

## Path A — Docker (recommended)

### A1. Load the images (one time)
```bash
docker load -i deploy/images/drdo-gis-images.tar.gz
```
This imports `drdo-gis-backend`, `drdo-gis-frontend`, and `postgis/postgis:15-3.3`.
Verify:
```bash
docker images | grep -E "drdo-gis|postgis"
```

### A2. Point at DRDO's terrain data — edit `.env`
Open `.env` and set the two data directories. Either:

* **Option 1 — drop files into this project** (simplest): put DRDO's DTED tiles
  into `data/terrain/dted/` and their base map into `data/terrain/geotiff/`, and
  leave `.env` at its defaults.
* **Option 2 — point at DRDO's existing folders**: set the paths, e.g.
  ```
  DTED_HOST_DIR=/mnt/drdo/terrain/dted          # Linux
  GEOTIFF_HOST_DIR=/mnt/drdo/terrain/geotiff
  ```
  (On Windows use forward slashes: `C:/drdo/terrain/dted`.)

Set which base map to show:
```
DEFAULT_BASE_MAP=india_basemap.tif    # or DRDO's own file name
```

### A3. Start
```bash
docker compose -f docker-compose.offline.yml up -d
```
Open **http://localhost:4200** in the browser. That's it.

### A4. Stop / restart
```bash
docker compose -f docker-compose.offline.yml down      # stop
docker compose -f docker-compose.offline.yml up -d      # start again
```
Deployments you create are stored in the `drdo_pgdata` Docker volume and persist
across restarts (i.e. **on the DRDO machine**).

---

## Path B — Manual (no Docker)

Requires these to already be on the machine (install from DRDO's internal
sources if needed):

* **Java 17** (JDK or JRE) — `java -version` should show 17.
* **Python 3** — `python3 --version` (used only to serve the frontend).
* **PostgreSQL 15 with the PostGIS 3.3 extension installed.**

### B1. Prepare the database (one time)
As a Postgres admin, create the database and user:
```sql
CREATE DATABASE drdo_gis;
CREATE USER drdo_user WITH PASSWORD 'drdo_secret';
GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
\c drdo_gis
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
GRANT ALL ON SCHEMA public TO drdo_user;
```
(The app also tries to create the extensions itself on first run; doing it here
as admin avoids needing superuser rights for the app user.)

### B2. Start the backend
From the project folder:
```bash
java -jar deploy/manual/backend.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/drdo_gis \
  --spring.datasource.username=drdo_user \
  --spring.datasource.password=drdo_secret \
  --gis.terrain.dted-base-path=./data/terrain/dted \
  --gis.terrain.geotiff-base-path=./data/terrain/geotiff \
  --gis.terrain.default-base-map=india_basemap.tif
```
Change the two `...-base-path` values to DRDO's DTED / GeoTIFF folders as needed.
The backend runs on **http://localhost:8080** (API base `/api/v1`).

### B3. Start the frontend (new terminal)
```bash
python3 deploy/manual/serve_frontend.py
```
This serves the UI on **http://localhost:4200** and forwards `/api` calls to the
backend on 8080 — open that URL in the browser.

To stop: `Ctrl+C` in each terminal.

---

## Swapping the base map (either path)

The map shows whichever GeoTIFF `DEFAULT_BASE_MAP` names, out of the files in the
GeoTIFF folder.

1. Put DRDO's base map (a `.tif` / `.tiff`) in the GeoTIFF folder.
2. Set `DEFAULT_BASE_MAP=<their-file-name>.tif` in `.env` (Path A) or
   `--gis.terrain.default-base-map=<their-file-name>.tif` (Path B).
3. Restart the backend.

> **Base-map requirement:** for the browser map to line up, the GeoTIFF should be
> in **Web-Mercator (EPSG:3857)**. The shipped `india_basemap.tif` already is. If
> DRDO's imagery is in a different projection (e.g. UTM or lat/lon 4326), reproject
> it once with GDAL:
> `gdalwarp -t_srs EPSG:3857 -of COG input.tif output.tif`

---

## Using DRDO's DTED data

The system reads DTED elevation tiles (`.dt0` / `.dt1` / `.dt2`) from the DTED
folder. Two ways to supply DRDO's data:

* copy their tiles into `data/terrain/dted/`, **or**
* set `DTED_HOST_DIR` in `.env` (Path A) / `--gis.terrain.dted-base-path` (Path B)
  to the folder where their tiles already live.

**Folder layout does not matter.** Each DTED file records the 1° cell it covers
inside its own header, so the reader finds the right tile by reading the files —
regardless of how they are named or foldered. All of these work:
```
<dted-folder>/n28/e077.dt1         # the sample layout
<dted-folder>/E077/N28.DT1         # standard DTED (upper-case, lon/lat)
<dted-folder>/anything/N28E077.DT1 # a single flat folder, any names
```
Just point `DTED_HOST_DIR` at the folder that contains DRDO's `.dt1` files (the
reader scans it, including sub-folders). No renaming or reorganising needed.

> If a location has **no** covering tile, the system logs a warning and treats
> that spot as elevation 0 (flat) rather than failing — so a partial DTED set
> still works for the areas it covers.

---

## Verifying it works

```bash
# backend healthy
curl http://localhost:8080/api/actuator/health          # -> {"status":"UP"}
# base map registered (your default should be first)
curl http://localhost:8080/api/v1/tiles/geotiff
```
Then in the browser (http://localhost:4200): the India map should appear, and
clicking the map → **Compute Geometry** should draw a deployment polygon.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `docker load` says "no space" | The image tar needs ~1.5 GB free while loading. |
| Port already in use | Change `FRONTEND_PORT` / `BACKEND_PORT` / `POSTGRES_PORT` in `.env`. |
| Map is blank, only a grid | No readable GeoTIFF found, or `DEFAULT_BASE_MAP` name doesn't match a file in the GeoTIFF folder. Check the file name and that it is EPSG:3857. |
| Map shows but is offset/misaligned | The GeoTIFF is not in EPSG:3857 — reproject with the `gdalwarp` command above. |
| Backend won't start, DB errors (Path B) | PostGIS not installed, or the DB user can't create the extension — run the `CREATE EXTENSION` lines in B1 as an admin. |
| Apple-Silicon Mac, Docker images won't run | Use **Path B** (the JAR is CPU-independent). |

Everything runs fully offline — no internet, CDN, or external services are used at
any point.
