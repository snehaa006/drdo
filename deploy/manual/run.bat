@echo off
REM ---------------------------------------------------------------------------
REM  DRDO GIS - start the whole system (backend API + web UI) from ONE jar.
REM  Requirements on this machine:  Java 17+  and  a running PostgreSQL+PostGIS.
REM  NO Maven, NO Node, NO Python, NO Docker are needed.
REM  When it prints "Started", open:  http://localhost:8080
REM  Stop it by closing this window or pressing Ctrl+C.
REM
REM  DRDO's terrain elsewhere? append the path, e.g.:
REM     run.bat --gis.terrain.dted-base-path=C:/drdo/dted
REM ---------------------------------------------------------------------------
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: 'java' was not found on PATH. Install Java 17+ ^(Temurin^) first.
  exit /b 1
)

echo Starting DRDO GIS ...  the UI will be at  http://localhost:8080  (close window to stop)
java -jar "%SCRIPT_DIR%backend.jar" ^
  --spring.config.additional-location="optional:file:%SCRIPT_DIR%application.properties" ^
  --gis.terrain.dted-base-path="%REPO_ROOT%\data\terrain\dted" ^
  --gis.terrain.geotiff-base-path="%REPO_ROOT%\data\terrain\geotiff" ^
  %*
endlocal
