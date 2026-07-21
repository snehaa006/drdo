# DRDO GIS — On-Site Runbook
### Setup · Configuration · Testing · Every Error and Its Fix

The single document to follow on the DRDO machine (offline). **No Docker, no Python,
no Node, no Maven at runtime** — the app ships prebuilt as one jar. On the machine
you need only **Java** and **PostgreSQL** (you manage it in pgAdmin).

Read Sections 1–5 to get it running. Section 6 tests the workflow, Section 7 edge
cases, and **Section 8 is the master error list** — find your symptom, apply the fix.

---

## 1. What you have

| Path | Purpose |
|------|---------|
| `deploy/manual/backend.jar` | **The whole app** (API + web UI) in one file — run with Java. |
| `deploy/manual/run.bat` / `run.sh` | Start scripts — double-click `run.bat` on Windows. |
| `deploy/manual/application.properties` | Editable DB settings (no rebuild needed). |
| `data/terrain/geotiff/india_basemap.tif` | Real India base map (replaceable). |
| `data/terrain/dted/` | Sample DTED (replace with DRDO's `.dt1` files). |
| `backend/` , `frontend/` | Source — only needed to *rebuild* the jar (OFFLINE_SETUP §8). |

**On the machine you need only two things:**
- **Java 17+** (JDK or JRE) — check with `java -version`.
- **PostgreSQL with the PostGIS extension** — the one you already open in pgAdmin.

Nothing is built or downloaded on this machine. (Maven, Node and the `~/.m2` /
`node_modules` libraries are only for rebuilding the jar — see Section 9.)

---

## 2. Configuration — `deploy/manual/application.properties`

The start scripts read `deploy/manual/application.properties` on every launch, so
you change settings **without rebuilding anything**. The values you may change:

```properties
# Database — must match the database + role you create in pgAdmin (Section 3)
spring.datasource.url=jdbc:postgresql://localhost:5432/drdo_gis
spring.datasource.username=drdo_user
spring.datasource.password=drdo_secret

# Terrain (optional — the scripts already point these at this project's data/ folder)
# gis.terrain.dted-base-path=C:/drdo/terrain/dted
# gis.terrain.geotiff-base-path=C:/drdo/terrain/geotiff
# gis.terrain.default-base-map=india_basemap.tif
```

**Decision guide:**

- **Database** — the `url` / `username` / `password` must match the database and
  role you create in pgAdmin (Section 3). Defaults are `drdo_gis` /
  `drdo_user` / `drdo_secret`.
- **DTED** — drop DRDO's `.dt1` files into `data/terrain/dted/` (any layout), or set
  `gis.terrain.dted-base-path` to the folder where they already are (or append it on
  the command line: `run.bat --gis.terrain.dted-base-path=C:/drdo/dted`). Layout /
  casing don't matter — the reader uses each file's header. Windows: forward slashes.
- **Base map** — to use DRDO's imagery, put their `.tif` in `data/terrain/geotiff/`
  and set `gis.terrain.default-base-map=<their-file>.tif`. It must be **EPSG:3857**
  (see §8-D).

Backend/UI port is **8080** (both come from the one jar). Change only if busy:
append `--server.port=8081` to the start script.

---

## 3. Setup the database (once, in pgAdmin — no psql)

You already use pgAdmin, so PostgreSQL is running. Do all of this in pgAdmin's
**Query Tool** — you do **not** need the `psql` command line.

1. **Create the database** — right-click *Databases → Create → Database*, name it
   **`drdo_gis`**.
2. **Create the login role** — right-click *Login/Group Roles → Create*; name
   **`drdo_user`**, set password **`drdo_secret`** (Definition tab), enable
   *Can login?* (Privileges tab).
3. **Enable PostGIS** — open the **Query Tool on the `drdo_gis` database** and run
   the following **as the `postgres` superuser** (enabling an extension needs
   superuser rights — do it once here so the app never has to):
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   CREATE EXTENSION IF NOT EXISTS postgis_topology;
   GRANT ALL ON SCHEMA public TO drdo_user;
   GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
   ```

**Do not create tables** — the backend creates them itself on first start. Because
PostGIS is already enabled here, the app's own `CREATE EXTENSION IF NOT EXISTS` step
is a harmless no-op even though `drdo_user` is not a superuser.

> **How the app talks to the database:** PostgreSQL runs as a background service
> listening on a port (5432 by default — see pgAdmin *server → Properties →
> Connection*). The app connects to that port over JDBC using the settings in
> `application.properties` — the same host/port/user/password pgAdmin uses. Nothing
> goes through a terminal or through psql. If pgAdmin connects, the app connects.

---

## 4. Run it (one command)

- **Windows:** double-click **`deploy\manual\run.bat`** (or run it in a terminal).
- **Linux / macOS:** `./deploy/manual/run.sh`

Wait for **`Started GisDeploymentApplication`**, then open **http://localhost:8080**.
The **same** jar serves the web UI (at `/`) and the API (at `/api/v1`) on port 8080.
First run creates all tables in `drdo_gis`. Leave the window open; stop with `Ctrl+C`
or by closing the window.

*(Plain Java without the script, from the repo root:)*
```bash
java -jar deploy/manual/backend.jar \
  --spring.config.additional-location=optional:file:deploy/manual/application.properties \
  --gis.terrain.dted-base-path="$PWD/data/terrain/dted" \
  --gis.terrain.geotiff-base-path="$PWD/data/terrain/geotiff"
```

---

## 5. First-run verification (smoke tests)

```bash
curl http://localhost:8080/api/actuator/health          # -> {"status":"UP"}
curl http://localhost:8080/api/v1/tiles/geotiff          # -> lists your base map
curl http://localhost:8080/api/v1/deployments            # -> [] (or your deployments)
```

In the run window's log, look for:
```
Registered 1 new GeoTIFF tiles
Indexed N DTED tile(s) by header origin ...   (N > 0 means DTED was found)
Started GisDeploymentApplication ...
```
Then open **http://localhost:8080** — you should see the India map (or DRDO's).

---

## 6. Full workflow test

| # | Action | Expected result |
|---|--------|-----------------|
| 1 | Open http://localhost:8080 | Base map renders (not just a grid). |
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

### A. Starting the app
| Symptom | Cause → Fix |
|---------|-------------|
| `java: command not found` | Java not installed / on PATH → install Java 17+ (Temurin). |
| `UnsupportedClassVersionError` | Java too old → needs **Java 17 or newer**. |
| `run.bat`/`run.sh` prints "'java' was not found" | Same as above — install Java 17+ and reopen the terminal so PATH updates. |
| `no main manifest attribute` / jar won't run | `backend.jar` is truncated (partial copy/clone) → re-copy it; it should be ~82 MB. |
| Port 8080 already in use | Append `--server.port=8081` to the start script, then use that port in the URLs. |
| Nothing at http://localhost:8080 | You opened the wrong port (it's **8080**, not 4200), or the log hasn't reached `Started` yet — wait for that line. |

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

### C. Web UI (served by the same jar on 8080)
| Symptom | Cause → Fix |
|---------|-------------|
| "Failed to load deployments" toast | The app isn't fully up → confirm `curl http://localhost:8080/api/actuator/health` returns `UP`; wait for the `Started` line. |
| Blank white page | Opened before startup finished, or stale cache → wait for `Started`, then hard-refresh (Ctrl+Shift+R). |
| Map never loads / JS errors | Open browser console (F12), note the error; usually stale cache → hard-refresh. |
| Page not found at `/` | You're on the wrong port — the UI is at **http://localhost:8080** (not 4200). |
| *(Developers only)* running `ng serve` on 4200, CORS error | The dev server's origin `http://localhost:4200` is allowed in `WebConfig`; make sure the backend is running on 8080. |

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
| Works on the server but not from another machine | The jar already listens on all interfaces — just browse to `http://<server-ip>:8080` from the other PC. Ensure port **8080** is open in the firewall. |

---

## 9. Useful commands

```bash
# Start the app (Windows: deploy\manual\run.bat)
./deploy/manual/run.sh

# App health
curl http://localhost:8080/api/actuator/health

# What base maps does the app see?
curl http://localhost:8080/api/v1/tiles/geotiff

# Peek at the database — run in pgAdmin Query Tool on drdo_gis:
#   SELECT count(*) FROM deployment;

# Clear a stuck Liquibase lock — pgAdmin Query Tool on drdo_gis:
#   DELETE FROM databasechangeloglock;

# Rebuild the jar (only if you changed the code; needs a network) — see OFFLINE_SETUP §8
cd backend && mvn clean package && cp target/*.jar ../deploy/manual/backend.jar
```

---

## 10. Quick reference card

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

Everything runs fully offline. No internet, CDN, or external service is used at
runtime.
