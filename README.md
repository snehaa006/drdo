# DRDO GIS — Offline Guide

Terrain-aware deployment-geometry system that runs **fully offline** on the DRDO
machine. This is the **only** document you need — setup, running, configuration,
testing, and every error and its fix.

- **Backend + UI** — one Java jar (Spring Boot 3, GeoTools, JTS, PostGIS + Angular 15)
- **Database** — PostgreSQL with PostGIS (set up from the terminal with `sudo -u postgres psql`, or pgAdmin if you prefer a GUI)

**No Docker. No Python. No Node. No Maven. No internet.** The app ships prebuilt as a
single jar (`deploy/manual/backend.jar`, committed in this repo), so on the target
machine you only need **Java** and **PostgreSQL**. The jar serves both the web UI and
the API on **http://localhost:8080**.

---

## Contents

1. [What's in this folder](#1-whats-in-this-folder)
2. [Prerequisites — check & install (Linux)](#2-prerequisites--check-whats-there-install-whats-missing-linux)
3. [Set up the database (Linux)](#3-set-up-the-database-linux)
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

## 2. Prerequisites — check what's there, install what's missing (Linux)

The machine is **Linux** (you're on it via remote desktop). Only three things must be
present. Open a terminal, check each, and install only what's missing. Commands are
shown for **Debian/Ubuntu (`apt`)** and **RHEL/CentOS/Rocky (`dnf`)** — use whichever
your box is. (Nothing else is needed — no Docker, Python, Node, or Maven.)

| # | What | Check it's present (run in a terminal) | Install if missing |
|---|------|----------------------------------------|--------------------|
| 1 | **Java 17+** | `java -version` → shows 17 or higher | `sudo apt install openjdk-17-jre` · or `sudo dnf install java-17-openjdk` |
| 2 | **PostgreSQL 15+**, running | `pg_isready` → *accepting connections* (or `systemctl status postgresql`) | `sudo apt install postgresql` · or `sudo dnf install postgresql-server && sudo postgresql-setup --initdb` — then **start it:** `sudo systemctl enable --now postgresql` |
| 3 | **PostGIS** (add-on inside PostgreSQL) | `sudo -u postgres psql -c "SELECT name FROM pg_available_extensions WHERE name='postgis';"` → lists `postgis` | `sudo apt install postgresql-15-postgis-3` · or `sudo dnf install postgis` |

**pgAdmin is optional on Linux** — everything below is done from the terminal with
`sudo -u postgres psql`. Install it only if you want the GUI
(`sudo apt install pgadmin4-desktop`).

> **Offline install (air-gapped):** on-site `apt`/`dnf` can't download. On a **connected
> machine of the same OS + version**, fetch each package **with its dependencies** as
> files — Debian/Ubuntu: `apt-get download <pkg>` (or the `apt-offline` tool); RHEL:
> `dnf download --resolve <pkg>` — carry them on the USB, then install on-site with
> `sudo dpkg -i *.deb` or `sudo dnf install ./*.rpm`. Java can instead be the **Temurin
> 17 `.tar.gz`** (adoptium.net): unpack it and add its `bin/` to `PATH`.

Once #1–#3 are present, continue to Section 3.

---

## 3. Set up the database (Linux)

### The mental model — this clears up the `psql` confusion
- PostgreSQL is a **service** listening on port **5432** (check with `pg_isready`). It
  is not a program you keep open in a terminal.
- The app connects to that port over the network (JDBC), the same way any client does.
- **To run SQL as the database administrator on Linux, use `sudo -u postgres psql`.**
  That logs you in as PostgreSQL's built-in `postgres` superuser with **no password**,
  through local "peer" authentication. Plain `psql` on its own fails because it tries
  to log in as *your Linux username*, which has no database role — **that is exactly
  why your earlier `psql` commands "didn't work."**

### The setup — copy-paste these into a terminal
```bash
# 1. Create the database and the login role the app will use
sudo -u postgres psql -c "CREATE DATABASE drdo_gis;"
sudo -u postgres psql -c "CREATE ROLE drdo_user LOGIN PASSWORD 'drdo_secret';"

# 2. Enable PostGIS + grant access — note the '-d drdo_gis' (must be done INSIDE that database)
sudo -u postgres psql -d drdo_gis -c "CREATE EXTENSION IF NOT EXISTS postgis;"
sudo -u postgres psql -d drdo_gis -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"
sudo -u postgres psql -d drdo_gis -c "GRANT ALL ON SCHEMA public TO drdo_user;"
sudo -u postgres psql -d drdo_gis -c "GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;"
```
If these run without errors, the database is ready — **do not create any tables**, the
app builds them itself on first start.

> If step 2 fails with *"could not open extension control file … postgis"*, PostGIS is
> not installed — install it (Section 2, item 3) and re-run step 2. This is the one
> thing that can genuinely block you.

### Confirm it works — before you even start the app
```bash
# Is PostGIS enabled in drdo_gis?
sudo -u postgres psql -d drdo_gis -c "SELECT postgis_version();"           # prints a version

# Does the app's exact login work? (over TCP, as drdo_user, with the password)
PGPASSWORD=drdo_secret psql -h localhost -U drdo_user -d drdo_gis -c "SELECT 1;"
```
The **second command connects the exact way the app will.** If it prints a `1`, the app
will connect too. If it fails with an *authentication* error, that's a one-line
`pg_hba.conf` fix — see **Section 9 → Database**.

### Prefer pgAdmin's GUI instead? (optional)
Open pgAdmin → right-click **Servers → Register → Server**; on the **Connection** tab
set Host `localhost`, Port `5432`, Username `postgres`. Then use its **Query Tool** to
run the same SQL. On Linux the terminal commands above are simpler and need no extra
setup, so pgAdmin is entirely optional.

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
| `localhost` | the machine PostgreSQL runs on | this same machine → `localhost` |
| `5432` | the **port** PostgreSQL listens on | the default (confirm with `pg_isready`, which reports the port) |
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
  *permission denied to create extension* = you skipped step 3.3.

In other words: **the app starting up with no database error IS the proof the
connection is correct.** There's nothing else to wire up.

---

## 5. Run it

In a terminal on the Linux machine, from the project folder:
```bash
./deploy/manual/run.sh
```
(If it says permission denied: `chmod +x deploy/manual/run.sh` once, then re-run.
On Windows the equivalent is double-clicking `deploy\manual\run.bat`.)

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

### Database (Linux)
| Symptom | Cause → Fix |
|---------|-------------|
| `pg_isready` says *no response* / PostgreSQL not running | Start it: `sudo systemctl enable --now postgresql` (RHEL first-time also needs `sudo postgresql-setup --initdb`). |
| `psql: command not found` | The client isn't installed → `sudo apt install postgresql-client` (or `dnf install postgresql`). But use **`sudo -u postgres psql`** to run admin SQL (§3). |
| `psql` runs but says *role "yourname" does not exist* | You ran plain `psql`, which logs in as your Linux user. Use **`sudo -u postgres psql`** instead. |
| App: `password authentication failed for user "drdo_user"` | The password in `application.properties` doesn't match the role. Re-set it: `sudo -u postgres psql -c "ALTER ROLE drdo_user PASSWORD 'drdo_secret';"` |
| App: `no pg_hba.conf entry for host …` **or** `Ident/peer authentication failed` for `drdo_user` | PostgreSQL isn't allowing password login over TCP. Find the file: `sudo -u postgres psql -tc "SHOW hba_file;"`, edit it, ensure this line exists near the IPv4 section, then reload: <br>`host  all  all  127.0.0.1/32  scram-sha-256` <br>`sudo systemctl reload postgresql` <br>(Test with the `PGPASSWORD=… psql -h localhost …` command in §3.) |
| App: `Connection refused` to `localhost:5432` | PostgreSQL not running (`sudo systemctl start postgresql`) or on a different port — check `pg_isready`. |
| App: `database "drdo_gis" does not exist` | Run the `CREATE DATABASE` in §3. |
| `could not open extension control file … postgis` | PostGIS not installed → install it (Section 2, item 3), then re-run §3 step 2. |
| `Could not acquire change log lock` | A previous run crashed mid-migration → `sudo -u postgres psql -d drdo_gis -c "DELETE FROM databasechangeloglock;"` then restart the app. |
| Want a clean/empty database | `sudo -u postgres psql -c "DROP DATABASE drdo_gis;"` then re-run all of §3 (⚠ deletes all saved deployments). |

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
| Start the app | `./deploy/manual/run.sh` (Linux) · `deploy\manual\run.bat` (Windows) |
| Config file | `deploy/manual/application.properties` (DB settings) |
| Needs on machine | Java 17+ and PostgreSQL+PostGIS — nothing else |
| Database admin | `sudo -u postgres psql` (or pgAdmin if you installed it) |
| DTED: any layout? | Yes — files are matched by their header, not their name. |
| Base map must be | EPSG:3857 GeoTIFF |
| Set location | Type Lat/Lon **or** click the map |

Everything runs fully offline — no internet, CDN, or external service is used at runtime.
