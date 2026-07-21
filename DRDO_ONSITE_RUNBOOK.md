# DRDO GIS — On-Site Runbook
### Setup · Configuration · Testing · Every Error and Its Fix

The single document to follow on the DRDO machine (offline). No Docker, no Python
— only **Java + Spring Boot** (backend), **Angular** (frontend), and
**PostgreSQL** (you manage it in pgAdmin).

Read Sections 1–5 to get it running. Section 6 tests the workflow, Section 7 edge
cases, and **Section 8 is the master error list** — find your symptom, apply the fix.

---

## 1. What you have

| Path | Purpose |
|------|---------|
| `backend/` | Spring Boot API — build with Maven, run with Java. |
| `frontend/` | Angular 15 app — run with `ng serve`. |
| `backend/src/main/resources/application.yml` | Backend config: DB + terrain paths. |
| `data/terrain/geotiff/india_basemap.tif` | Real India base map (replaceable). |
| `data/terrain/dted/` | Sample DTED (replace with DRDO's `.dt1` files). |

**On the machine you need:** Java 17 (JDK), Maven 3, Node.js 18+ with npm, and
PostgreSQL 15 with the PostGIS 3.3 extension. See **OFFLINE_SETUP.md §1** for how
to get the Maven/npm libraries onto an air-gapped machine.

---

## 2. Configuration — `application.yml`

Everything the backend needs is in `backend/src/main/resources/application.yml`.
The values you may change:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/drdo_gis   # host:port/dbname — match pgAdmin
    username: drdo_user
    password: drdo_secret
gis:
  terrain:
    dted-base-path: ../data/terrain/dted             # folder with DRDO's .dt1 files
    geotiff-base-path: ../data/terrain/geotiff        # folder with the base-map .tif
    default-base-map: india_basemap.tif               # which .tif to show as the map
```

**Decision guide:**

- **Database** — the `url` / `username` / `password` must match the database and
  role you create in pgAdmin (Section 3). Defaults are `drdo_gis` /
  `drdo_user` / `drdo_secret`.
- **DTED** — drop DRDO's `.dt1` files into `data/terrain/dted/` (any layout), or
  set `dted-base-path` to the folder where they already are. Layout / casing don't
  matter — the reader uses each file's header. Windows: use forward slashes,
  e.g. `C:/drdo/dted`.
- **Base map** — to use DRDO's imagery, put their `.tif` in `data/terrain/geotiff/`
  and set `default-base-map: <their-file>.tif`. It must be **EPSG:3857** (see §8-F).

Frontend port (4200) and backend port (8080): change only if busy — backend port
via `--server.port=8081`, frontend via `ng serve --port 4201`.

---

## 3. Setup the database (once, in pgAdmin — no psql)

You already use pgAdmin, so PostgreSQL is running. Do all of this in pgAdmin's
**Query Tool** — you do **not** need the `psql` command line.

1. **Create the database** — right-click *Databases → Create → Database*, name it
   **`drdo_gis`**.
2. **Create the login role** — right-click *Login/Group Roles → Create*; name
   **`drdo_user`**, set password **`drdo_secret`** (Definition tab), enable
   *Can login?* (Privileges tab).
3. **Enable PostGIS** — open the **Query Tool on the `drdo_gis` database** and run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   CREATE EXTENSION IF NOT EXISTS postgis_topology;
   GRANT ALL ON SCHEMA public TO drdo_user;
   GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
   ```

**Do not create tables** — the backend creates them itself on first start.

> **How the app talks to the database:** PostgreSQL runs as a background service
> listening on a port (5432 by default — see pgAdmin *server → Properties →
> Connection*). The backend connects to that port over JDBC using the settings in
> `application.yml` — the same host/port/user/password pgAdmin uses. Nothing goes
> through a terminal or through psql. If pgAdmin connects, the backend connects.

---

## 4. Run it (two terminals)

**Terminal 1 — backend** (from the `backend` folder):
```bash
cd backend
mvn spring-boot:run
```
Starts on **http://localhost:8080** (API base `/api`). First run creates all
tables. Leave it running.

*(Alternative — build a JAR and run with plain Java, from inside `backend/`:)*
```bash
mvn clean package
java -jar target/gis-deployment-system-1.0.0-SNAPSHOT.jar
```

**Terminal 2 — frontend** (from the `frontend` folder):
```bash
cd frontend
npm install     # first time only
npm start       # = ng serve, on port 4200
```
Open **http://localhost:4200**.

---

## 5. First-run verification (smoke tests)

```bash
curl http://localhost:8080/api/actuator/health          # -> {"status":"UP"}
curl http://localhost:8080/api/v1/tiles/geotiff          # -> lists your base map
curl http://localhost:8080/api/v1/deployments            # -> [] (or your deployments)
```

In the backend (Terminal 1) log, look for:
```
Registered 1 new GeoTIFF tiles
Indexed N DTED tile(s) by header origin ...   (N > 0 means DTED was found)
Started GisDeploymentApplication ...
```
Then open **http://localhost:4200** — you should see the India map (or DRDO's).

---

## 6. Full workflow test

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
| 10 | Restart the backend | Your remaining deployments are still there (persisted in PostgreSQL). |

Both ways of setting the location — **typing coordinates** and **clicking the
map** — are supported and interchangeable.

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
| Swap base map | change `default-base-map`, restart | New imagery appears. |

---

## 8. Error catalog — symptom → cause → fix

### A. Backend build / start
| Symptom | Cause → Fix |
|---------|-------------|
| `mvn: command not found` | Maven not installed / on PATH → install Maven 3, or build a JAR on a connected machine and run it with Java (OFFLINE_SETUP §1). |
| `java: command not found` | Java 17 not installed / on PATH → install JDK 17. |
| `UnsupportedClassVersionError` | Wrong Java version → needs **Java 17**. |
| Maven can't download dependencies | No network / no mirror → populate `~/.m2` first (OFFLINE_SETUP §1). |
| Port 8080 already in use | Add `--server.port=8081` (and use that port in the URLs). |

### B. Database
| Symptom | Cause → Fix |
|---------|-------------|
| `Connection refused` to `localhost:5432` | PostgreSQL not running, or wrong host/port → confirm pgAdmin connects; match its host/port in `application.yml`. |
| `database "drdo_gis" does not exist` | Create it in pgAdmin (§3.1). |
| `password authentication failed for user "drdo_user"` | Role/password mismatch → align `application.yml` with the pgAdmin role (§3.2). |
| `could not open extension control file ... postgis` | PostGIS package not installed → install PostGIS for this PostgreSQL, then re-run §3.3. |
| `permission denied to create extension "postgis"` | Run the `CREATE EXTENSION` lines (§3.3) as a Postgres **superuser** in pgAdmin. |
| `Could not acquire change log lock` | A previous run crashed mid-migration → in pgAdmin Query Tool on `drdo_gis`: `DELETE FROM databasechangeloglock;` then restart the backend. |
| `psql` not recognised in the terminal | Expected — psql isn't on PATH. You don't need it; use pgAdmin's Query Tool. (Optional: OFFLINE_SETUP §2 shows the full psql path.) |
| Want a clean/empty database | In pgAdmin, drop & recreate `drdo_gis` (⚠ deletes all saved deployments), then re-run §3.3 and restart the backend. |

### C. Frontend
| Symptom | Cause → Fix |
|---------|-------------|
| `ng: command not found` | Angular CLI/Node missing → install Node 18+; `npm start` uses the local CLI once `npm install` has run. |
| `npm install` fails offline | `node_modules`/registry not available → OFFLINE_SETUP §1 (internal mirror or carry `node_modules`). |
| "Failed to load deployments" toast | Backend not reachable → confirm backend is UP (`curl .../actuator/health`) on 8080. |
| Blank white page | Frontend didn't compile → check the `ng serve` terminal for errors; hard-refresh (Ctrl+Shift+R). |
| Map never loads / JS errors | Open browser console (F12), note the error; usually stale cache → hard-refresh. |
| CORS error in console | Frontend served from an unexpected origin → run it via `ng serve` on 4200 (allowed origin), or add your origin to `WebConfig`. |

### D. Base map / GeoTIFF
| Symptom | Cause → Fix |
|---------|-------------|
| Map shows only a **grid**, no imagery | (1) No readable `.tif` in the geotiff folder; (2) `default-base-map` name doesn't match a file; (3) the `.tif` isn't EPSG:3857. Check `curl http://localhost:8080/api/v1/tiles/geotiff` and the backend log. |
| Imagery appears **offset / wrong place** | GeoTIFF not EPSG:3857 → reproject once on a networked machine: `gdalwarp -t_srs EPSG:3857 -of COG in.tif out.tif`. |
| Tile request returns **500** | The `.tif` is corrupt / not a valid TIFF → replace it. |
| Base map loads very slowly | Huge, non-tiled GeoTIFF → convert to Cloud-Optimized: `gdal_translate in.tif out.tif -of COG`. |

### E. DTED / terrain
| Symptom | Cause → Fix |
|---------|-------------|
| **Everywhere** is flat: Mean Elevation 0, always PLANAR/ELLIPSE | DTED not found → backend log shows `Indexed 0 DTED tile(s)` → `dted-base-path` wrong or folder has no `.dt1` files. Fix the path, restart. |
| Flat **only in some areas** | Those points have no covering tile → normal; add tiles for that area if needed. |
| A `.dt1` file exists but isn't used | Not a valid DTED file (bad header) → verify the file. |

### F. Deployment / workflow
| Symptom | Cause → Fix |
|---------|-------------|
| "Validation failed" / HTTP 400 on create | Value out of range: **lat −90…90, lon −180…180, frontage 10–5000 m, depth 5–2000 m, slope 0–90°, heading 0–360°** → correct the input. |
| **COMPUTE GEOMETRY** stays greyed out | Latitude/Longitude empty or out of range → enter valid coordinates (type or click the map). |
| **EDIT GEOMETRY** button missing | The deployment is an **ELLIPSE** (planar) — only adaptive Bézier shapes are editable. By design. |
| Geometry shows **INVALID** | Rare; the polygon couldn't be repaired → note the coordinates/params and report. |

### G. Accessing from another PC on the LAN
| Symptom | Cause → Fix |
|---------|-------------|
| Works on the server but not from another machine | Start the frontend with `ng serve --host 0.0.0.0` and browse to `http://<server-ip>:4200`. Ensure ports 4200/8080 are open in the firewall. |

---

## 9. Useful commands

```bash
# Backend health
curl http://localhost:8080/api/actuator/health

# What base maps does the backend see?
curl http://localhost:8080/api/v1/tiles/geotiff

# Peek at the database — run in pgAdmin Query Tool on drdo_gis:
#   SELECT count(*) FROM deployment;

# Clear a stuck Liquibase lock — pgAdmin Query Tool on drdo_gis:
#   DELETE FROM databasechangeloglock;

# Rebuild the backend JAR
cd backend && mvn clean package

# Reinstall frontend deps
cd frontend && npm install
```

---

## 10. Quick reference card

| Thing | Value |
|-------|-------|
| Web UI | http://localhost:4200 |
| API base | http://localhost:8080/api/v1 |
| Health check | http://localhost:8080/api/actuator/health |
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| Config file | `backend/src/main/resources/application.yml` |
| Start backend | `cd backend && mvn spring-boot:run` |
| Start frontend | `cd frontend && npm start` |
| Database admin | pgAdmin (Query Tool) — no psql needed |
| DTED: any layout? | Yes — files are matched by their header, not their name. |
| Base map must be | EPSG:3857 GeoTIFF |
| Set location | Type Lat/Lon **or** click the map |

Everything runs fully offline. No internet, CDN, or external service is used at
runtime.
