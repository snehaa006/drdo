# DRDO GIS — Offline Setup Guide (on-site, no internet)

This sets up the whole system on an air-gapped machine using only **Java**,
**Angular (Node)** and **PostgreSQL** — no Docker, no Python.

There are two things to run: the **backend** (Spring Boot) and the **frontend**
(Angular). Both talk to your **PostgreSQL** database, which you manage in pgAdmin.

---

## 0. What you carry in

Copy this **entire project folder** to the DRDO machine (via GitHub clone or
internal transfer). What matters:

| Path | What it is |
|------|-----------|
| `backend/` | Spring Boot API (build with Maven, run with Java). |
| `frontend/` | Angular 15 app (run with `ng serve`). |
| `data/terrain/geotiff/india_basemap.tif` | Real India base map (swap for DRDO's). |
| `data/terrain/dted/` | Where DTED tiles go (or point config at DRDO's folder). |
| `backend/src/main/resources/application.yml` | Backend config (DB + data paths). |

---

## 1. The one real offline requirement — dependencies

The **code** runs fully offline. The only thing that needs a network is fetching
the third-party **libraries the first time**:

- **Backend (Maven):** needs Spring Boot / GeoTools / etc. in the local Maven
  repo `~/.m2/repository`.
- **Frontend (npm):** needs the Angular packages in `frontend/node_modules`.

On an air-gapped machine you get these one of three ways:

1. **DRDO internal mirror** — point Maven (`~/.m2/settings.xml`) and npm
   (`npm config set registry <internal-url>`) at DRDO's internal repository, then
   run `mvn ...` / `npm install` normally.
   > **Important:** the backend uses **GeoTools**, which is **not on Maven
   > Central** — it comes from the OSGeo repository declared in
   > `backend/pom.xml` (`https://repo.osgeo.org/repository/release/`). When
   > mirroring or pre-fetching, make sure that repository's artifacts are
   > included too, or the backend build fails to resolve `org.geotools:*`.
2. **Pre-populate on a connected machine, then carry over** — on a machine with
   internet run `cd backend && mvn -q dependency:go-offline` and
   `cd frontend && npm install`, then copy the resulting `~/.m2/repository` and
   `frontend/node_modules` folders to the air-gapped machine.
3. **Prebuilt artifacts** — on a connected machine run `mvn clean package` (→ a
   runnable `backend/target/*.jar`) and `ng build` (→ `frontend/dist/`), and carry
   those. The JAR then runs with only Java; the built frontend is static files.

After the libraries are present once, nothing else is downloaded — ever.

---

## 2. Database — set up in pgAdmin (you do NOT need psql)

You already have pgAdmin and it connects to PostgreSQL, so the server is running
(usually on port **5432** — check *right-click the server → Properties →
Connection → Port*). Everything below is done in pgAdmin's **Query Tool** — the
`psql` command line is **not required** at any point.

**2.1** Create the database: right-click *Databases → Create → Database*, name it
**`drdo_gis`**.

**2.2** Create the login role: right-click *Login/Group Roles → Create → Login/
Group Role*, name **`drdo_user`**; on the *Definition* tab set password
**`drdo_secret`**; on the *Privileges* tab turn on *Can login?*.

**2.3** Open the **Query Tool on `drdo_gis`** (click the `drdo_gis` database, then
the Query Tool button) and run:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
GRANT ALL ON SCHEMA public TO drdo_user;
GRANT ALL PRIVILEGES ON DATABASE drdo_gis TO drdo_user;
```

That's it — **do not create any tables**. The backend creates them automatically
on first start (via Liquibase).

> **"Is the database connected to the terminal / running on a port?"** — It runs
> as a background service and listens on a TCP port (5432 by default). The backend
> connects to that port over JDBC using the settings in `application.yml`; it does
> not go through a terminal or through psql. If pgAdmin connects, the backend will
> connect with the same host/port/user/password.

> **If you *want* psql in a terminal (optional):** psql ships inside PostgreSQL's
> `bin` folder but is often not on the PATH — that's why `psql` "isn't found".
> Full path examples: Windows `"C:\Program Files\PostgreSQL\15\bin\psql.exe"`,
> Linux `/usr/bin/psql` or `/usr/pgsql-15/bin/psql`. Then connect with
> `psql -h localhost -p 5432 -U drdo_user -d drdo_gis`. But for this project the
> pgAdmin Query Tool does everything psql would, so you can skip it entirely.

---

## 3. Configure the backend — `application.yml`

Open `backend/src/main/resources/application.yml`. The two things to check:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/drdo_gis   # host:port/dbname (match pgAdmin)
    username: drdo_user
    password: drdo_secret

gis:
  terrain:
    dted-base-path: ../data/terrain/dted             # DRDO's DTED folder
    geotiff-base-path: ../data/terrain/geotiff        # base-map folder
    default-base-map: india_basemap.tif               # which .tif to show
```
If you created the DB with different name/user/password, put those here. Point the
two `*-base-path` values at DRDO's terrain folders if the data lives elsewhere
(absolute paths are fine, e.g. `C:/drdo/terrain/dted`).

---

## 4. Run the backend

From the **`backend`** folder:
```bash
cd backend
mvn spring-boot:run
```
It starts on **http://localhost:8080** (API base `/api`). On first run it creates
all tables in `drdo_gis`. Leave this terminal running.

Prefer a plain Java launch? Build a JAR once and run it (from inside `backend/`):
```bash
mvn clean package
java -jar target/gis-deployment-system-1.0.0-SNAPSHOT.jar
```

---

## 5. Run the frontend (a second terminal)

From the **`frontend`** folder:
```bash
cd frontend
npm install        # first time only (needs node_modules — see Section 1)
npm start          # = ng serve, on port 4200
```
Open **http://localhost:4200** in the browser. The Angular dev server calls the
backend on port 8080 automatically (CORS is allowed for `localhost:4200`).

To stop either process: `Ctrl+C` in its terminal.

---

## 6. Verify it works

```bash
# backend healthy
curl http://localhost:8080/api/actuator/health          # -> {"status":"UP"}
# base map registered (your default should be listed)
curl http://localhost:8080/api/v1/tiles/geotiff
```
Then in the browser (http://localhost:4200): the India map should appear, and
clicking the map → **Compute Geometry** should draw a deployment polygon.

---

## 7. Swapping the base map

1. Put DRDO's base map (`.tif`) in the GeoTIFF folder (`data/terrain/geotiff/`).
2. Set `gis.terrain.default-base-map: <their-file>.tif` in `application.yml`
   (or pass `--gis.terrain.default-base-map=<their-file>.tif` on the command line).
3. Restart the backend.

> The GeoTIFF should be **EPSG:3857** so it lines up in the browser. The shipped
> `india_basemap.tif` already is. Reproject others once on a connected machine:
> `gdalwarp -t_srs EPSG:3857 -of COG input.tif output.tif`.

---

## 8. Using DRDO's DTED data

Copy DRDO's `.dt1` tiles into `data/terrain/dted/`, **or** set
`gis.terrain.dted-base-path` to the folder where they already live. **Layout and
file-name casing don't matter** — each DTED file records the 1° cell it covers in
its own header, so the reader finds the right tile regardless of naming
(`n28/e077.dt1`, `E077/N28.DT1`, or one flat folder all work). If a location has
no covering tile, that spot is treated as elevation 0 (flat) with a logged
warning, rather than failing — so a partial DTED set still works for its area.

---

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `mvn: command not found` | Maven isn't installed / on PATH. Install Maven 3, or use a prebuilt JAR (Section 1.3). |
| `java: command not found` / `UnsupportedClassVersionError` | Java 17 not installed or not on PATH. |
| Backend fails: `Connection refused` to 5432 | PostgreSQL isn't running, or wrong host/port in `application.yml`. Confirm pgAdmin can connect, and match its host/port. |
| Backend fails: `password authentication failed` | `username`/`password` in `application.yml` don't match the pgAdmin role. |
| Backend fails: `could not open extension control file ... postgis` | PostGIS not installed for this PostgreSQL — install the PostGIS package, then re-run the `CREATE EXTENSION` in Section 2.3. |
| Backend fails: `permission denied to create extension "postgis"` | Run the `CREATE EXTENSION` lines (Section 2.3) as a Postgres superuser in pgAdmin. |
| `ng: command not found` / `npm install` errors offline | Node/Angular CLI missing, or `node_modules` not present — see Section 1. |
| Frontend loads but "Failed to load deployments" | Backend not running or not reachable on 8080 — check `curl .../actuator/health`. |
| Map is blank, only a grid | No readable GeoTIFF found, or `default-base-map` name doesn't match a file, or the `.tif` isn't EPSG:3857. |
| Map shows but is offset | The GeoTIFF isn't EPSG:3857 — reproject with the `gdalwarp` command above. |
| Everywhere is flat (elevation 0, always ELLIPSE) | DTED not found — backend log shows `Indexed 0 DTED tile(s)`. Fix `dted-base-path`, restart. |

Everything runs fully offline — no internet, CDN, or external services are used at
runtime.
