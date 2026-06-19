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
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * Computes terrain slope, roughness, directional influence,
 * and suitability score from DTED elevation samples.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerrainEngine {

    private static final double PLANAR_SLOPE_THRESHOLD = 5.0;   // degrees
    private static final double PLANAR_ROUGHNESS_THRESHOLD = 0.15;
    private static final int    GRID_ROWS = 11;
    private static final int    GRID_COLS = 11;

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

        // Determine bbox in lat/lon
        double degLat = halfD  / 111320.0;
        double degLon = halfF  / (111320.0 * Math.cos(Math.toRadians(centerLat)));
        double margin = sampleDistM / 111320.0;

        double minLat = centerLat - degLat - margin;
        double maxLat = centerLat + degLat + margin;
        double minLon = centerLon - degLon - margin;
        double maxLon = centerLon + degLon + margin;

        double[][] elevGrid = dtedReader.sampleGrid(minLat, minLon, maxLat, maxLon,
                                                     GRID_ROWS, GRID_COLS);
        double latStep = (maxLat - minLat) / (GRID_ROWS - 1);
        double lonStep = (maxLon - minLon) / (GRID_COLS - 1);
        double cellSizeM = GeoUtils.haversineM(minLat, minLon, minLat + latStep, minLon);

        List<SlopeSample> samples = new ArrayList<>();

        for (int r = 1; r < GRID_ROWS - 1; r++) {
            for (int c = 1; c < GRID_COLS - 1; c++) {
                double lat = minLat + r * latStep;
                double lon = minLon + c * lonStep;
                double elev = elevGrid[r][c];

                // Finite-difference slope computation (Horn's method)
                double dzdx = ((elevGrid[r-1][c+1] + 2*elevGrid[r][c+1] + elevGrid[r+1][c+1])
                             - (elevGrid[r-1][c-1] + 2*elevGrid[r][c-1] + elevGrid[r+1][c-1]))
                             / (8.0 * cellSizeM);
                double dzdy = ((elevGrid[r+1][c-1] + 2*elevGrid[r+1][c] + elevGrid[r+1][c+1])
                             - (elevGrid[r-1][c-1] + 2*elevGrid[r-1][c] + elevGrid[r-1][c+1]))
                             / (8.0 * cellSizeM);

                double slopeDeg  = Math.toDegrees(Math.atan(Math.sqrt(dzdx*dzdx + dzdy*dzdy)));
                double aspectDeg = (Math.toDegrees(Math.atan2(dzdy, -dzdx)) + 360.0) % 360.0;

                samples.add(new SlopeSample(lat, lon, elev, slopeDeg, aspectDeg, dzdy, dzdx));
            }
        }

        DoubleSummaryStatistics elevStats = samples.stream()
            .mapToDouble(SlopeSample::elevationM).summaryStatistics();
        DoubleSummaryStatistics slopeStats = samples.stream()
            .mapToDouble(SlopeSample::slopeDegrees).summaryStatistics();

        double roughness = computeRoughness(elevGrid);
        // Core rule: flat terrain (mean slope below the requested threshold and
        // low roughness) → ellipse; otherwise → adaptive Bézier.
        double planarSlopeLimit = slopeThresholdDeg > 0 ? slopeThresholdDeg : PLANAR_SLOPE_THRESHOLD;
        boolean isPlanar = slopeStats.getAverage() < planarSlopeLimit
                        && roughness < PLANAR_ROUGHNESS_THRESHOLD;
        double suitability = computeSuitabilityScore(slopeStats.getAverage(), roughness,
            planarSlopeLimit);

        return new TerrainResult(
            elevStats.getAverage(),
            elevStats.getMin(),
            elevStats.getMax(),
            slopeStats.getAverage(),
            slopeStats.getMax(),
            roughness,
            suitability,
            isPlanar,
            samples.size(),
            samples
        );
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

    private double computeRoughness(double[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        double sum = 0, sumSq = 0;
        int n = rows * cols;
        for (double[] row : grid) for (double v : row) { sum += v; sumSq += v * v; }
        double mean = sum / n;
        double variance = sumSq / n - mean * mean;
        double range = 0;
        for (double[] row : grid) for (double v : row) {
            if (v - mean > range) range = v - mean;
        }
        return range > 0 ? Math.sqrt(variance) / range : 0.0;
    }

    private double computeSuitabilityScore(double meanSlope, double roughness, double threshold) {
        double slopeScore     = Math.max(0, 1.0 - meanSlope   / threshold);
        double roughnessScore = Math.max(0, 1.0 - roughness   / 0.5);
        return (slopeScore * 0.7 + roughnessScore * 0.3);
    }
}
