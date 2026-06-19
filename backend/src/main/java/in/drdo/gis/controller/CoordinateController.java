
package in.drdo.gis.controller;

import in.drdo.gis.service.CoordinateConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/coordinates")
@RequiredArgsConstructor
public class CoordinateController {

    private final CoordinateConversionService conversionService;

    @GetMapping("/to-utm")
    public ResponseEntity<Map<String, Object>> toUtm(
            @RequestParam double lat, @RequestParam double lon) {
        return ResponseEntity.ok(conversionService.latLonToUtm(lat, lon));
    }

    @GetMapping("/from-utm")
    public ResponseEntity<Map<String, Double>> fromUtm(
            @RequestParam double easting,
            @RequestParam double northing,
            @RequestParam int zone,
            @RequestParam(defaultValue = "true") boolean north) {
        return ResponseEntity.ok(conversionService.utmToLatLon(easting, northing, zone, north));
    }

    @GetMapping("/metres-per-degree")
    public ResponseEntity<Map<String, Double>> metresPerDegree(@RequestParam double lat) {
        return ResponseEntity.ok(conversionService.metresPerDegree(lat));
    }

    @GetMapping("/transform")
    public ResponseEntity<Map<String, Double>> transform(
            @RequestParam double x, @RequestParam double y,
            @RequestParam String srcCrs, @RequestParam String tgtCrs) {
        return ResponseEntity.ok(conversionService.transform(x, y, srcCrs, tgtCrs));
    }
}
