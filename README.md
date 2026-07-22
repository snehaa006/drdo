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
| `deploy/manual/backend.jar` | **The whole app** (API + web UI) in one file — run with Java. |
| `deploy/manual/run.bat` / `run.sh` | Start scripts — double-click `run.bat` on Windows. |
| `deploy/manual/application.properties` | Editable DB settings (no rebuild needed). |
| `data/terrain/geotiff/india_basemap.tif` | India base map (EPSG:3857) — replace with DRDO's. |
| `data/terrain/dted/` | Sample DTED tiles — replace with DRDO's `.dt1` files. |
| `backend/` , `frontend/` | Source — only needed to *rebuild* the jar (Section 10). |

---

## 2. Prerequisites — check what's there, install what's missing

Three things must be present on the machine. **This section assumes nothing is
installed** — first check each one, then install only what's missing. (Nothing else
is needed: no Docker, Python, Node, or Maven.)

| # | What | Why | How to check it's present | If the check fails, it's missing |
|---|------|-----|---------------------------|----------------------------------|
| 1 | **Java 17+** | Runs the app | Open a terminal / Command Prompt and type `java -version` | *"'java' is not recognized"* / *command not found* |
| 2 | **PostgreSQL 15+** | The database engine (the service on port 5432) | Windows: open **Services**, look for a running *postgresql-x64-…* service. Any OS: if pgAdmin (below) can open the server, it's installed and running | No such service, or pgAdmin can't connect |
| 3 | **pgAdmin 4** | The tool you use to manage the database | Look for **pgAdmin 4** in the Start menu / Applications | Not in the app list |
| 3b | **PostGIS** (an add-on *inside* PostgreSQL) | Required — the app stores geometry | In pgAdmin's Query Tool run `SELECT postgis_version();` | Error *"could not open extension control file … postgis"* → not installed |

> On Windows, the **EDB PostgreSQL installer installs #2 *and* #3 together** (pgAdmin
> ships inside it), and its **StackBuilder** step adds #3b (PostGIS). So one installer
> usually covers PostgreSQL + pgAdmin + PostGIS.

### Installing what's missing (offline — carry the installers in)

Download these **while you still have internet**, copy them to the USB alongside this
project, and run them on the DRDO machine. Pick the ones for the target OS.

| Missing piece | Windows | Linux |
|---|---|---|
| **Java 17** | Eclipse **Temurin 17** `.msi` (adoptium.net) — run it, tick *"Add to PATH"* | Temurin 17 `.tar.gz`, or `apt/dnf install` the `openjdk-17-jre` package **+ its dependencies** |
| **PostgreSQL + pgAdmin** | **EDB PostgreSQL 15** installer `.exe` (enterprisedb.com) — installs the server **and** pgAdmin 4. During install it asks you to **set a password for the `postgres` superuser — write it down, you need it to open pgAdmin** | `postgresql-15` package **+ deps**; install **pgAdmin 4** separately (`pgadmin4` package or the desktop build) |
| **PostGIS** | In the EDB installer's **StackBuilder** pick *Spatial Extensions → PostGIS* (needs a network on that machine), **or** carry the standalone **PostGIS bundle** `.exe` for PG 15 and run it | `postgresql-15-postgis-3` package **+ deps** |

> **The `postgres` password you set while installing PostgreSQL is the important one** —
> it's what you'll type to open the server in pgAdmin (Section 3.0). It is *not* the
> same as the `drdo_user` password you'll create later.

Once #1–#3b are present, continue to Section 3.

---

## 3. Set up the database in pgAdmin

### First, the mental model (this clears up the usual confusion)

- **PostgreSQL is a background service.** It's always running and listening on a
  network **port** (almost always **5432**). It is *not* something you "start in a
  terminal."
- **The app connects to that port over the network** (a protocol called JDBC) — the
  **exact same way pgAdmin connects to it.** So: **if pgAdmin can open the server, the
  app can reach it too.** There is nothing extra to "connect."
- **You do NOT need `psql`.** `psql` is a separate command-line program that is usually
  not on the PATH, which is why typing `psql` "does nothing" or says *command not
  found*. **Ignore it completely.** Everything below is done in pgAdmin's **Query
  Tool** (a window inside pgAdmin where you type SQL and press ▶ Run).

So the database work is just: create a database, create a login, turn on PostGIS —
all clicks and SQL inside pgAdmin. Then you write those same names into one text file
so the app knows where to connect.

### The steps

**3.0 — Open pgAdmin and connect to the server.** *(This is the step people get stuck
on — "I made the database but couldn't connect.")*

1. Launch **pgAdmin 4**. The first time, it asks you to set a **master password** —
   this is pgAdmin's own password (make one up, remember it). It is *not* the database
   password.
2. Look at the left tree under **Servers**:
   - If a server (e.g. *"PostgreSQL 15"*) is already listed, **double-click it** and
     enter the **`postgres` password you set when installing PostgreSQL** (Section 2).
   - If **no server is listed**, add one: right-click **Servers → Register → Server**.
     On the **General** tab give it any name (e.g. `Local`). On the **Connection** tab
     enter: **Host** `localhost`, **Port** `5432`, **Maintenance database** `postgres`,
     **Username** `postgres`, **Password** = the `postgres` password from Section 2.
     Click **Save**.
3. The tree should now expand to show **Databases**, **Login/Group Roles**, etc. If it
   does, **pgAdmin is connected to the server** and you can do the rest. If it errors,
   see Section 9 → Database (usually a wrong password or PostgreSQL not running).

**3.1 — Create the database.** In pgAdmin's left tree, right-click *Databases → Create
→ Database*. Name it **`drdo_gis`**. Save.

**3.2 — Create the login role.** Right-click *Login/Group Roles → Create → Login/Group
Role*. Name it **`drdo_user`**. On the **Definition** tab, set the password to
**`drdo_secret`**. On the **Privileges** tab, turn **Can login?** on. Save.

**3.3 — Turn on PostGIS.** Click the **`drdo_gis`** database once to select it, then
open the **Query Tool** (the ▶_ icon, or right-click → *Query Tool*). Make sure you are
connected as the **`postgres`** superuser (the account pgAdmin normally uses). Paste
this and press **▶ Run**:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
GRANT ALL ON SCHEMA public TO drdo_user;
GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
```
If it runs without an error, the database side is **done**. **Do not create any
tables** — the app builds them itself on first start.

> If line 1 fails with *"could not open extension control file … postgis"*, PostGIS
> is not installed on this PostgreSQL — install the PostGIS package/bundle (Section 2)
> and run 3.3 again. This is the one thing that can genuinely block you.

---

## 4. Tell the app how to connect — and confirm it worked

The app reads its connection settings from one plain text file:
**`deploy/manual/application.properties`**. Open it in any editor. It already contains:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/drdo_gis
spring.datasource.username=drdo_user
spring.datasource.password=drdo_secret
```

Each part comes straight from what you did in Section 3 — this table is the whole
mapping:

| In the file | What it is | Where it comes from |
|---|---|---|
| `localhost` | the machine PostgreSQL runs on | almost always this machine → `localhost` (in pgAdmin: *server → Properties → Connection → Host*) |
| `5432` | the **port** PostgreSQL listens on | pgAdmin: *server → Properties → Connection → Port* (usually 5432) |
| `drdo_gis` | the **database name** | the database you made in 3.1 |
| `username` | the **login role** | the role you made in 3.2 (`drdo_user`) |
| `password` | that role's **password** | what you set in 3.2 (`drdo_secret`) |

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
  *permission denied to create extension* = you skipped step 3.3.

In other words: **the app starting up with no database error IS the proof the
connection is correct.** There's nothing else to wire up.

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
| **pgAdmin itself won't connect / no server listed** | Register the server (§3.0) with Host `localhost`, Port `5432`, user `postgres`. If it still fails, PostgreSQL isn't running — Windows: open **Services** and Start the *postgresql-x64-…* service. |
| **Forgot the `postgres` password** | It was set when PostgreSQL was installed. If lost, it can be reset by editing `pg_hba.conf` to `trust` temporarily (ask your PostgreSQL admin) — there's no way to recover the original. |
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
| "Validation failed" / HTTP 400 on create | A value is out of range — lat/lon/slope/heading (see Section 7), or a frontage/depth that isn't a positive number. Correct the input. |
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
