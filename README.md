# DRDO GIS — Offline Guide (Windows)

Terrain-aware deployment-geometry system that runs **fully offline** on the DRDO
Windows machine. This is the **only** document you need — setup, running, configuration,
testing, and every error and its fix.

- **Backend + UI** — one Java jar (Spring Boot 3, GeoTools, JTS, PostGIS + Angular 15)
- **Database** — PostgreSQL with PostGIS (set up with pgAdmin)

**No Docker. No Python. No Node. No Maven. No internet.** The app ships prebuilt as a
single jar (`deploy/manual/backend.jar`, committed in this repo), so on the target
machine you only need **Java** and **PostgreSQL**. The jar serves both the web UI and
the API on **http://localhost:8080**.

---

## Contents

1. [What's in this folder](#1-whats-in-this-folder)
2. [Prerequisites — check & install](#2-prerequisites--check-whats-there-install-whats-missing)
3. [Set up the database](#3-set-up-the-database)
4. [Tell the app how to connect — and confirm it worked](#4-tell-the-app-how-to-connect--and-confirm-it-worked)
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
| `deploy\manual\backend.jar` | **The whole app** (API + web UI) in one file — run with Java. |
| `deploy\manual\run.bat` | Start script — **double-click it** to launch the app. |
| `deploy\manual\application.properties` | Editable DB and port settings (no rebuild needed). |
| `data\terrain\geotiff\india_basemap.tif` | India base map (EPSG:3857) — replace with DRDO's. |
| `data\terrain\dted\` | Sample DTED tiles — replace with DRDO's `.dt1` files. |
| `backend\` , `frontend\` | Source — only needed to *rebuild* the jar (Section 10). |

---

## 2. Prerequisites — check what's there, install what's missing

Everything runs on **Windows**. Three things must be present. (Nothing else is needed —
no Docker, Python, Node, or Maven.)

| # | What | How to check it's present |
|---|------|---------------------------|
| 1 | **Java 17+** | Open **Command Prompt** and type `java -version` → shows 17 or higher |
| 2 | **PostgreSQL 15+**, running | *Services* (press <kbd>Win</kbd>+<kbd>R</kbd> → `services.msc`) shows a running *postgresql-x64-…* |
| 3 | **PostGIS** (add-on inside PostgreSQL) | Trying to enable it in Section 3 either succeeds, or errors with *"could not open extension control file"* (= not installed) |

### Install what's missing (offline — carry the installers in on a USB)

| Missing | Installer |
|---|---|
| **Java 17** | Eclipse **Temurin 17 `.msi`** (adoptium.net) — during install, tick *Add to PATH*. |
| **PostgreSQL (+ pgAdmin)** | **EDB PostgreSQL 15 `.exe`** (enterprisedb.com) — installs the server **and** pgAdmin together. **Write down the `postgres` password it makes you set** — you need it to open pgAdmin. |
| **PostGIS** | EDB **StackBuilder → Spatial Extensions → PostGIS**, or the standalone **PostGIS bundle `.exe`** for PG 15. |
| **pgAdmin** | *(already inside the EDB installer above)* |

> **Offline (air-gapped):** download every `.msi`/`.exe` installer while you still have
> internet and carry them on the USB. All of the above are single self-contained
> installers — no dependency resolution needed.

Once #1–#3 are present, continue to Section 3.

---

## 3. Set up the database

### The mental model (read this first)
- PostgreSQL is a **Windows service on port 5432**, always running. The app connects to
  it over the network (JDBC) — there's nothing to "keep open" in a window.
- The whole job is: create a database, create a login role, turn on PostGIS. Do it in
  **pgAdmin** (installed with PostgreSQL).

### In pgAdmin
1. Open **pgAdmin 4** (set a master password the first time — that's pgAdmin's own).
   Under **Servers**, double-click *PostgreSQL 15* and enter the **`postgres` password
   you set during install**. *(If no server is listed: right-click **Servers → Register
   → Server**; Connection tab → Host `localhost`, Port `5432`, Username `postgres`, that
   password.)*
2. Right-click **Databases → Create → Database**, name it **`drdo_gis`**.
3. Right-click **Login/Group Roles → Create**; name **`drdo_user`**; *Definition* tab
   password **`drdo_secret`**; *Privileges* tab **Can login? = Yes**.
4. Click the **`drdo_gis`** database, open the **Query Tool** (▶_ icon), and run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   CREATE EXTENSION IF NOT EXISTS postgis_topology;
   GRANT ALL ON SCHEMA public TO drdo_user;
   GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
   ```

**Do not create any tables** — the app builds them itself on first start.

> If the PostGIS line fails with *"could not open extension control file … postgis"*,
> PostGIS isn't installed — add it (Section 2) and run that line again. This is the one
> thing that can genuinely block you.

### Confirm it worked
Test the **exact login the app uses** — over the network, as `drdo_user`, with the
password. In pgAdmin, open a **Query Tool connected as `drdo_user` on `drdo_gis`** and run:
```sql
SELECT 1;
```
*(Or from Command Prompt, if `psql` is on your PATH — it's in
`C:\Program Files\PostgreSQL\15\bin`:*
```bat
set PGPASSWORD=drdo_secret
psql -h localhost -U drdo_user -d drdo_gis -c "SELECT 1;"
```
*)* If it returns `1`, **the app will connect too.**

---

## 4. Tell the app how to connect — and confirm it worked

The app reads its connection settings from one plain text file:
**`deploy\manual\application.properties`**. Open it in **Notepad** (or any editor). It
already contains:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/drdo_gis
spring.datasource.username=drdo_user
spring.datasource.password=drdo_secret
```

Each part comes straight from what you did in Section 3 — this table is the whole
mapping:

| In the file | What it is | Where it comes from |
|---|---|---|
| `localhost` | the machine PostgreSQL runs on | this same machine → `localhost` |
| `5432` | the **port** PostgreSQL listens on | the default (shown in pgAdmin under *Properties → Connection*) |
| `drdo_gis` | the **database name** | the `CREATE DATABASE` in Section 3 |
| `username` = `drdo_user` | the **login role** | the `CREATE ROLE` in Section 3 |
| `password` = `drdo_secret` | that role's **password** | the `PASSWORD '…'` you set on that role in Section 3 |

**If you used the exact names above, change nothing** — the defaults already match.
Only edit this file if your database name, user, password, host, or port are different.
The file is read fresh at every start, so you never rebuild anything to change it.

### How to know the connection actually worked

You don't test the connection separately — **you just start the app (Section 5) and
watch its window.** One of two things happens:

- ✅ **It connected:** you'll see lines like `HikariPool-1 - Start completed`, then
  Liquibase creating tables, then **`Started GisDeploymentApplication`**. Open
  `http://localhost:8080/api/actuator/health` → `{"status":"UP"}`. That means the app
  reached the database, logged in, and built its tables. Done.
- ❌ **It couldn't connect:** the window prints a clear reason and stops. The three
  common ones and their exact fix are in **Section 9 → Database** — e.g.
  *Connection refused* = wrong host/port (or PostgreSQL not running),
  *password authentication failed* = wrong user/password in this file,
  *permission denied to create extension* = you skipped step 3.4.

In other words: **the app starting up with no database error IS the proof the
connection is correct.** There's nothing else to wire up.

---

## 5. Run it

**Double-click `deploy\manual\run.bat`.** A console window opens.

Wait for the line **`Started GisDeploymentApplication`**, then open
**http://localhost:8080** in a browser. The **same** jar serves the web UI (at `/`) and
the API (at `/api/v1`) on port 8080. On first run it creates all tables in `drdo_gis`.
Leave the window open; stop it by closing the window or pressing `Ctrl+C`.

> Plain Java without the script, from a Command Prompt in the repo root:
> ```bat
> java -jar deploy\manual\backend.jar ^
>   --spring.config.additional-location="optional:file:deploy\manual\application.properties" ^
>   --gis.terrain.dted-base-path="%CD%\data\terrain\dted" ^
>   --gis.terrain.geotiff-base-path="%CD%\data\terrain\geotiff"
> ```

To reach it from another PC on the LAN: browse to `http://<server-ip>:8080` (the jar
already listens on all interfaces; open port 8080 in Windows Firewall).

---

## 6. Verify it works

In a Command Prompt (Windows 10+ has `curl` built in):
```bat
curl http://localhost:8080/api/actuator/health
curl http://localhost:8080/api/v1/tiles/geotiff
curl http://localhost:8080/api/v1/deployments
```
Expected: `{"status":"UP"}`, then a list containing `india_basemap.tif`, then `[]` (or
your deployments). In the run window's log, look for:
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
| 4 | Set Frontage `2`, Depth `1` (in **km**), click **COMPUTE GEOMETRY** | Map pans to the point and draws a polygon; detail panel shows Geometry Type + Terrain Analysis. |
| 5 | Look at **Terrain Analysis** | Suitability %, PLANAR/NON-PLANAR, mean/max slope, elevation, samples. |
| 6 | On a **NON-PLANAR** deployment: **✎ EDIT GEOMETRY**, drag a control point, **✔ SAVE EDITS** | Geometry reshapes and persists. |
| 7 | Select a deployment in the left list | Map pans to it, shows its geometry + details. |
| 8 | Click **DELETE** | It disappears from the list. |
| 9 | Restart the app | Remaining deployments are still there (persisted in PostgreSQL). |

**Valid input ranges:** lat −90…90, lon −180…180, slope 0–90°, heading 0–360°.
**Frontage and depth have no upper limit** — enter any positive size (a deployment can
be tens of kilometres across). The terrain analysis is **adaptive**: it samples the
whole footprint at roughly the terrain resolution (~30 m) no matter how large the area,
so big deployments are analysed just as accurately as small ones (a 25 km × 12 km
deployment samples ~200,000 points; a 200 m × 100 m one samples ~80). Out-of-range
lat/lon/slope/heading, or a zero/negative frontage/depth, disable Compute or return HTTP 400.

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

`run.bat` points the app at this project's `data\terrain\` automatically. To use DRDO's
own folders, either edit the `gis.terrain.*` lines in `application.properties`, or append
the path when starting:
```bat
run.bat --gis.terrain.dted-base-path=C:/drdo/dted
```
*(Use forward slashes in the path, even on Windows.)*

**DTED** (`.dt0/.dt1/.dt2`) — **layout and file-name casing don't matter**; each file
records the 1° cell it covers in its own header, so the reader finds the right tile
regardless of naming (`E077\N28.DT1`, `n28\e077.dt1`, or one flat folder all work). A
location with no covering tile is treated as flat (elevation 0) with a logged warning, so
a partial DTED set still works for its area.

**GeoTIFF base map** — `data\terrain\geotiff\india_basemap.tif` ships and is
auto-discovered on startup. Replace it with DRDO's aerial/satellite `.tif` for production;
it should be **Web-Mercator (EPSG:3857)** so it lines up in the browser. Set
`gis.terrain.default-base-map=<their-file>.tif` and restart. Reproject others once on a
connected machine: `gdalwarp -t_srs EPSG:3857 -of COG input.tif output.tif`.

---

## 9. Troubleshooting — every error and its fix

### Starting the app
| Symptom | Cause → Fix |
|---------|-------------|
| `'java' is not recognized` | Java not installed / on PATH → install Java 17+ (Temurin) with *Add to PATH*, reopen the Command Prompt. |
| `UnsupportedClassVersionError` | Java too old → needs **Java 17 or newer**. |
| `run.bat` prints "'java' was not found" | Same — install Java 17+ so `java -version` works. |
| `no main manifest attribute` / jar won't run | `backend.jar` is truncated (partial copy/clone) → re-copy it; it should be ~82 MB. |
| **`Web server failed to start. Port 8080 was already in use`** (window closes) | Something is already on 8080 — **usually an earlier copy of this app still running.** See "Port 8080 already in use" just below. |
| Port 8080 already in use — how to fix | **First check what's on it:** `netstat -ano \| findstr :8080` → the last number is the PID → `taskkill /F /PID <pid>` (or `taskkill /F /IM java.exe` to end all Java). Often it's a previous run of this app — killing it, then re-running, is all you need (keep using 8080). **To change the port instead:** the port is **not** baked in the jar — change the `server.port=8080` line in `deploy\manual\application.properties` to your port (e.g. `server.port=8081`), **or** run `deploy\manual\run.bat --server.port=8081`. Then open `http://localhost:8081`. |
| Already have it running? | If a previous window is still open and reached `Started`, the app is already up — just open **http://localhost:8080** in the browser; don't start a second copy. |
| Nothing at http://localhost:8080 | Wrong port (it's **8080**, not 4200), or the log hasn't reached `Started` yet — wait for that line. |

### Database
| Symptom | Cause → Fix |
|---------|-------------|
| PostgreSQL not running | Open *Services* (`services.msc`), Start *postgresql-x64-15*. Confirm the service status is *Running*. |
| pgAdmin won't connect / no server listed | Register it: right-click *Servers → Register → Server*; Host `localhost`, Port `5432`, user `postgres`, the password set during install. |
| `psql` is not recognized | It's not on PATH — use pgAdmin's Query Tool, or the full path `"C:\Program Files\PostgreSQL\15\bin\psql.exe"`. |
| App: `password authentication failed for user "drdo_user"` | The role's password ≠ the one in `application.properties`. Reset it in a pgAdmin Query Tool: `ALTER ROLE drdo_user PASSWORD 'drdo_secret';` |
| App: `Connection refused` to `localhost:5432` | PostgreSQL not running, or a different port — check the *postgresql* service is Running and match host/port in `application.properties`. |
| App: `database "drdo_gis" does not exist` | Run the `CREATE DATABASE` step in §3. |
| `could not open extension control file … postgis` | PostGIS not installed → install it (Section 2), then re-run the PostGIS line in §3. |
| Forgot the `postgres` password | It was set at install time; recoverable only by temporarily editing `pg_hba.conf` to `trust` (ask your PostgreSQL admin). |
| `Could not acquire change log lock` | A previous run crashed mid-migration → run `DELETE FROM databasechangeloglock;` on `drdo_gis` (a pgAdmin Query Tool), then restart. |
| Want a clean/empty database | Drop & recreate: `DROP DATABASE drdo_gis;` then re-run §3 (⚠ deletes all saved deployments). |

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
| "Validation failed" / HTTP 400 on create | A value is out of range — lat/lon/slope/heading (see Section 7), or a frontage/depth that isn't a positive number. Correct the input. |
| **COMPUTE GEOMETRY** stays greyed out | Latitude/Longitude empty or out of range → enter valid coordinates (type or click the map). |
| **EDIT GEOMETRY** button missing | The deployment is an **ELLIPSE** (planar) — only adaptive Bézier shapes are editable. By design. |
| Geometry shows **INVALID** | Rare; the polygon couldn't be repaired → note the coordinates/params and report. |

---

## 10. Rebuilding from source (optional — only with internet or an internal mirror)

You do **not** need this to run at DRDO. It's only for changing the code.

- **Backend + bundled UI:** `cd backend && mvn clean package` → copy
  `target\gis-deployment-system-1.0.0-SNAPSHOT.jar` over `deploy\manual\backend.jar`. The
  Angular UI is committed under `backend\src\main\resources\static\`, so the jar already
  contains the web UI — **Node is not needed to rebuild the backend**. Maven needs a
  network or a populated `.m2`; GeoTools comes from the OSGeo repository in
  `backend\pom.xml` (not Maven Central), so a mirror must include it. Build with Java 17.
- **Changing the UI:** `cd frontend && npm install && npm run build`, copy
  `dist\drdo-gis-frontend\*` into `backend\src\main\resources\static\`, then rebuild the
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
| Start the app | double-click `deploy\manual\run.bat` |
| Config file | `deploy\manual\application.properties` (DB settings + `server.port`) |
| Change the web port | edit `server.port=8080` in `application.properties` (not baked into the jar) |
| Needs on machine | Java 17+ and PostgreSQL+PostGIS — nothing else |
| Database admin | pgAdmin |
| DTED: any layout? | Yes — files are matched by their header, not their name. |
| Base map must be | EPSG:3857 GeoTIFF |
| Set location | Type Lat/Lon **or** click the map |

Everything runs fully offline — no internet, CDN, or external service is used at runtime.
