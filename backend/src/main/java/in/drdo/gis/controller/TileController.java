package in.drdo.gis.controller;

import in.drdo.gis.service.TileManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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

    /**
     * Streams a GeoTIFF file so the frontend can render it as a base layer.
     * <p>
     * Honours HTTP Range requests (RFC 7233). This is required because OpenLayers'
     * GeoTIFF source (geotiff.js) fetches large rasters in byte-range chunks and
     * throws ("Server responded with full file") if the server always returns the
     * whole file with 200 OK — Spring MVC (unlike WebFlux) does not add automatic
     * Range support for controller methods returning a plain {@code Resource}, so
     * it must be handled explicitly here.
     */
    @GetMapping("/geotiff/{name}")
    public ResponseEntity<ResourceRegion> getGeoTiff(
            @PathVariable String name,
            @RequestHeader HttpHeaders headers) throws IOException {
        Path file = tileService.resolveGeoTiff(name);
        PathResource resource = new PathResource(file);
        long contentLength = resource.contentLength();
        List<HttpRange> ranges = headers.getRange();

        HttpStatus status;
        ResourceRegion region;
        if (ranges.isEmpty()) {
            status = HttpStatus.OK;
            region = new ResourceRegion(resource, 0, contentLength);
        } else {
            status = HttpStatus.PARTIAL_CONTENT;
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            region = new ResourceRegion(resource, start, end - start + 1);
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("image/tiff"))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.getFileName() + "\"")
                .body(region);
    }
}
