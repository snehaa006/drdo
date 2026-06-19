package in.drdo.gis.controller;

import in.drdo.gis.service.TileManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
