package in.drdo.gis.service;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.engine.GeoTiffReader;
import in.drdo.gis.entity.TerrainTile;
import in.drdo.gis.repository.TerrainTileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.drdo.gis.exception.TerrainDataException;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TileManagementService {

    private final TerrainTileRepository tileRepo;
    private final GeoTiffReader geoTiffReader;
    private final GisProperties gisProperties;

    @Transactional
    public int scanAndRegisterGeoTiffs() {
        List<String> files = geoTiffReader.discoverTiffFiles();
        int count = 0;
        for (String filePath : files) {
            try {
                GeoTiffReader.GeoTiffMetadata meta = geoTiffReader.readMetadata(filePath);
                String key = Path.of(filePath).getFileName().toString();
                if (tileRepo.findByTileKey(key).isEmpty()) {
                    TerrainTile tile = TerrainTile.builder()
                        .tileKey(key)
                        .filePath(filePath)
                        .tileType(TerrainTile.TileType.GEOTIFF)
                        .minLat(meta.minLat())
                        .maxLat(meta.maxLat())
                        .minLon(meta.minLon())
                        .maxLon(meta.maxLon())
                        .crsCode(meta.crsCode())
                        .loadedAt(OffsetDateTime.now())
                        .build();
                    tileRepo.save(tile);
                    count++;
                }
            } catch (Exception ex) {
                log.warn("Could not register GeoTIFF {}: {}", filePath, ex.getMessage());
            }
        }
        log.info("Registered {} new GeoTIFF tiles", count);
        return count;
    }

    public List<TerrainTile> getTilesForBbox(double minLat, double minLon,
                                              double maxLat, double maxLon) {
        return tileRepo.findTilesInBbox(minLat, minLon, maxLat, maxLon);
    }

    /**
     * Lists every offline GeoTIFF available on disk together with its geo-bounds,
     * so the frontend can load it as a base map layer.
     */
    public List<Map<String, Object>> listAvailableGeoTiffs() {
        return geoTiffReader.discoverTiffFiles().stream()
            .map(fp -> {
                try {
                    GeoTiffReader.GeoTiffMetadata m = geoTiffReader.readMetadata(fp);
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", Path.of(fp).getFileName().toString());
                    info.put("crs", m.crsCode());
                    info.put("minLon", m.minLon());
                    info.put("minLat", m.minLat());
                    info.put("maxLon", m.maxLon());
                    info.put("maxLat", m.maxLat());
                    info.put("width", m.width());
                    info.put("height", m.height());
                    info.put("bandCount", m.bandCount());
                    return info;
                } catch (Exception ex) {
                    log.warn("Skipping unreadable GeoTIFF {}: {}", fp, ex.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Resolves a GeoTIFF by its file name against the discovered allow-list.
     * Matching by file-name only (never a raw path) prevents directory traversal.
     */
    public Path resolveGeoTiff(String name) {
        String safe = Path.of(name).getFileName().toString();
        return geoTiffReader.discoverTiffFiles().stream()
            .filter(fp -> Path.of(fp).getFileName().toString().equals(safe))
            .map(Path::of)
            .findFirst()
            .orElseThrow(() -> new TerrainDataException("GeoTIFF not found: " + safe));
    }
}
