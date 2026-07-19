# DRDO GIS — On-Site Runbook
### Setup · Configuration · Testing · Every Error and Its Fix

This is the single document to follow on the DRDO machine (offline). It assumes
**no internet** and **no ability to ask anyone** — everything you need is here and
inside this project folder.

Read Sections 1–5 to get it running. Use Section 6 to test the workflow, Section 7
for edge cases, and **Section 8 is the master error list** — find your symptom and
apply the fix.

---

## 1. What you have (bundle contents)

| Path | Purpose |
|------|---------|
| `.env` | **The only file you edit.** All paths, base map, ports, DB creds. |
| `docker-compose.offline.yml` | Runs the pre-built containers (no build, no internet). |
| `deploy/images/drdo-gis-images.tar.gz` | The 3 pre-built images (~396 MB). |
| `deploy/manual/` | No-Docker fallback: `backend.jar`, `frontend-dist/`, `serve_frontend.py`. |
| `data/terrain/geotiff/india_basemap.tif` | Real India base map (replaceable). |
| `data/terrain/dted/` | Sample DTED (replace with DRDO's `.dt1` files). |

> Pre-built images are **linux/amd64** (standard Windows/Linux/Intel PCs). If the
> machine is an Apple-Silicon Mac, use **Path B (manual)** — the `.jar` runs on any CPU.

---

## 2. Configuration flow — the `.env` file

Open `.env` in a text editor. You only ever change these:

```
DTED_HOST_DIR=./data/terrain/dted        # folder with DRDO's .dt1 files
GEOTIFF_HOST_DIR=./data/terrain/geotiff  # folder with the base-map .tif
DEFAULT_BASE_MAP=india_basemap.tif       # which .tif to show as the map
FRONTEND_PORT=4200                       # change if the port is busy
BACKEND_PORT=8080
POSTGRES_PORT=5432
POSTGRES_DB=drdo_gis
POSTGRES_USER=drdo_user
POSTGRES_PASSWORD=drdo_secret
```

**Decision guide:**

- **DTED** — either drop DRDO's `.dt1` files into `data/terrain/dted/` (any layout),
  **or** set `DTED_HOST_DIR` to the folder where their files already are.
  *Folder layout / file-name casing does not matter* — the system reads each file's
  internal header to know which area it covers. `E077/N28.DT1`, `n28/e077.dt1`, or a
  single flat folder all work.
- **Base map** — to use DRDO's own imagery, put their `.tif` in `data/terrain/geotiff/`
  and set `DEFAULT_BASE_MAP=<their-file>.tif`. It must be **EPSG:3857** (see §8-G).
- **Ports** — only change if 4200 / 8080 / 5432 are already in use on the machine.

Windows paths: use forward slashes, e.g. `DTED_HOST_DIR=C:/drdo/dted`.

---

## 3. Setup — Path A: Docker (recommended)

Copy the whole project folder to the machine, open a terminal **in that folder**, then:

```bash
# 1. Load the pre-built images (one time)
docker load -i deploy/images/drdo-gis-images.tar.gz

# 2. (edit .env as per Section 2)

# 3. Start everything
docker compose -f docker-compose.offline.yml up -d
```

Open **http://localhost:4200**. Done — the database is inside the bundle, nothing
else to install.

Stop / start again:
```bash
docker compose -f docker-compose.offline.yml down
docker compose -f docker-compose.offline.yml up -d
```

---

## 4. Setup — Path B: Manual (no Docker)

Needs on the machine: **Java 17**, **Python 3**, **PostgreSQL 15 + PostGIS 3.3**.

```bash
# 1. Create the database (once), as a Postgres admin:
psql -c "CREATE DATABASE drdo_gis;"
psql -c "CREATE USER drdo_user WITH PASSWORD 'drdo_secret';"
psql -c "GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;"
psql -d drdo_gis -c "CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS postgis_topology; GRANT ALL ON SCHEMA public TO drdo_user;"

# 2. Start the backend (terminal 1)
java -jar deploy/manual/backend.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/drdo_gis \
  --spring.datasource.username=drdo_user \
  --spring.datasource.password=drdo_secret \
  --gis.terrain.dted-base-path=./data/terrain/dted \
  --gis.terrain.geotiff-base-path=./data/terrain/geotiff \
  --gis.terrain.default-base-map=india_basemap.tif

# 3. Start the frontend (terminal 2)
python3 deploy/manual/serve_frontend.py
```

Open **http://localhost:4200**.

---

## 5. First-run verification (smoke tests)

Run these after starting (works for both paths):

```bash
curl http://localhost:8080/api/actuator/health          # -> {"status":"UP"}
curl http://localhost:8080/api/v1/tiles/geotiff          # -> lists your base map first
curl http://localhost:4200/api/v1/deployments            # -> [] (or your deployments)
```

Check the backend registered the terrain data (Docker: `docker logs drdo_backend`):
```
Registered 1 new GeoTIFF tiles
Indexed N DTED tile(s) by header origin ...   (N > 0 means DTED was found)
Started GisDeploymentApplication ...
```

Then open **http://localhost:4200** — you should see the India map (or DRDO's).

---

## 6. Full workflow test

Do this once end-to-end to confirm everything works.

| # | Action | Expected result |
|---|--------|-----------------|
| 1 | Open http://localhost:4200 | Base map renders (not just a grid). |
| 2 | Click **+ NEW DEPLOYMENT** | The **Deployment Parameters** panel opens with Latitude/Longitude fields. |
| 3 | **Type** Latitude `28.6` and Longitude `77.2` | Compute Geometry button becomes enabled. |
| 4 | Set Frontage `250`, Depth `125`, click **COMPUTE GEOMETRY** | Map pans to the point and draws a polygon; detail panel shows Geometry Type + **Terrain Analysis**. |
| 5 | Instead of typing, **click anywhere on the map** | The Lat/Lon fields fill in from the click; you can then Compute. |
| 6 | Look at **Terrain Analysis** | Shows Suitability %, PLANAR/NON-PLANAR, mean/max slope, elevation, samples. |
| 7 | On a **NON-PLANAR** deployment, click **✎ EDIT GEOMETRY**, drag a control point, **✔ SAVE EDITS** | Geometry reshapes and persists. |
| 8 | Select a deployment in the left list | Map pans to it and shows its geometry + details. |
| 9 | Click **DELETE** on a deployment | It disappears from the list. |
| 10 | `down` then `up -d` again (or restart backend) | Your remaining deployments are still there (persisted). |

Both ways of setting the location — **typing coordinates** and **clicking the map** —
are supported and interchangeable.

---

## 7. Edge cases to test (with expected behaviour)

| Test | Input | Expected |
|------|-------|----------|
| Flat/plains inside DTED | 28.6 N, 77.2 E | Real elevation shown; usually NON-PLANAR adaptive Bézier. |
| **Outside DTED coverage** | a point with no tile | Mean Elevation **0 m**, **100% PLANAR**, an **ELLIPSE** (safe fallback, not an error). |
| Steep/hilly terrain | a Himalayan cell (if you have that DTED) | NON-PLANAR, higher slope, visibly deformed shape. |
| Slope threshold = 90 | any point | Treated as planar → **ELLIPSE**. |
| Slope threshold = 0 | any point with slope | Treated as non-planar → **BÉZIER_ADAPTIVE**. |
| Min sizes | Frontage 10, Depth 5 | Small valid polygon. |
| Max sizes | Frontage 5000, Depth 2000 | Large valid polygon. |
| Below min | Frontage 5 | Button disabled / 400 "Validation failed". |
| Bad latitude | 100 | Field marked invalid; Compute stays disabled. |
| Heading | 0 vs 90 | Shape orientation rotates. |
| ELLIPSE geometry | any planar result | **EDIT GEOMETRY hidden** (only adaptive shapes are editable — by design). |
| No base map | remove all `.tif`, restart | Map falls back to a coordinate grid — never blank. |
| Swap base map | change `DEFAULT_BASE_MAP`, restart | New imagery appears. |

---

## 8. Error catalog — symptom → cause → fix

### A. Docker & image loading
| Symptom | Cause → Fix |
|---------|-------------|
| `docker: command not found` | Docker not installed → use **Path B (manual)**. |
| `Cannot connect to the Docker daemon` | Docker not running → start Docker Desktop (wait for it to say "running"); Linux: `sudo systemctl start docker`. |
| `docker load` → `unexpected EOF` / `invalid tar` | Corrupted copy → re-copy `drdo-gis-images.tar.gz`; verify with `gzip -t deploy/images/drdo-gis-images.tar.gz`. |
| `no space left on device` on load | Need ~1.5 GB free → free disk space, retry. |
| `no matching manifest for linux/arm64` | Machine is Apple-Silicon; images are amd64 → use **Path B**. |
| Compose tries to **build** / `failed to solve` | You used the wrong compose file → use `-f docker-compose.offline.yml` and run `docker load` first. |
| Containers start but app is very slow | amd64 images under emulation on ARM → use **Path B**. |

### B. Ports
| Symptom | Cause → Fix |
|---------|-------------|
| `Bind for 0.0.0.0:4200 failed: port is already allocated` | Port in use → change `FRONTEND_PORT` in `.env`, `up -d` again. |
| Same for 8080 / 5432 | Change `BACKEND_PORT` / `POSTGRES_PORT` in `.env`. |

### C. `.env` / configuration
| Symptom | Cause → Fix |
|---------|-------------|
| `.env` edits have no effect | Run compose **from the folder containing `.env`**; force refresh: `docker compose -f docker-compose.offline.yml up -d --force-recreate`. |
| Mount error / path not found | The `DTED_HOST_DIR`/`GEOTIFF_HOST_DIR` path doesn't exist → create it or fix the path. Windows: use forward slashes. |

### D. Database (Docker)
| Symptom | Cause → Fix |
|---------|-------------|
| Backend keeps restarting, "connection refused" | Postgres not ready yet → wait ~20 s; check `docker compose -f docker-compose.offline.yml ps` shows **postgres (healthy)**. |
| `Could not acquire change log lock` | A previous run crashed mid-migration → `docker exec drdo_postgres psql -U drdo_user -d drdo_gis -c "DELETE FROM databasechangeloglock;"` then restart backend. |
| Want a clean/empty database | `docker compose -f docker-compose.offline.yml down -v` (⚠ deletes all saved deployments), then `up -d`. |

### E. Database (manual / Path B)
| Symptom | Cause → Fix |
|---------|-------------|
| `database "drdo_gis" does not exist` | Run the CREATE DATABASE step in §4. |
| `could not open extension control file ... postgis` | PostGIS not installed → install the PostGIS package for your PostgreSQL. |
| `permission denied to create extension "postgis"` | Run the `CREATE EXTENSION` lines in §4 as a **superuser**. |
| `password authentication failed` | Credentials don't match → align the `--spring.datasource.*` args with the DB user. |

### F. Backend startup
| Symptom | Cause → Fix |
|---------|-------------|
| `java: command not found` (Path B) | Install Java 17 / put it on PATH. |
| `UnsupportedClassVersionError` | Wrong Java → needs **Java 17**. |
| Port 8080 already in use | Change `BACKEND_PORT` (Docker) or `--server.port` (manual). |

### G. Base map / GeoTIFF
| Symptom | Cause → Fix |
|---------|-------------|
| Map shows only a **grid**, no imagery | (1) No readable `.tif` in the geotiff folder; (2) `DEFAULT_BASE_MAP` name doesn't match a file; (3) the `.tif` isn't EPSG:3857. Check `curl http://localhost:8080/api/v1/tiles/geotiff` and `docker logs drdo_backend`. |
| Imagery appears **offset / wrong place** | GeoTIFF is not EPSG:3857 → reproject once: `gdalwarp -t_srs EPSG:3857 -of COG in.tif out.tif` (needs GDAL on a networked machine, done beforehand). |
| Tile request returns **500** | The `.tif` is corrupt / not a valid TIFF → replace it. |
| Base map loads very slowly | Huge, non-tiled GeoTIFF → convert to Cloud-Optimized: `gdal_translate in.tif out.tif -of COG`. |

### H. DTED / terrain
| Symptom | Cause → Fix |
|---------|-------------|
| **Everywhere** is flat: Mean Elevation 0, always PLANAR/ELLIPSE | DTED not found → `docker logs drdo_backend` shows `Indexed 0 DTED tile(s)` → `DTED_HOST_DIR` is wrong or the folder has no `.dt1` files. Fix the path, restart. |
| Flat **only in some areas** | Those points have no covering tile → normal; add tiles for that area if needed. |
| A `.dt1` file exists but isn't used | It's not a valid DTED file (bad header) → verify the file. |
| Elevation values look wrong | Wrong DTED data/level for the area → check the source data. |

### I. Deployment / workflow
| Symptom | Cause → Fix |
|---------|-------------|
| "Validation failed" / HTTP 400 on create | A value is out of range: **lat −90…90, lon −180…180, frontage 10–5000 m, depth 5–2000 m, slope 0–90°, heading 0–360°** → correct the input. |
| **COMPUTE GEOMETRY** stays greyed out | Latitude/Longitude empty or out of range → enter valid coordinates (type them or click the map). |
| **EDIT GEOMETRY** button missing | The deployment is an **ELLIPSE** (planar) — only adaptive Bézier shapes are editable. This is by design. |
| Geometry shows **INVALID** | Rare; the polygon couldn't be repaired → note the coordinates/params and report. |

### J. Frontend / UI
| Symptom | Cause → Fix |
|---------|-------------|
| "Failed to load deployments" toast | Backend not reachable → check backend is UP (`curl …/actuator/health`); Docker: ensure all 3 containers are up; Manual: ensure `serve_frontend.py` is running and the backend jar is up. |
| Blank white page | Frontend not served → Docker: `docker logs drdo_frontend`; Manual: confirm `frontend-dist/` exists next to `serve_frontend.py`. |
| Map never loads / JS errors | Open the browser console (F12) and note the error; usually a stale cache → hard-refresh (Ctrl+Shift+R). |

### K. Accessing from another PC on the LAN
| Symptom | Cause → Fix |
|---------|-------------|
| Works on the server but not from another machine | Use the **server's IP** in the browser (e.g. `http://10.x.x.x:4200`). The app calls the API on the same address automatically (relative path), so this works once the port is reachable through any firewall. |

---

## 9. Useful commands

```bash
# Status of all containers
docker compose -f docker-compose.offline.yml ps

# Live backend logs (terrain/DTED/GeoTIFF messages, errors)
docker logs -f drdo_backend

# Restart just the backend (after changing DTED files / .env)
docker compose -f docker-compose.offline.yml up -d --force-recreate backend

# Peek at the database
docker exec drdo_postgres psql -U drdo_user -d drdo_gis -c "SELECT count(*) FROM deployment;"

# Full reset (⚠ wipes all deployments)
docker compose -f docker-compose.offline.yml down -v && docker compose -f docker-compose.offline.yml up -d

# What base maps does the backend see?
curl http://localhost:8080/api/v1/tiles/geotiff
```

---

## 10. Quick reference card

| Thing | Value |
|-------|-------|
| Web UI | http://localhost:4200 |
| API base | http://localhost:8080/api/v1 |
| Health check | http://localhost:8080/api/actuator/health |
| Config file | `.env` (this folder) |
| Start (Docker) | `docker compose -f docker-compose.offline.yml up -d` |
| Stop (Docker) | `docker compose -f docker-compose.offline.yml down` |
| Backend logs | `docker logs -f drdo_backend` |
| DTED: any layout? | Yes — files are matched by their header, not their name. |
| Base map must be | EPSG:3857 GeoTIFF |
| Set location | Type Lat/Lon **or** click the map |

Everything runs fully offline. No internet, CDN, or external service is used at any point.
