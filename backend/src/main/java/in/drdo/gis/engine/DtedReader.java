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
import java.util.List;
import java.util.Optional;

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

    /**
     * Returns the elevation in metres for the given WGS84 coordinate.
     * Looks up the covering tile, reads and bilinearly interpolates.
     */
    public double getElevation(double lat, double lon) {
        TerrainTile tile = resolveTile(lat, lon);
        if (tile == null) {
            log.warn("No DTED tile found for {}/{}, returning 0", lat, lon);
            return 0.0;
        }
        return readElevationFromTile(tile, lat, lon);
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
        String latDir  = (latBase >= 0 ? "n" : "s") + String.format("%02d", Math.abs(latBase));
        String lonFile = (lonBase >= 0 ? "e" : "w") + String.format("%03d", Math.abs(lonBase));
        String basePath = gisProperties.getTerrain().getDtedBasePath();

        for (String ext : new String[]{".dt1", ".dt0", ".dt2", ".DT1", ".DT0"}) {
            Path candidate = Path.of(basePath, latDir, lonFile + ext);
            if (Files.exists(candidate)) {
                TerrainTile.TileType type = ext.toLowerCase().contains("dt1")
                    ? TerrainTile.TileType.DTED1
                    : ext.toLowerCase().contains("dt2")
                        ? TerrainTile.TileType.DTED2
                        : TerrainTile.TileType.DTED0;
                TerrainTile tile = TerrainTile.builder()
                    .tileKey(latDir + "_" + lonFile)
                    .filePath(candidate.toString())
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
                return Optional.of(tile);
            }
        }
        return Optional.empty();
    }

    private double readElevationFromTile(TerrainTile tile, double lat, double lon) {
        Path path = Path.of(tile.getFilePath());
        if (!Files.exists(path)) {
            throw new TerrainDataException("DTED tile file missing: " + path);
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            // Parse UHL to extract origin and post spacing
            byte[] uhl = new byte[UHL_SIZE];
            raf.read(uhl);

            double originLon = parseDtedCoord(uhl, 4,  8);  // DLON field
            double originLat = parseDtedCoord(uhl, 12, 8);  // DLAT field
            double lonInterval = parseDtedInterval(uhl, 20); // in arc-seconds
            double latInterval = parseDtedInterval(uhl, 24);
            int numLonLines   = parseShort(uhl, 47);
            int numLatPoints  = parseShort(uhl, 51);

            double normLat = lat - tile.getMinLat();
            double normLon = lon - tile.getMinLon();

            double lonIdxF = (normLon * 3600.0) / lonInterval;
            double latIdxF = (normLat * 3600.0) / latInterval;

            int lonIdx = (int) Math.floor(lonIdxF);
            int latIdx = (int) Math.floor(latIdxF);

            lonIdx = Math.max(0, Math.min(lonIdx, numLonLines - 2));
            latIdx = Math.max(0, Math.min(latIdx, numLatPoints - 2));

            double fracLon = lonIdxF - lonIdx;
            double fracLat = latIdxF - latIdx;

            // Each data record: 8-byte header + 2*numLatPoints bytes + 4-byte checksum
            int recordLen = 8 + 2 * numLatPoints + 4;

            short e00 = readPost(raf, lonIdx,     latIdx,     numLatPoints, recordLen);
            short e10 = readPost(raf, lonIdx + 1, latIdx,     numLatPoints, recordLen);
            short e01 = readPost(raf, lonIdx,     latIdx + 1, numLatPoints, recordLen);
            short e11 = readPost(raf, lonIdx + 1, latIdx + 1, numLatPoints, recordLen);

            // Bilinear interpolation
            double e = (1 - fracLon) * ((1 - fracLat) * e00 + fracLat * e01)
                     + fracLon      * ((1 - fracLat) * e10 + fracLat * e11);
            return e;

        } catch (IOException ex) {
            throw new TerrainDataException("Failed reading DTED tile: " + path, ex);
        }
    }

    private short readPost(RandomAccessFile raf, int lonIdx, int latIdx,
                            int numLatPoints, int recordLen) throws IOException {
        long offset = (long) HEADER_TOTAL + (long) lonIdx * recordLen + 8 + (long) latIdx * 2;
        raf.seek(offset);
        byte[] buf = new byte[2];
        raf.readFully(buf);
        // DTED posts are big-endian signed 16-bit
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).getShort();
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
