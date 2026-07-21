# DRDO GIS — Offline Setup Guide (on-site, no internet)

This sets up the whole system on an air-gapped machine using only **Java** and
**PostgreSQL** — no Docker, no Python, no Node, no Maven at runtime.

The application ships **prebuilt** as a single jar (`deploy/manual/backend.jar`)
that contains both the REST API and the Angular web UI. You run that one jar; it
serves everything on **http://localhost:8080**.

---

## 0. What you carry in

Copy this **entire project folder** to the DRDO machine (GitHub clone or internal
transfer — the jar is committed in the repo, so a clone brings it too). What matters:

| Path | What it is |
|------|-----------|
| `deploy/manual/backend.jar` | The whole app (API + web UI) — run with Java. |
| `deploy/manual/run.bat` / `run.sh` | Start scripts (double-click `run.bat` on Windows). |
| `deploy/manual/application.properties` | Editable DB settings (no rebuild needed). |
| `data/terrain/geotiff/india_basemap.tif` | Real India base map (swap for DRDO's). |
| `data/terrain/dted/` | Where DTED tiles go (or point config at DRDO's folder). |

You do **not** need `~/.m2`, `node_modules`, Maven, or Node on this machine — those
are only for *rebuilding* the jar (Section 7), which DRDO does not need to do.

---

## 1. The only two prerequisites

| Tool | Why | Check |
|------|-----|-------|
| **Java 17+** (JDK or JRE) | Runs the jar | `java -version` |
| **PostgreSQL + PostGIS** | The database (you already use pgAdmin) | pgAdmin connects |

That's it. If both are present, skip to Section 2. If the machine has *nothing*
installed, install these two from installers you carried in:

- **Java** — Eclipse Temurin 17 (Windows `.msi`, Linux `.tar.gz`, macOS `.pkg`).
- **PostgreSQL 15 + PostGIS 3.3** — Windows: the EDB PostgreSQL installer, then the
  **PostGIS bundle** for that PG version (grab the bundle file while you still have
  internet — the StackBuilder step needs a network). Linux: the `postgresql-15` and
  `postgresql-15-postgis-3` packages **plus their dependencies**.

> **PostGIS is required.** The app stores real geometry and runs spatial queries, so
> the `postgis` extension must be available for your PostgreSQL. This is the one
> piece that can't be worked around — make sure the PostGIS package is installed
> before Section 2.

---

## 2. Database — set up in pgAdmin (you do NOT need psql)

You already have pgAdmin and it connects to PostgreSQL, so the server is running
(usually on port **5432** — see *right-click the server → Properties → Connection →
Port*). Everything below is done in pgAdmin's **Query Tool** — the `psql` command
line is **not required** at any point.

**2.1** Create the database: right-click *Databases → Create → Database*, name it
**`drdo_gis`**.

**2.2** Create the login role: right-click *Login/Group Roles → Create → Login/Group
Role*, name **`drdo_user`**; on *Definition* set password **`drdo_secret`**; on
*Privileges* turn on *Can login?*.

**2.3** Open the **Query Tool on `drdo_gis`** (click the `drdo_gis` database, then the
Query Tool button) and run the following **as the `postgres` superuser** — creating an
extension needs superuser rights, so do it once here and the app never has to:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
GRANT ALL ON SCHEMA public TO drdo_user;
GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
```

That's it — **do not create any tables**. The app creates them automatically on
first start (via Liquibase). Because you already enabled PostGIS here as superuser,
the app's own `CREATE EXTENSION IF NOT EXISTS` step later is a harmless no-op even
though `drdo_user` is not a superuser.

> **"Is the database connected to the terminal / running on a port?"** — It runs as a
> background service and listens on a TCP port (5432 by default). The app connects to
> that port over JDBC using the settings in `application.properties`; it does not go
> through a terminal or through psql. If pgAdmin connects, the app will connect with
> the same host/port/user/password.

> **If you *want* psql in a terminal (optional):** psql ships inside PostgreSQL's
> `bin` folder but is often not on PATH — that's why `psql` "isn't found". Full-path
> examples: Windows `"C:\Program Files\PostgreSQL\15\bin\psql.exe"`, Linux
> `/usr/bin/psql`. Then `psql -h localhost -p 5432 -U drdo_user -d drdo_gis`. But the
> pgAdmin Query Tool does everything psql would, so you can skip it entirely.

---

## 3. Configure the app — `deploy/manual/application.properties`

The start scripts read `deploy/manual/application.properties` every launch, so you
change settings **without rebuilding anything**. The defaults match Section 2:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/drdo_gis
spring.datasource.username=drdo_user
spring.datasource.password=drdo_secret
```
If you created the database with a different name/user/password or on a different
host/port, edit these three lines and save. Terrain paths default to this project's
`data/` folder automatically (Section 5 to change them).

---

## 4. Run the app

- **Windows:** double-click **`deploy\manual\run.bat`**, or in a terminal:
  ```bat
  deploy\manual\run.bat
  ```
- **Linux / macOS:**
  ```bash
  ./deploy/manual/run.sh
  ```

Wait for **`Started GisDeploymentApplication`**, then open **http://localhost:8080**.
The one jar serves both the web UI (at `/`) and the API (at `/api/v1`). On first run
it creates all tables in `drdo_gis`. Leave the window open; stop with `Ctrl+C` (or
close the window).

> Prefer plain Java without the script? From the repo root:
> ```bash
> java -jar deploy/manual/backend.jar \
>   --spring.config.additional-location=optional:file:deploy/manual/application.properties \
>   --gis.terrain.dted-base-path="$PWD/data/terrain/dted" \
>   --gis.terrain.geotiff-base-path="$PWD/data/terrain/geotiff"
> ```
> (The scripts just do this for you and figure out the paths.)

---

## 5. Using DRDO's terrain data

The scripts point the app at this project's `data/terrain/` by default. To use
DRDO's own folders, either uncomment the `gis.terrain.*` lines in
`application.properties`, or append them when starting:
```bash
./deploy/manual/run.sh --gis.terrain.dted-base-path=/mnt/drdo/dted
```
```bat
run.bat --gis.terrain.dted-base-path=C:/drdo/dted
```
Copy DRDO's `.dt1` tiles into the DTED folder (**layout and file-name casing don't
matter** — each DTED file records the 1° cell it covers in its own header, so the
reader finds the right tile regardless of naming). A location with no covering tile
is treated as elevation 0 (flat) with a logged warning, so a partial set still works.

---

## 6. Verify it works

```bash
# app healthy
curl http://localhost:8080/api/actuator/health          # -> {"status":"UP"}
# base map registered
curl http://localhost:8080/api/v1/tiles/geotiff          # -> lists india_basemap.tif
```
Then in the browser (**http://localhost:8080**): the India map should appear, and
clicking the map (or typing Lat/Lon) → **Compute Geometry** should draw a deployment
polygon.

---

## 7. Swapping the base map

1. Put DRDO's base map (`.tif`) in `data/terrain/geotiff/`.
2. Set `gis.terrain.default-base-map=<their-file>.tif` in `application.properties`.
3. Restart the app.

> The GeoTIFF should be **EPSG:3857** so it lines up in the browser. The shipped
> `india_basemap.tif` already is. Reproject others once on a connected machine:
> `gdalwarp -t_srs EPSG:3857 -of COG input.tif output.tif`.

---

## 8. Rebuilding the jar (only if you change the code — needs a network)

DRDO does not need this. If you modify the source:

- **Backend + bundled UI:** `cd backend && mvn clean package` → copy
  `target/gis-deployment-system-1.0.0-SNAPSHOT.jar` over `deploy/manual/backend.jar`.
  The Angular UI is committed in `backend/src/main/resources/static/`, so the jar
  already includes the web UI — **Node is not needed to rebuild the backend**. Maven
  needs a network or a populated `~/.m2`; GeoTools comes from the OSGeo repository in
  `backend/pom.xml` (not Maven Central), so a mirror must include it.
- **Changing the UI:** `cd frontend && npm install && npm run build`, copy
  `dist/drdo-gis-frontend/*` into `backend/src/main/resources/static/`, then rebuild
  the backend.

---

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `java: command not found` / `UnsupportedClassVersionError` | Java 17+ not installed or not on PATH. |
| App fails: `Connection refused` to 5432 | PostgreSQL isn't running, or wrong host/port in `application.properties`. Confirm pgAdmin can connect, and match its host/port. |
| App fails: `password authentication failed` | `username`/`password` in `application.properties` don't match the pgAdmin role. |
| App fails: `could not open extension control file ... postgis` | PostGIS not installed for this PostgreSQL — install the PostGIS package, then re-run the `CREATE EXTENSION` in Section 2.3. |
| App fails: `permission denied to create extension "postgis"` | You skipped Section 2.3 — enable PostGIS once **as the `postgres` superuser** in pgAdmin, then start the app. |
| `Could not acquire change log lock` | A previous run crashed mid-migration → in pgAdmin Query Tool on `drdo_gis`: `DELETE FROM databasechangeloglock;` then restart. |
| Browser page is blank / map missing | Make sure you opened **http://localhost:8080** (not 4200) and the window shows `Started`. Hard-refresh (Ctrl+Shift+R). |
| Map is only a grid, no imagery | No readable GeoTIFF found, or `default-base-map` name doesn't match a file, or the `.tif` isn't EPSG:3857. |
| Everywhere is flat (elevation 0, always ELLIPSE) | DTED not found — the log shows `Indexed 0 DTED tile(s)`. Fix the DTED path (Section 5), restart. |

Everything runs fully offline — no internet, CDN, or external services are used at
runtime.
