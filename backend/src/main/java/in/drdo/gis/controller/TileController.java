package in.drdo.gis.controller;

import in.drdo.gis.service.TileManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/tiles")
@RequiredArgsConstructor
public class TileController {

    private final TileManagementService tileService;

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scan() {
        int count = tileService.scanAndRegisterGeoTiffs();
        return ResponseEntity.ok(Map.of("registered", count, "status", "OK"));
    }

    @GetMapping("/bbox")
    public ResponseEntity<?> tilesInBbox(
            @RequestParam double minLat, @RequestParam double minLon,
            @RequestParam double maxLat, @RequestParam double maxLon) {
        return ResponseEntity.ok(tileService.getTilesForBbox(minLat, minLon, maxLat, maxLon));
    }

    /** Lists every offline GeoTIFF base map available on disk, with geo-bounds. */
    @GetMapping("/geotiff")
    public ResponseEntity<List<Map<String, Object>>> listGeoTiffs() {
        return ResponseEntity.ok(tileService.listAvailableGeoTiffs());
    }

    /** Streams a GeoTIFF file so the frontend can render it as a base layer. */
    @GetMapping("/geotiff/{name}")
    public ResponseEntity<Resource> getGeoTiff(@PathVariable String name) {
        Path file = tileService.resolveGeoTiff(name);
        Resource resource = new PathResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/tiff"))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }
}
