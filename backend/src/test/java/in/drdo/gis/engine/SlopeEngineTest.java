
package in.drdo.gis.engine;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SlopeEngineTest {

    private final SlopeEngine engine = new SlopeEngine();

    @Test
    void flatGridProducesZeroSlope() {
        double[][] flat = {
            {100, 100, 100},
            {100, 100, 100},
            {100, 100, 100}
        };
        assertThat(engine.slopeFromGrid3x3(flat, 30)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void uniformEastwardSlopeComputedCorrectly() {
        // 1 metre rise per 30 metres east → ~1.9° slope
        double[][] grid = {
            {100, 101, 102},
            {100, 101, 102},
            {100, 101, 102}
        };
        double slope = engine.slopeFromGrid3x3(grid, 30);
        assertThat(slope).isGreaterThan(1.0).isLessThan(3.0);
    }

    @Test
    void suitabilityFactorAtZeroSlopeIsOne() {
        assertThat(engine.suitabilityFactor(0, 15)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void suitabilityFactorAtThresholdIsZero() {
        assertThat(engine.suitabilityFactor(15, 15)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void roughnessOfConstantArrayIsZero() {
        double[] uniform = {100, 100, 100, 100};
        assertThat(engine.roughness(uniform)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void roughnessOfVariedArrayIsPositive() {
        double[] varied = {80, 100, 120, 90, 110};
        assertThat(engine.roughness(varied)).isGreaterThan(0.0);
    }
}
