package in.drdo.gis.engine;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.entity.SlopeAnalysis;
import in.drdo.gis.entity.TerrainAnalysis;
import in.drdo.gis.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes terrain slope, roughness, directional influence,
 * and suitability score from DTED elevation samples.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerrainEngine {

    private static final double PLANAR_SLOPE_THRESHOLD = 5.0;   // degrees (fallback threshold)
    // Roughness is the RMS deviation of the ground from its best-fit plane, in metres.
    // Below PLANAR_ROUGHNESS_MAX_M the surface is smooth enough to treat as planar; the
    // reference value scales roughness into the [0,1] suitability contribution.
    private static final double PLANAR_ROUGHNESS_MAX_M = 2.0;
    private static final double ROUGHNESS_SUITABILITY_REF_M = 15.0;
    // Adaptive sampling grid bounds. The number of rows/cols is chosen per request so
    // the spacing between samples stays close to the configured sample distance
    // (~30 m ≈ DTED resolution), regardless of the deployment size — a large area is
    // sampled just as finely as a small one instead of being stretched over a fixed
    // 11×11 grid. GRID_MIN keeps small areas well-sampled (and leaves room for the
    // 3×3 slope stencil); GRID_MAX bounds memory/compute for very large areas.
    private static final int GRID_MIN = 11;
    private static final int GRID_MAX = 501;
    // Cap on how many samples are stored per deployment (the full dense grid drives the
    // statistics; persisting every point of a large grid would flood the database).
    private static final int PERSIST_PER_SIDE = 15;

    private final DtedReader dtedReader;
    private final GisProperties gisProperties;

    public record TerrainResult(
        double meanElevationM,
        double minElevationM,
        double maxElevationM,
        double meanSlopeDegrees,
        double maxSlopeDegrees,
        double terrainRoughness,
        double suitabilityScore,
        boolean isPlanar,
        int sampleCount,
        List<SlopeSample> slopeSamples
    ) {}

    public record SlopeSample(
        double lat, double lon,
        double elevationM,
        double slopeDegrees,
        double aspectDegrees,
        double slopeNs, double slopeEw
    ) {}

    /**
     * Full terrain analysis using the default planar slope threshold (5°).
     * Retained for callers (e.g. recompute) that do not supply a threshold.
     */
    public TerrainResult analyse(double centerLat, double centerLon,
                                  double frontageM, double depthM,
                                  double headingDeg) {
        return analyse(centerLat, centerLon, frontageM, depthM, headingDeg,
                       PLANAR_SLOPE_THRESHOLD);
    }

    /**
     * Full terrain analysis over the deployment area.
     * Samples a grid of DTED points and computes statistics.
     *
     * @param slopeThresholdDeg slope (degrees) below which terrain is treated as
     *                          planar → an ellipse is drawn; at or above it the
     *                          terrain is non-planar → an adaptive Bézier is used.
     */
    public TerrainResult analyse(double centerLat, double centerLon,
                                  double frontageM, double depthM,
                                  double headingDeg, double slopeThresholdDeg) {
        double halfF = frontageM / 2.0;
        double halfD = depthM    / 2.0;
        double sampleDistM = gisProperties.getTerrain().getElevationSampleDistanceM();
        if (sampleDistM <= 0) sampleDistM = 30.0;

        // Determine bbox in lat/lon
        double degLat = halfD  / 111320.0;
        double degLon = halfF  / (111320.0 * Math.cos(Math.toRadians(centerLat)));
        double margin = sampleDistM / 111320.0;

        double minLat = centerLat - degLat - margin;
        double maxLat = centerLat + degLat + margin;
        double minLon = centerLon - degLon - margin;
        double maxLon = centerLon + degLon + margin;

        // Adaptive grid: pick enough rows/cols that the spacing between samples stays
        // ~= sampleDistM in both directions, so terrain features aren't skipped over on
        // large deployments. Clamped to [GRID_MIN, GRID_MAX].
        int cols = clampGrid((int) Math.round(frontageM / sampleDistM) + 1);
        int rows = clampGrid((int) Math.round(depthM    / sampleDistM) + 1);

        double[][] elevGrid = dtedReader.sampleGrid(minLat, minLon, maxLat, maxLon, rows, cols);
        double latStep = (maxLat - minLat) / (rows - 1);
        double lonStep = (maxLon - minLon) / (cols - 1);
        // Cell size in metres along each axis separately — the cells are not
        // necessarily square, and using one size for both would skew the slope.
        double cellSizeY = GeoUtils.haversineM(minLat, minLon, minLat + latStep, minLon);
        double cellSizeX = GeoUtils.haversineM(minLat, minLon, minLat, minLon + lonStep);
        if (cellSizeY <= 0) cellSizeY = sampleDistM;
        if (cellSizeX <= 0) cellSizeX = sampleDistM;

        // Persist only an evenly-spaced subset of the samples (see PERSIST_PER_SIDE).
        int rowStride = Math.max(1, (rows - 2) / PERSIST_PER_SIDE);
        int colStride = Math.max(1, (cols - 2) / PERSIST_PER_SIDE);

        List<SlopeSample> samples = new ArrayList<>();
        double elevSum = 0, elevMin = Double.POSITIVE_INFINITY, elevMax = Double.NEGATIVE_INFINITY;
        double slopeSum = 0, slopeMax = Double.NEGATIVE_INFINITY;
        int n = 0;

        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < cols - 1; c++) {
                double elev = elevGrid[r][c];

                // Finite-difference slope computation (Horn's method), corrected for
                // non-square cells by using the per-axis cell size.
                double dzdx = ((elevGrid[r-1][c+1] + 2*elevGrid[r][c+1] + elevGrid[r+1][c+1])
                             - (elevGrid[r-1][c-1] + 2*elevGrid[r][c-1] + elevGrid[r+1][c-1]))
                             / (8.0 * cellSizeX);
                double dzdy = ((elevGrid[r+1][c-1] + 2*elevGrid[r+1][c] + elevGrid[r+1][c+1])
                             - (elevGrid[r-1][c-1] + 2*elevGrid[r-1][c] + elevGrid[r-1][c+1]))
                             / (8.0 * cellSizeY);

                double slopeDeg  = Math.toDegrees(Math.atan(Math.sqrt(dzdx*dzdx + dzdy*dzdy)));
                double aspectDeg = (Math.toDegrees(Math.atan2(dzdy, -dzdx)) + 360.0) % 360.0;

                // Statistics accumulate over EVERY interior point — the full dense grid.
                elevSum += elev;
                if (elev < elevMin) elevMin = elev;
                if (elev > elevMax) elevMax = elev;
                slopeSum += slopeDeg;
                if (slopeDeg > slopeMax) slopeMax = slopeDeg;
                n++;

                // ...but only store a bounded, evenly-spaced subset for the record.
                if ((r - 1) % rowStride == 0 && (c - 1) % colStride == 0) {
                    double lat = minLat + r * latStep;
                    double lon = minLon + c * lonStep;
                    samples.add(new SlopeSample(lat, lon, elev, slopeDeg, aspectDeg, dzdy, dzdx));
                }
            }
        }

        double meanElev  = n > 0 ? elevSum  / n : 0.0;
        double meanSlope = n > 0 ? slopeSum / n : 0.0;
        if (n == 0) { elevMin = elevMax = slopeMax = 0.0; }

        double roughness = computeRoughness(elevGrid);
        // Core rule: flat terrain (mean slope below the requested threshold and
        // low roughness) → ellipse; otherwise → adaptive Bézier.
        double planarSlopeLimit = slopeThresholdDeg > 0 ? slopeThresholdDeg : PLANAR_SLOPE_THRESHOLD;
        boolean isPlanar = meanSlope < planarSlopeLimit
                        && roughness < PLANAR_ROUGHNESS_MAX_M;
        double suitability = computeSuitabilityScore(meanSlope, roughness, planarSlopeLimit);

        return new TerrainResult(
            meanElev,
            elevMin,
            elevMax,
            meanSlope,
            slopeMax,
            roughness,
            suitability,
            isPlanar,
            n,          // total points analysed across the dense grid
            samples     // bounded, evenly-spaced subset for persistence
        );
    }

    private static int clampGrid(int v) {
        if (v < GRID_MIN) return GRID_MIN;
        if (v > GRID_MAX) return GRID_MAX;
        return v;
    }

    /**
     * Returns per-direction slope influence factors for geometry distortion.
     * Keys: N, NE, E, SE, S, SW, W, NW as 0..7 factors in [0,1].
     */
    public double[] directionalSlopeFactors(double centerLat, double centerLon,
                                             double radiusM, double slopeThreshold) {
        double[] factors = new double[8];
        double[] bearings = {0, 45, 90, 135, 180, 225, 270, 315};
        double degLon = 1.0 / (111320.0 * Math.cos(Math.toRadians(centerLat)));
        double degLat = 1.0 / 111320.0;

        for (int i = 0; i < 8; i++) {
            double bearingRad = Math.toRadians(bearings[i]);
            double dLat = Math.cos(bearingRad) * radiusM * degLat;
            double dLon = Math.sin(bearingRad) * radiusM * degLon;
            double targetLat = centerLat + dLat;
            double targetLon = centerLon + dLon;
            double elevCenter = dtedReader.getElevation(centerLat, centerLon);
            double elevTarget = dtedReader.getElevation(targetLat, targetLon);
            double slopeDeg   = Math.toDegrees(Math.atan(Math.abs(elevTarget - elevCenter) / radiusM));
            // Factor: 1.0 = flat (no distortion), closer to 0 = steep (shrink in that direction)
            factors[i] = Math.max(0.1, 1.0 - Math.min(1.0, slopeDeg / slopeThreshold));
        }
        return factors;
    }

    /**
     * Terrain roughness = RMS deviation of the elevation surface from its best-fit
     * plane, in metres. This is "detrended": a smooth surface — even a steeply
     * tilted one — has roughness ≈ 0, while genuinely bumpy/irregular ground scores
     * higher. It therefore measures real surface irregularity independent of the
     * overall slope (which is reported separately). A least-squares plane
     * z = a·x + b·y + c is fitted; because the grid indices are centred, the normal
     * equations decouple (Σx = Σy = Σxy = 0), so a, b, c have closed forms.
     */
    private double computeRoughness(double[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int n = rows * cols;
        if (n == 0) return 0.0;

        double cx = (cols - 1) / 2.0;
        double cy = (rows - 1) / 2.0;
        double sxx = 0, syy = 0, sxz = 0, syz = 0, sz = 0;
        for (int r = 0; r < rows; r++) {
            double yy = r - cy;
            for (int c = 0; c < cols; c++) {
                double xx = c - cx;
                double z = grid[r][c];
                sxx += xx * xx;
                syy += yy * yy;
                sxz += xx * z;
                syz += yy * z;
                sz  += z;
            }
        }
        double a  = sxx > 0 ? sxz / sxx : 0.0;   // plane slope per column step
        double b  = syy > 0 ? syz / syy : 0.0;   // plane slope per row step
        double c0 = sz / n;                        // plane height at the centre (= mean)

        double sumSqResid = 0;
        for (int r = 0; r < rows; r++) {
            double yy = r - cy;
            for (int c = 0; c < cols; c++) {
                double fit = a * (c - cx) + b * yy + c0;
                double resid = grid[r][c] - fit;
                sumSqResid += resid * resid;
            }
        }
        return Math.sqrt(sumSqResid / n);
    }

    /**
     * Suitability in [0,1]: a decision-support score, not a physical measurement.
     * 70% comes from how gentle the mean slope is relative to the requested
     * threshold, 30% from how smooth (low-roughness) the ground is. 1.0 = flat and
     * smooth; it falls toward 0 as the terrain gets steeper or more irregular.
     */
    private double computeSuitabilityScore(double meanSlope, double roughnessM, double slopeThreshold) {
        double slopeScore     = Math.max(0, 1.0 - meanSlope  / slopeThreshold);
        double roughnessScore = Math.max(0, 1.0 - roughnessM / ROUGHNESS_SUITABILITY_REF_M);
        return slopeScore * 0.7 + roughnessScore * 0.3;
    }
}
