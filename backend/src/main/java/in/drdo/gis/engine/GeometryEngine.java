package in.drdo.gis.engine;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.entity.ControlPoint;
import in.drdo.gis.entity.DeploymentGeometry;
import in.drdo.gis.exception.GeometryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates geometry generation:
 * - Flat terrain → ellipse
 * - Non-planar terrain → terrain-aware Bézier polygon with directional distortion
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeometryEngine {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final BezierEngine bezierEngine;
    private final TerrainEngine terrainEngine;
    private final GisProperties gisProperties;

    public record GeometryResult(
        Polygon polygon,
        DeploymentGeometry.GeometryType geometryType,
        List<ControlPoint> controlPoints,
        boolean isValid,
        String validationMessage
    ) {}

    /**
     * Generates deployment geometry based on terrain suitability.
     *
     * @param isPlanar         whether terrain was classified as planar
     * @param terrainFactors   8 directional distortion factors [0..7] = [N,NE,E,SE,S,SW,W,NW]
     */
    public GeometryResult generate(
            double centerLat, double centerLon,
            double frontageM, double depthM,
            double headingDeg, boolean isPlanar,
            double[] terrainFactors) {

        if (isPlanar) {
            return generateEllipse(centerLat, centerLon, frontageM, depthM, headingDeg);
        } else {
            return generateTerrainAdaptiveBezier(
                centerLat, centerLon, frontageM, depthM, headingDeg, terrainFactors);
        }
    }

    // ------------------------------------------------------------------ //

    private GeometryResult generateEllipse(double cLat, double cLon,
                                            double fM, double dM, double hDeg) {
        int segs = gisProperties.getGeometry().getEllipseSegments();
        Polygon poly = bezierEngine.buildEllipse(cLat, cLon, fM / 2.0, dM / 2.0, hDeg, segs);
        poly = repairIfNeeded(poly);
        return new GeometryResult(poly, DeploymentGeometry.GeometryType.ELLIPSE,
            List.of(), poly.isValid(), null);
    }

    private GeometryResult generateTerrainAdaptiveBezier(
            double cLat, double cLon, double fM, double dM,
            double hDeg, double[] terrainFactors) {

        int cpCount = gisProperties.getGeometry().getControlPointCount();
        int steps   = gisProperties.getGeometry().getBezierSmoothingSteps();
        double tension = 0.4;

        double[] baseLats = new double[cpCount];
        double[] baseLons = new double[cpCount];
        double mPerDegLat = 111320.0;
        double mPerDegLon = 111320.0 * Math.cos(Math.toRadians(cLat));
        double rotRad = Math.toRadians(hDeg);

        for (int i = 0; i < cpCount; i++) {
            double angle = 2.0 * Math.PI * i / cpCount;
            double ex = (fM / 2.0) * Math.cos(angle);
            double ey = (dM / 2.0) * Math.sin(angle);
            // Rotate by heading
            double rx = ex * Math.cos(rotRad) - ey * Math.sin(rotRad);
            double ry = ex * Math.sin(rotRad) + ey * Math.cos(rotRad);
            // Apply terrain distortion factor for nearest octant
            int octant = (int) Math.round(angle / (Math.PI / 4)) % 8;
            double factor = (terrainFactors != null && octant < terrainFactors.length)
                ? terrainFactors[octant] : 1.0;
            double scaledRx = rx * factor;
            double scaledRy = ry * factor;
            baseLats[i] = cLat + scaledRy / mPerDegLat;
            baseLons[i] = cLon + scaledRx / mPerDegLon;
        }

        // Generate smooth handles using Catmull-Rom
        double[][] handles = bezierEngine.generateSmoothHandles(baseLats, baseLons, tension);

        // Build ControlPoint entities
        List<ControlPoint> cps = new ArrayList<>();
        for (int i = 0; i < cpCount; i++) {
            ControlPoint cp = ControlPoint.builder()
                .pointIndex(i)
                .pointType(ControlPoint.PointType.ANCHOR)
                .lat(baseLats[i])
                .lon(baseLons[i])
                .handleLat1(handles[i][0])
                .handleLon1(handles[i][1])
                .handleLat2(handles[i][2])
                .handleLon2(handles[i][3])
                .build();
            cps.add(cp);
        }

        Polygon poly = bezierEngine.buildBezierPolygon(cps, steps);
        poly = repairIfNeeded(poly);

        IsValidOp valid = new IsValidOp(poly);
        boolean isValid = valid.isValid();
        String msg = isValid ? null : valid.getValidationError().getMessage();

        return new GeometryResult(poly, DeploymentGeometry.GeometryType.BEZIER_ADAPTIVE,
            cps, isValid, msg);
    }

    private Polygon repairIfNeeded(Polygon poly) {
        if (!poly.isValid()) {
            Geometry repaired = poly.buffer(0);
            if (repaired instanceof Polygon p) return p;
            if (repaired instanceof MultiPolygon mp && mp.getNumGeometries() > 0) {
                return (Polygon) mp.getGeometryN(0);
            }
            throw new GeometryException("Cannot repair invalid polygon");
        }
        return poly;
    }
}
