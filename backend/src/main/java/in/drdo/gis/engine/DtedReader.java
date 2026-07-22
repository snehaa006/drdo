package in.drdo.gis.engine;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.entity.TerrainTile;
import in.drdo.gis.exception.TerrainDataException;
import in.drdo.gis.repository.TerrainTileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Reads DTED Level 0/1/2 (.dt0/.dt1/.dt2) terrain elevation files.
 * DTED format: fixed-size binary with UHL, DSI, ACC header records followed
 * by data records containing 16-bit signed elevation values.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DtedReader {

    private static final int UHL_SIZE   = 80;
    private static final int DSI_SIZE   = 648;
    private static final int ACC_SIZE   = 2700;
    private static final int HEADER_TOTAL = UHL_SIZE + DSI_SIZE + ACC_SIZE;

    private final GisProperties gisProperties;
    private final TerrainTileRepository terrainTileRepository;

    /** Lazily-built index of DTED files by 1° cell key ("lat_lon"), keyed on each
     *  file's own UHL header origin — so it works for ANY folder layout / casing. */
    private volatile Map<String, String> dtedIndex;

    /** Each covering DTED tile is read into memory ONCE (all elevation posts) and cached
     *  by its 1° cell, so every subsequent sample is an in-memory bilinear lookup rather
     *  than a fresh file open + header parse. This is what lets a large deployment be
     *  sampled at full resolution (millions of points) in well under a second. */
    private final Map<String, LoadedTile> cellCache   = new ConcurrentHashMap<>();
    /** Cells known to have no covering DTED tile — avoids re-querying over empty areas. */
    private final Set<String>             missingCells = ConcurrentHashMap.newKeySet();

    /**
     * Returns the elevation in metres for the given WGS84 coordinate by bilinear
     * interpolation from the covering DTED tile (0 if no tile covers the point).
     */
    public double getElevation(double lat, double lon) {
        String cellKey = (int) Math.floor(lat) + "_" + (int) Math.floor(lon);
        LoadedTile lt = cellCache.get(cellKey);
        if (lt != null) return lt.elevationAt(lat, lon);
        if (missingCells.contains(cellKey)) return 0.0;

        TerrainTile tile = resolveTile(lat, lon);
        if (tile == null) {
            if (missingCells.add(cellKey)) {
                log.warn("No DTED tile for cell {} — treating that area as elevation 0", cellKey);
            }
            return 0.0;
        }
        lt = cellCache.computeIfAbsent(cellKey, k -> loadTile(tile));
        return lt.elevationAt(lat, lon);
    }

    /** Batch elevation sampling over a regular grid inside the given bbox. */
    public double[][] sampleGrid(double minLat, double minLon,
                                  double maxLat, double maxLon,
                                  int rows, int cols) {
        double[][] elevations = new double[rows][cols];
        double latStep = (maxLat - minLat) / (rows - 1);
        double lonStep = (maxLon - minLon) / (cols - 1);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double sampleLat = minLat + r * latStep;
                double sampleLon = minLon + c * lonStep;
                elevations[r][c] = getElevation(sampleLat, sampleLon);
            }
        }
        return elevations;
    }

    // ------------------------------------------------------------------ //

    private TerrainTile resolveTile(double lat, double lon) {
        List<TerrainTile> tiles = terrainTileRepository.findTilesCoveringPoint(
            lat, lon,
            List.of(TerrainTile.TileType.DTED1, TerrainTile.TileType.DTED0, TerrainTile.TileType.DTED2)
        );
        if (!tiles.isEmpty()) return tiles.get(0);
        // Fall back: attempt filesystem discovery
        return discoverTileFromFilesystem(lat, lon).orElse(null);
    }

    private Optional<TerrainTile> discoverTileFromFilesystem(double lat, double lon) {
        int latBase = (int) Math.floor(lat);
        int lonBase = (int) Math.floor(lon);
        String basePath = gisProperties.getTerrain().getDtedBasePath();

        // Fast path: the documented sample layout  <base>/{n|s}NN/{e|w}EEE.dtX
        String latDir  = (latBase >= 0 ? "n" : "s") + String.format("%02d", Math.abs(latBase));
        String lonFile = (lonBase >= 0 ? "e" : "w") + String.format("%03d", Math.abs(lonBase));
        for (String ext : new String[]{".dt1", ".dt0", ".dt2", ".DT1", ".DT0", ".DT2"}) {
            Path candidate = Path.of(basePath, latDir, lonFile + ext);
            if (Files.exists(candidate)) {
                return Optional.of(registerTile(candidate, latBase, lonBase));
            }
        }

        // Robust path: locate the tile by each DTED file's own UHL header origin,
        // so ANY folder layout or file-name casing DRDO uses is handled (e.g. the
        // standard <lon>/<lat>.dt1, upper-case names, or a single flat folder).
        String fp = dtedFileIndex().get(latBase + "_" + lonBase);
        if (fp != null) {
            return Optional.of(registerTile(Path.of(fp), latBase, lonBase));
        }
        return Optional.empty();
    }

    /** Builds a TerrainTile for a discovered file and persists it to the cache. */
    private TerrainTile registerTile(Path file, int latBase, int lonBase) {
        String lower = file.getFileName().toString().toLowerCase();
        TerrainTile.TileType type = lower.endsWith(".dt2") ? TerrainTile.TileType.DTED2
                                  : lower.endsWith(".dt0") ? TerrainTile.TileType.DTED0
                                  : TerrainTile.TileType.DTED1;
        TerrainTile tile = TerrainTile.builder()
            .tileKey(latBase + "_" + lonBase)
            .filePath(file.toString())
            .tileType(type)
            .minLat((double) latBase)
            .maxLat((double) (latBase + 1))
            .minLon((double) lonBase)
            .maxLon((double) (lonBase + 1))
            .resolutionArcSec(type == TerrainTile.TileType.DTED1 ? 3
                            : type == TerrainTile.TileType.DTED2 ? 1 : 30)
            .crsCode("EPSG:4326")
            .build();
        terrainTileRepository.save(tile);
        return tile;
    }

    /** Lazily indexes every DTED file under the base path by the 1° cell its UHL
     *  header declares. Built once, then reused (a DB tile cache fronts this). */
    private Map<String, String> dtedFileIndex() {
        Map<String, String> idx = dtedIndex;
        if (idx == null) {
            synchronized (this) {
                idx = dtedIndex;
                if (idx == null) {
                    idx = buildDtedIndex();
                    dtedIndex = idx;
                }
            }
        }
        return idx;
    }

    private Map<String, String> buildDtedIndex() {
        Map<String, String> idx = new HashMap<>();
        Path base = Path.of(gisProperties.getTerrain().getDtedBasePath());
        if (!Files.isDirectory(base)) return idx;
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".dt0") || n.endsWith(".dt1") || n.endsWith(".dt2");
                })
                .forEach(p -> {
                    int[] cell = cellFromHeader(p);
                    if (cell != null) idx.putIfAbsent(cell[0] + "_" + cell[1], p.toString());
                });
        } catch (IOException ex) {
            log.warn("DTED index scan failed under {}: {}", base, ex.getMessage());
        }
        log.info("Indexed {} DTED tile(s) by header origin under {}", idx.size(), base);
        return idx;
    }

    /** Reads a DTED file's UHL header and returns its origin cell {latBase, lonBase}. */
    private int[] cellFromHeader(Path file) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            byte[] uhl = new byte[UHL_SIZE];
            if (raf.read(uhl) < 20) return null;
            double originLon = parseDtedCoord(uhl, 4, 8);   // DLON
            double originLat = parseDtedCoord(uhl, 12, 8);  // DLAT
            return new int[]{ (int) Math.floor(originLat), (int) Math.floor(originLon) };
        } catch (Exception ex) {
            log.warn("Could not read DTED header {}: {}", file, ex.getMessage());
            return null;
        }
    }

    /** Reads an entire DTED tile into memory once: header params + every elevation post.
     *  DTED data records are one per longitude line (west→east), each holding all the
     *  latitude posts (south→north) as big-endian signed 16-bit values. */
    private LoadedTile loadTile(TerrainTile tile) {
        Path path = Path.of(tile.getFilePath());
        if (!Files.exists(path)) {
            throw new TerrainDataException("DTED tile file missing: " + path);
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            byte[] uhl = new byte[UHL_SIZE];
            raf.readFully(uhl);
            double lonInterval = parseDtedInterval(uhl, 20); // arc-seconds
            double latInterval = parseDtedInterval(uhl, 24);
            int numLonLines  = parseShort(uhl, 47);
            int numLatPoints = parseShort(uhl, 51);
            int recordLen = 8 + 2 * numLatPoints + 4; // 8-byte header + posts + 4-byte checksum

            short[][] posts = new short[numLonLines][numLatPoints];
            byte[] rec = new byte[recordLen];
            for (int lonLine = 0; lonLine < numLonLines; lonLine++) {
                raf.seek((long) HEADER_TOTAL + (long) lonLine * recordLen);
                raf.readFully(rec);
                ByteBuffer bb = ByteBuffer.wrap(rec).order(ByteOrder.BIG_ENDIAN);
                bb.position(8); // skip the per-record header
                short[] col = posts[lonLine];
                for (int latPt = 0; latPt < numLatPoints; latPt++) {
                    col[latPt] = bb.getShort();
                }
            }
            log.info("Loaded DTED tile {} ({}×{} posts) into memory",
                     path.getFileName(), numLonLines, numLatPoints);
            return new LoadedTile(tile.getMinLat(), tile.getMinLon(),
                                  lonInterval, latInterval, numLonLines, numLatPoints, posts);
        } catch (IOException ex) {
            throw new TerrainDataException("Failed reading DTED tile: " + path, ex);
        }
    }

    /** A DTED tile fully in memory, sampled by in-place bilinear interpolation. */
    private record LoadedTile(double minLat, double minLon,
                              double lonIntervalSec, double latIntervalSec,
                              int numLonLines, int numLatPoints,
                              short[][] posts) {
        double elevationAt(double lat, double lon) {
            double lonIdxF = ((lon - minLon) * 3600.0) / lonIntervalSec;
            double latIdxF = ((lat - minLat) * 3600.0) / latIntervalSec;
            int lonIdx = Math.max(0, Math.min((int) Math.floor(lonIdxF), numLonLines - 2));
            int latIdx = Math.max(0, Math.min((int) Math.floor(latIdxF), numLatPoints - 2));
            double fracLon = lonIdxF - lonIdx;
            double fracLat = latIdxF - latIdx;
            double e00 = posts[lonIdx][latIdx];
            double e10 = posts[lonIdx + 1][latIdx];
            double e01 = posts[lonIdx][latIdx + 1];
            double e11 = posts[lonIdx + 1][latIdx + 1];
            return (1 - fracLon) * ((1 - fracLat) * e00 + fracLat * e01)
                 + fracLon      * ((1 - fracLat) * e10 + fracLat * e11);
        }
    }

    /** Parse DTED DDMMSS.SH coordinate string from byte array at offset. */
    private double parseDtedCoord(byte[] buf, int offset, int len) {
        String s = new String(buf, offset, len).trim();
        if (s.length() < 7) return 0.0;
        char hemi = s.charAt(s.length() - 1);
        String numeric = s.substring(0, s.length() - 1);
        int deg  = Integer.parseInt(numeric.substring(0, numeric.length() - 4));
        int min  = Integer.parseInt(numeric.substring(numeric.length() - 4, numeric.length() - 2));
        int sec  = Integer.parseInt(numeric.substring(numeric.length() - 2));
        double val = deg + min / 60.0 + sec / 3600.0;
        return (hemi == 'S' || hemi == 'W') ? -val : val;
    }

    private double parseDtedInterval(byte[] buf, int offset) {
        String s = new String(buf, offset, 4).trim();
        try { return Double.parseDouble(s) / 10.0; } catch (NumberFormatException e) { return 3.0; }
    }

    private int parseShort(byte[] buf, int offset) {
        String s = new String(buf, offset, 4).trim();
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1; }
    }
}
