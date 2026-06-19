package in.drdo.gis.service;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.engine.GeoTiffReader;
import in.drdo.gis.entity.TerrainTile;
import in.drdo.gis.repository.TerrainTileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

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
}
