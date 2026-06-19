package in.drdo.gis.engine;

import in.drdo.gis.entity.ControlPoint;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates smooth closed cubic Bézier curves from a set of anchor + handle control points.
 * Each segment is a cubic Bézier: P0 -> handle1 -> handle2 -> P1.
 */
@Slf4j
@Component
public class BezierEngine {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final int DEFAULT_STEPS = 64;

    /**
     * Interpolates a closed polygon from control points using cubic Bézier segments.
     *
     * @param controlPoints ordered list of anchor control points (each with handle lat/lon).
     * @param steps         interpolation resolution per segment.
     * @return closed JTS Polygon.
     */
    public Polygon buildBezierPolygon(List<ControlPoint> controlPoints, int steps) {
        if (controlPoints.size() < 3) {
            throw new IllegalArgumentException("Need at least 3 control points for Bézier curve");
        }

        int n = controlPoints.size();
        Coordinate[] coords = new Coordinate[n * steps + 1];
        int idx = 0;

        for (int i = 0; i < n; i++) {
            ControlPoint p0 = controlPoints.get(i);
            ControlPoint p1 = controlPoints.get((i + 1) % n);

            // P0 anchor
            double ax = p0.getLon(), ay = p0.getLat();
            // Handle from P0 (outgoing handle)
            double bx = (p0.getHandleLon2() != null) ? p0.getHandleLon2() : ax;
            double by = (p0.getHandleLat2() != null) ? p0.getHandleLat2() : ay;
            // Handle into P1 (incoming handle)
            double cx = (p1.getHandleLon1() != null) ? p1.getHandleLon1() : p1.getLon();
            double cy = (p1.getHandleLat1() != null) ? p1.getHandleLat1() : p1.getLat();
            // P1 anchor
            double dx = p1.getLon(), dy = p1.getLat();

            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                double mt = 1.0 - t;
                double lon = mt*mt*mt*ax + 3*mt*mt*t*bx + 3*mt*t*t*cx + t*t*t*dx;
                double lat = mt*mt*mt*ay + 3*mt*mt*t*by + 3*mt*t*t*cy + t*t*t*dy;
                coords[idx++] = new Coordinate(lon, lat);
            }
        }
        // Close the ring
        coords[idx] = coords[0];

        LinearRing ring = GF.createLinearRing(coords);
        return GF.createPolygon(ring);
    }

    /**
     * Generates auto-smooth handles for a set of anchor coordinates using the
     * Catmull-Rom to Bézier conversion, so the curve passes through all anchors.
     *
     * @param lats anchor latitudes
     * @param lons anchor longitudes
     * @return array of [handleLat1, handleLon1, handleLat2, handleLon2] per anchor
     */
    public double[][] generateSmoothHandles(double[] lats, double[] lons, double tension) {
        int n = lats.length;
        double[][] handles = new double[n][4];
        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            int next = (i + 1) % n;
            // Catmull-Rom tangent
            double tx = (lons[next] - lons[prev]) * tension;
            double ty = (lats[next] - lats[prev]) * tension;
            // Incoming handle (towards current from prev direction)
            handles[i][0] = lats[i] - ty / 3.0;
            handles[i][1] = lons[i] - tx / 3.0;
            // Outgoing handle (towards next from current direction)
            handles[i][2] = lats[i] + ty / 3.0;
            handles[i][3] = lons[i] + tx / 3.0;
        }
        return handles;
    }

    /**
     * Builds a simple ellipse as a JTS Polygon.
     *
     * @param centerLat  latitude of centre
     * @param centerLon  longitude of centre
     * @param semiMajorM semi-major axis in metres (frontage/2)
     * @param semiMinorM semi-minor axis in metres (depth/2)
     * @param headingDeg rotation of the major axis from North, clockwise
     * @param segments   number of polygon segments
     */
    public Polygon buildEllipse(double centerLat, double centerLon,
                                 double semiMajorM, double semiMinorM,
                                 double headingDeg, int segments) {
        Coordinate[] coords = new Coordinate[segments + 1];
        double mPerDegLat = 111320.0;
        double mPerDegLon = 111320.0 * Math.cos(Math.toRadians(centerLat));
        double rotRad = Math.toRadians(headingDeg);

        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            // Local offsets in metres (East, North)
            double ex = semiMajorM * Math.cos(angle);
            double ey = semiMinorM * Math.sin(angle);
            // Rotate by heading
            double rx = ex * Math.cos(rotRad) - ey * Math.sin(rotRad);
            double ry = ex * Math.sin(rotRad) + ey * Math.cos(rotRad);
            double lon = centerLon + rx / mPerDegLon;
            double lat = centerLat + ry / mPerDegLat;
            coords[i] = new Coordinate(lon, lat);
        }
        coords[segments] = coords[0]; // close
        return GF.createPolygon(GF.createLinearRing(coords));
    }
}
