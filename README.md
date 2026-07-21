# DRDO GIS — Offline Guide

Terrain-aware deployment-geometry system that runs **fully offline** on the DRDO
machine. This is the **only** document you need — setup, running, configuration,
testing, and every error and its fix.

- **Backend + UI** — one Java jar (Spring Boot 3, GeoTools, JTS, PostGIS + Angular 15)
- **Database** — PostgreSQL with PostGIS, managed in pgAdmin

**No Docker. No Python. No Node. No Maven. No internet.** The app ships prebuilt as a
single jar (`deploy/manual/backend.jar`, committed in this repo), so on the target
machine you only need **Java** and **PostgreSQL**. The jar serves both the web UI and
the API on **http://localhost:8080**.

---

## Contents

1. [What's in this folder](#1-whats-in-this-folder)
2. [Prerequisites](#2-prerequisites)
3. [Set up the database in pgAdmin](#3-set-up-the-database-in-pgadmin)
4. [Configure the app](#4-configure-the-app)
5. [Run it](#5-run-it)
6. [Verify it works](#6-verify-it-works)
7. [Using the app](#7-using-the-app)
8. [Terrain data](#8-terrain-data)
9. [Troubleshooting — every error and its fix](#9-troubleshooting--every-error-and-its-fix)
10. [Rebuilding from source (optional)](#10-rebuilding-from-source-optional)
11. [Quick reference](#11-quick-reference)

---

## 1. What's in this folder

| Path | Purpose |
|------|---------|
| `deploy/manual/backend.jar` | **The whole app** (API + web UI) in one file — run with Java. |
| `deploy/manual/run.bat` / `run.sh` | Start scripts — double-click `run.bat` on Windows. |
| `deploy/manual/application.properties` | Editable DB settings (no rebuild needed). |
| `data/terrain/geotiff/india_basemap.tif` | India base map (EPSG:3857) — replace with DRDO's. |
| `data/terrain/dted/` | Sample DTED tiles — replace with DRDO's `.dt1` files. |
| `backend/` , `frontend/` | Source — only needed to *rebuild* the jar (Section 10). |

---

## 2. Prerequisites

Only two things must be on the machine:

| Tool | Why | Check |
|------|-----|-------|
| **Java 17+** (JDK or JRE) | Runs the jar | `java -version` |
| **PostgreSQL + PostGIS** | The database (you already open it in pgAdmin) | pgAdmin connects |

If the machine has nothing installed, install these two from installers you carried
in (download them while you still have internet):

- **Java** — Eclipse Temurin 17 (Windows `.msi`, Linux `.tar.gz`, macOS `.pkg`).
- **PostgreSQL 15 + PostGIS 3.3+** — Windows: the EDB PostgreSQL installer, then the
  **PostGIS bundle** for that version (grab the bundle file while online — the
  StackBuilder step needs a network). Linux: `postgresql-15` and
  `postgresql-15-postgis-3` packages **plus their dependencies**.

> **PostGIS is required and cannot be skipped.** The app stores real geometry and runs
> spatial queries, so the `postgis` extension must be available for your PostgreSQL.

Nothing else is needed — no Maven, Node, `~/.m2`, or `node_modules`. Those are only for
rebuilding the jar (Section 10), which DRDO does not need to do.

---

## 3. Set up the database in pgAdmin

You already have pgAdmin and it connects to PostgreSQL, so the server is running
(usually on port **5432** — see *right-click the server → Properties → Connection →
Port*). Do everything below in pgAdmin's **Query Tool** — the `psql` command line is
**not required** at any point.

**3.1** Create the database: right-click *Databases → Create → Database*, name it
**`drdo_gis`**.

**3.2** Create the login role: right-click *Login/Group Roles → Create → Login/Group
Role*, name **`drdo_user`**; on the *Definition* tab set password **`drdo_secret`**; on
the *Privileges* tab turn on *Can login?*.

**3.3** Open the **Query Tool on `drdo_gis`** (click the `drdo_gis` database, then the
Query Tool button) and run the following **as the `postgres` superuser** — enabling an
extension needs superuser rights, so do it once here and the app never has to:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
GRANT ALL ON SCHEMA public TO drdo_user;
GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
```

That's the whole database setup — **do not create any tables**, the app creates them
itself on first start. Because you enabled PostGIS here as superuser, the app's own
`CREATE EXTENSION IF NOT EXISTS` step later is a harmless no-op even though `drdo_user`
is a normal (non-superuser) role.

> **"Is the database connected to the terminal / running on a port?"** You don't connect
> a terminal to it. PostgreSQL runs as a background service on a TCP port (5432 by
> default). The app connects to that port over the network (JDBC), exactly the way
> pgAdmin does. **If pgAdmin can connect, the app can too** — you don't need `psql`.

> **If you really want `psql` (optional):** it ships inside PostgreSQL's `bin` folder but
> is usually not on PATH — that's why `psql` "isn't found". Full path examples: Windows
> `"C:\Program Files\PostgreSQL\15\bin\psql.exe"`, Linux `/usr/bin/psql`. Then
> `psql -h localhost -p 5432 -U drdo_user -d drdo_gis`. But the pgAdmin Query Tool does
> everything psql would.

---

## 4. Configure the app

`deploy/manual/application.properties` is read fresh every time you start the app, so you
change settings **without rebuilding anything**. The defaults match Section 3:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/drdo_gis
spring.datasource.username=drdo_user
spring.datasource.password=drdo_secret
```
If you created the database with a different name/user/password or on a different
host/port, edit these three lines and save. If you used the names above, change nothing.

---

## 5. Run it

- **Windows:** double-click **`deploy\manual\run.bat`** (or run it in a terminal).
- **Linux / macOS:** `./deploy/manual/run.sh`

Wait for the line **`Started GisDeploymentApplication`**, then open
**http://localhost:8080** in a browser. The **same** jar serves the web UI (at `/`) and
the API (at `/api/v1`) on port 8080. On first run it creates all tables in `drdo_gis`.
Leave the window open; stop it with `Ctrl+C` or by closing the window.

> Plain Java without the script, from the repo root:
> ```bash
> java -jar deploy/manual/backend.jar \
>   --spring.config.additional-location=optional:file:deploy/manual/application.properties \
>   --gis.terrain.dted-base-path="$PWD/data/terrain/dted" \
>   --gis.terrain.geotiff-base-path="$PWD/data/terrain/geotiff"
> ```

To reach it from another PC on the LAN: browse to `http://<server-ip>:8080` (the jar
already listens on all interfaces; open port 8080 in the firewall).

---

## 6. Verify it works

```bash
curl http://localhost:8080/api/actuator/health          # -> {"status":"UP"}
curl http://localhost:8080/api/v1/tiles/geotiff          # -> lists india_basemap.tif
curl http://localhost:8080/api/v1/deployments            # -> [] (or your deployments)
```
In the run window's log, look for:
```
Registered 1 new GeoTIFF tiles
Indexed N DTED tile(s) by header origin ...   (N > 0 means DTED was found)
Started GisDeploymentApplication ...
```
Then open **http://localhost:8080** — the India map (or DRDO's) should appear, and
clicking the map (or typing Lat/Lon) → **Compute Geometry** draws a deployment polygon.

---

## 7. Using the app

| # | Action | Expected result |
|---|--------|-----------------|
| 1 | Open http://localhost:8080 | Base map renders (falls back to a coordinate grid if no imagery). |
| 2 | Click **+ NEW DEPLOYMENT** | The **Deployment Parameters** panel opens with Latitude/Longitude fields. |
| 3 | Type Latitude `28.6`, Longitude `77.2` **or** click on the map | Lat/Lon fill in; **Compute Geometry** enables. |
| 4 | Set Frontage `250`, Depth `125`, click **COMPUTE GEOMETRY** | Map pans to the point and draws a polygon; detail panel shows Geometry Type + Terrain Analysis. |
| 5 | Look at **Terrain Analysis** | Suitability %, PLANAR/NON-PLANAR, mean/max slope, elevation, samples. |
| 6 | On a **NON-PLANAR** deployment: **✎ EDIT GEOMETRY**, drag a control point, **✔ SAVE EDITS** | Geometry reshapes and persists. |
| 7 | Select a deployment in the left list | Map pans to it, shows its geometry + details. |
| 8 | Click **DELETE** | It disappears from the list. |
| 9 | Restart the app | Remaining deployments are still there (persisted in PostgreSQL). |

**Valid input ranges:** lat −90…90, lon −180…180, frontage 10–5000 m, depth 5–2000 m,
slope 0–90°, heading 0–360°. Out-of-range values disable Compute or return HTTP 400.

**Edge cases (all expected, not errors):**

| Input | Expected |
|-------|----------|
| Point with no DTED tile | Mean Elevation **0 m**, **100% PLANAR**, an **ELLIPSE** (safe fallback). |
| Slope threshold 90 | Treated as planar → **ELLIPSE**. |
| Slope threshold 0 (on any slope) | Treated as non-planar → **BÉZIER_ADAPTIVE**. |
| An **ELLIPSE** result | **EDIT GEOMETRY hidden** — only adaptive shapes are editable, by design. |
| No base map present | Map falls back to a coordinate grid — never blank. |

---

## 8. Terrain data

The run scripts point the app at this project's `data/terrain/` automatically. To use
DRDO's own folders, either edit the `gis.terrain.*` lines in `application.properties`, or
append the path when starting:
```bash
./deploy/manual/run.sh --gis.terrain.dted-base-path=/mnt/drdo/dted
```
```bat
run.bat --gis.terrain.dted-base-path=C:/drdo/dted
```

**DTED** (`.dt0/.dt1/.dt2`) — **layout and file-name casing don't matter**; each file
records the 1° cell it covers in its own header, so the reader finds the right tile
regardless of naming (`n28/e077.dt1`, `E077/N28.DT1`, or one flat folder all work). A
location with no covering tile is treated as flat (elevation 0) with a logged warning, so
a partial DTED set still works for its area.

**GeoTIFF base map** — `data/terrain/geotiff/india_basemap.tif` ships and is
auto-discovered on startup. Replace it with DRDO's aerial/satellite `.tif` for production;
it should be **Web-Mercator (EPSG:3857)** so it lines up in the browser. Set
`gis.terrain.default-base-map=<their-file>.tif` and restart. Reproject others once on a
connected machine: `gdalwarp -t_srs EPSG:3857 -of COG input.tif output.tif`.

---

## 9. Troubleshooting — every error and its fix

### Starting the app
| Symptom | Cause → Fix |
|---------|-------------|
| `java: command not found` | Java not installed / on PATH → install Java 17+ (Temurin), reopen the terminal. |
| `UnsupportedClassVersionError` | Java too old → needs **Java 17 or newer**. |
| `run.bat`/`run.sh` prints "'java' was not found" | Same — install Java 17+ so `java -version` works. |
| `no main manifest attribute` / jar won't run | `backend.jar` is truncated (partial copy/clone) → re-copy it; it should be ~82 MB. |
| Port 8080 already in use | Append `--server.port=8081` to the start script, then use that port in the URLs. |
| Nothing at http://localhost:8080 | Wrong port (it's **8080**, not 4200), or the log hasn't reached `Started` yet — wait for that line. |

### Database
| Symptom | Cause → Fix |
|---------|-------------|
| `Connection refused` to `localhost:5432` | PostgreSQL not running, or wrong host/port → confirm pgAdmin connects; match its host/port in `application.properties`. |
| `database "drdo_gis" does not exist` | Create it in pgAdmin (§3.1). |
| `password authentication failed for user "drdo_user"` | Role/password mismatch → align `application.properties` with the pgAdmin role (§3.2). |
| `could not open extension control file ... postgis` | PostGIS package not installed for this PostgreSQL → install the PostGIS bundle/package, then re-run §3.3. |
| `permission denied to create extension "postgis"` | You skipped §3.3 → enable PostGIS once **as the `postgres` superuser** in pgAdmin, then start the app. |
| `Could not acquire change log lock` | A previous run crashed mid-migration → in pgAdmin Query Tool on `drdo_gis`: `DELETE FROM databasechangeloglock;` then restart. |
| `psql` not recognised in the terminal | Expected — psql isn't on PATH and you don't need it. Use pgAdmin's Query Tool (§3). |
| Want a clean/empty database | In pgAdmin, drop & recreate `drdo_gis` (⚠ deletes all saved deployments), re-run §3.3, restart the app. |

### Web UI (served by the same jar on 8080)
| Symptom | Cause → Fix |
|---------|-------------|
| "Failed to load deployments" | App not fully up → confirm `curl http://localhost:8080/api/actuator/health` returns `UP`; wait for `Started`. |
| Blank white page | Opened before startup finished, or stale cache → wait for `Started`, then hard-refresh (Ctrl+Shift+R). |
| Map never loads / JS errors | Open browser console (F12), note the error; usually stale cache → hard-refresh. |
| Page not found at `/` | Wrong port — the UI is at **http://localhost:8080**. |

### Base map / GeoTIFF
| Symptom | Cause → Fix |
|---------|-------------|
| Map shows only a **grid**, no imagery | No readable `.tif`, or `default-base-map` doesn't match a file, or the `.tif` isn't EPSG:3857. Check `curl http://localhost:8080/api/v1/tiles/geotiff` and the log. |
| Imagery appears **offset / wrong place** | GeoTIFF not EPSG:3857 → reproject: `gdalwarp -t_srs EPSG:3857 -of COG in.tif out.tif`. |
| Tile request returns **500** | The `.tif` is corrupt / not a valid TIFF → replace it. |
| Base map loads very slowly | Huge, non-tiled GeoTIFF → convert to Cloud-Optimized: `gdal_translate in.tif out.tif -of COG`. |

### DTED / terrain
| Symptom | Cause → Fix |
|---------|-------------|
| **Everywhere** is flat: Mean Elevation 0, always PLANAR/ELLIPSE | DTED not found → log shows `Indexed 0 DTED tile(s)` → the DTED path is wrong or has no `.dt1` files. Fix the path (§8), restart. |
| Flat **only in some areas** | Those points have no covering tile → normal; add tiles for that area if needed. |
| A `.dt1` file exists but isn't used | Not a valid DTED file (bad header) → verify the file. |

### Deployment / workflow
| Symptom | Cause → Fix |
|---------|-------------|
| "Validation failed" / HTTP 400 on create | Value out of range (see ranges in Section 7) → correct the input. |
| **COMPUTE GEOMETRY** stays greyed out | Latitude/Longitude empty or out of range → enter valid coordinates (type or click the map). |
| **EDIT GEOMETRY** button missing | The deployment is an **ELLIPSE** (planar) — only adaptive Bézier shapes are editable. By design. |
| Geometry shows **INVALID** | Rare; the polygon couldn't be repaired → note the coordinates/params and report. |

---

## 10. Rebuilding from source (optional — only with internet or an internal mirror)

You do **not** need this to run at DRDO. It's only for changing the code.

- **Backend + bundled UI:** `cd backend && mvn clean package` → copy
  `target/gis-deployment-system-1.0.0-SNAPSHOT.jar` over `deploy/manual/backend.jar`. The
  Angular UI is committed under `backend/src/main/resources/static/`, so the jar already
  contains the web UI — **Node is not needed to rebuild the backend**. Maven needs a
  network or a populated `~/.m2`; GeoTools comes from the OSGeo repository in
  `backend/pom.xml` (not Maven Central), so a mirror must include it. Build with Java 17.
- **Changing the UI:** `cd frontend && npm install && npm run build`, copy
  `dist/drdo-gis-frontend/*` into `backend/src/main/resources/static/`, then rebuild the
  backend. For live UI development, run `mvn spring-boot:run` and `npm start` separately
  (UI on http://localhost:4200, calling the API on 8080 with CORS).

---

## 11. Quick reference

| Thing | Value |
|-------|-------|
| Web UI | **http://localhost:8080** |
| API base | http://localhost:8080/api/v1 |
| Health check | http://localhost:8080/api/actuator/health |
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| Start the app | `deploy\manual\run.bat` (Windows) · `./deploy/manual/run.sh` (Linux/mac) |
| Config file | `deploy/manual/application.properties` (DB settings) |
| Needs on machine | Java 17+ and PostgreSQL+PostGIS — nothing else |
| Database admin | pgAdmin (Query Tool) — no psql needed |
| DTED: any layout? | Yes — files are matched by their header, not their name. |
| Base map must be | EPSG:3857 GeoTIFF |
| Set location | Type Lat/Lon **or** click the map |

Everything runs fully offline — no internet, CDN, or external service is used at runtime.
