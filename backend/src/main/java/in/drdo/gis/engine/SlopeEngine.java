
package in.drdo.gis.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dedicated slope computation engine.
 * Provides Horn's method, directional slope, aspect, and suitability helpers
 * independently of the TerrainEngine so they can be tested in isolation.
 */
@Slf4j
@Component
public class SlopeEngine {

    /**
     * Compute slope in degrees using Horn's 3×3 finite-difference method.
     *
     * @param grid      3×3 elevation array (rows south→north, cols west→east)
     * @param cellSizeM grid cell size in metres
     */
    public double slopeFromGrid3x3(double[][] grid, double cellSizeM) {
        if (grid.length < 3 || grid[0].length < 3) throw new IllegalArgumentException("Need 3×3 grid");
        double dzdx = ((grid[0][2] + 2*grid[1][2] + grid[2][2])
                     - (grid[0][0] + 2*grid[1][0] + grid[2][0])) / (8.0 * cellSizeM);
        double dzdy = ((grid[2][0] + 2*grid[2][1] + grid[2][2])
                     - (grid[0][0] + 2*grid[0][1] + grid[0][2])) / (8.0 * cellSizeM);
        return Math.toDegrees(Math.atan(Math.sqrt(dzdx * dzdx + dzdy * dzdy)));
    }

    /**
     * Compute aspect in degrees (0 = North, clockwise).
     *
     * @param grid      3×3 elevation array
     * @param cellSizeM cell size in metres
     */
    public double aspectFromGrid3x3(double[][] grid, double cellSizeM) {
        double dzdx = ((grid[0][2] + 2*grid[1][2] + grid[2][2])
                     - (grid[0][0] + 2*grid[1][0] + grid[2][0])) / (8.0 * cellSizeM);
        double dzdy = ((grid[2][0] + 2*grid[2][1] + grid[2][2])
                     - (grid[0][0] + 2*grid[0][1] + grid[0][2])) / (8.0 * cellSizeM);
        return (Math.toDegrees(Math.atan2(dzdy, -dzdx)) + 360.0) % 360.0;
    }

    /**
     * Slope along a specific azimuth bearing (degrees from North, clockwise).
     * Returns signed slope: positive = uphill, negative = downhill.
     */
    public double directionalSlope(double slopeDeg, double aspectDeg, double bearingDeg) {
        double diff = Math.toRadians(bearingDeg - aspectDeg);
        return slopeDeg * Math.cos(diff);
    }

    /**
     * Compute terrain roughness as coefficient of variation of a flat elevation array.
     */
    public double roughness(double[] elevations) {
        int n = elevations.length;
        if (n == 0) return 0.0;
        double sum = 0, sumSq = 0;
        for (double e : elevations) { sum += e; sumSq += e * e; }
        double mean = sum / n;
        if (mean == 0) return 0.0;
        double variance = sumSq / n - mean * mean;
        return Math.sqrt(Math.max(0, variance)) / Math.abs(mean);
    }

    /**
     * Converts slope degrees to a 0–1 suitability factor.
     * 0° → 1.0 (fully suitable), threshold° → 0.0 (fully unsuitable).
     */
    public double suitabilityFactor(double slopeDeg, double thresholdDeg) {
        if (thresholdDeg <= 0) return 1.0;
        return Math.max(0.0, 1.0 - (slopeDeg / thresholdDeg));
    }
}
