
package in.drdo.gis.controller;

import in.drdo.gis.service.PolygonValidationService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/validation")
@RequiredArgsConstructor
public class ValidationController {

    private final PolygonValidationService validationService;

    @PostMapping("/polygon")
    public ResponseEntity<Map<String, Object>> validatePolygon(@RequestBody Map<String, String> body) {
        String wkt = body.get("wkt");
        if (wkt == null || wkt.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "wkt is required"));
        }
        try {
            Polygon polygon = (Polygon) new WKTReader().read(wkt);
            PolygonValidationService.ValidationResult result = validationService.validate(polygon);
            return ResponseEntity.ok(Map.of(
                "valid",           result.valid(),
                "message",         result.message() != null ? result.message() : "",
                "selfIntersects",  validationService.hasSelfIntersection(polygon),
                "repairable",      result.repaired() != null
            ));
        } catch (ParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid WKT: " + e.getMessage()));
        }
    }
}
