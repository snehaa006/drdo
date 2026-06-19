package in.drdo.gis.engine;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.proj4j.*;
import org.springframework.stereotype.Service;

/**
 * Handles coordinate conversions: WGS84 ↔ UTM, custom CRS transforms.
 */
@Slf4j
@Service
public class ProjectionService {

    private final CRSFactory crsFactory = new CRSFactory();
    private final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();

    /** WGS84 → UTM (auto-zone). Returns [easting, northing, zone]. */
    public double[] latLonToUtm(double lat, double lon) {
        int zone = utmZone(lon);
        boolean north = lat >= 0;
        String crsName = utmCrsName(zone, north);
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem utmCrs = crsFactory.createFromName(crsName);
        CoordinateTransform transform = ctFactory.createTransform(wgs84, utmCrs);
        ProjCoordinate src = new ProjCoordinate(lon, lat);
        ProjCoordinate dst = new ProjCoordinate();
        transform.transform(src, dst);
        return new double[]{dst.x, dst.y, zone};
    }

    /** UTM → WGS84. Returns [lat, lon]. */
    public double[] utmToLatLon(double easting, double northing, int zone, boolean north) {
        String crsName = utmCrsName(zone, north);
        CoordinateReferenceSystem utmCrs = crsFactory.createFromName(crsName);
        CoordinateReferenceSystem wgs84  = crsFactory.createFromName("EPSG:4326");
        CoordinateTransform transform = ctFactory.createTransform(utmCrs, wgs84);
        ProjCoordinate src = new ProjCoordinate(easting, northing);
        ProjCoordinate dst = new ProjCoordinate();
        transform.transform(src, dst);
        return new double[]{dst.y, dst.x};
    }

    /** Generic CRS transform. Returns [x, y] in target CRS. */
    public double[] transform(double x, double y, String sourceCrs, String targetCrs) {
        CoordinateReferenceSystem src = crsFactory.createFromName(sourceCrs);
        CoordinateReferenceSystem tgt = crsFactory.createFromName(targetCrs);
        CoordinateTransform transform = ctFactory.createTransform(src, tgt);
        ProjCoordinate srcCoord = new ProjCoordinate(x, y);
        ProjCoordinate dstCoord = new ProjCoordinate();
        transform.transform(srcCoord, dstCoord);
        return new double[]{dstCoord.x, dstCoord.y};
    }

    /** Returns metres-per-degree-latitude at given latitude (approx). */
    public double metresPerDegreeLat(double lat) {
        double a = 6378137.0;
        double b = 6356752.3142;
        double e2 = 1 - (b * b) / (a * a);
        double sinLat = Math.sin(Math.toRadians(lat));
        double num = Math.PI * a * (1 - e2);
        double den = 180.0 * Math.pow(1 - e2 * sinLat * sinLat, 1.5);
        return num / den;
    }

    /** Returns metres-per-degree-longitude at given latitude. */
    public double metresPerDegreeLon(double lat) {
        double a = 6378137.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        double b = 6356752.3142;
        double e2 = 1 - (b * b) / (a * a);
        double sinLat = Math.sin(Math.toRadians(lat));
        double num = Math.PI * a * cosLat;
        double den = 180.0 * Math.sqrt(1 - e2 * sinLat * sinLat);
        return num / den;
    }

    private int utmZone(double lon) {
        return (int) Math.floor((lon + 180.0) / 6.0) + 1;
    }

    private String utmCrsName(int zone, boolean north) {
        int epsg = north ? 32600 + zone : 32700 + zone;
        return "EPSG:" + epsg;
    }
}
