package in.drdo.gis.util;

import org.locationtech.jts.geom.*;

public final class GeoUtils {
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double EARTH_RADIUS_M = 6378137.0;

    private GeoUtils() {}

    public static GeometryFactory geometryFactory() { return GF; }

    public static Point createPoint(double lon, double lat) {
        return GF.createPoint(new Coordinate(lon, lat));
    }

    /** Returns distance in metres between two WGS84 points (Haversine). */
    public static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Offset lat/lon by dx (east, metres) and dy (north, metres). */
    public static double[] offsetLatLon(double lat, double lon, double dxMetres, double dyMetres) {
        double newLat = lat + Math.toDegrees(dyMetres / EARTH_RADIUS_M);
        double newLon = lon + Math.toDegrees(dxMetres / (EARTH_RADIUS_M * Math.cos(Math.toRadians(lat))));
        return new double[]{newLat, newLon};
    }

    /** Bearing in degrees from point1 to point2 (0=North, clockwise). */
    public static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);
        double y = Math.sin(dLon) * Math.cos(rLat2);
        double x = Math.cos(rLat1) * Math.sin(rLat2)
                 - Math.sin(rLat1) * Math.cos(rLat2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
