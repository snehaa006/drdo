
package in.drdo.gis.service;

import in.drdo.gis.engine.ProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CoordinateConversionService {

    private final ProjectionService projectionService;

    /** Convert WGS-84 decimal degrees to UTM. Returns easting, northing, zone, hemisphere. */
    public Map<String, Object> latLonToUtm(double lat, double lon) {
        double[] result = projectionService.latLonToUtm(lat, lon);
        return Map.of(
            "easting",   result[0],
            "northing",  result[1],
            "zone",      (int) result[2],
            "hemisphere", lat >= 0 ? "N" : "S"
        );
    }

    /** Convert UTM to WGS-84. Returns lat, lon. */
    public Map<String, Double> utmToLatLon(double easting, double northing, int zone, boolean north) {
        double[] result = projectionService.utmToLatLon(easting, northing, zone, north);
        return Map.of("lat", result[0], "lon", result[1]);
    }

    /** Generic CRS transform. */
    public Map<String, Double> transform(double x, double y, String srcCrs, String tgtCrs) {
        double[] result = projectionService.transform(x, y, srcCrs, tgtCrs);
        return Map.of("x", result[0], "y", result[1]);
    }

    /** Metres per degree at given latitude. */
    public Map<String, Double> metresPerDegree(double lat) {
        return Map.of(
            "latitude",  projectionService.metresPerDegreeLat(lat),
            "longitude", projectionService.metresPerDegreeLon(lat)
        );
    }
}
