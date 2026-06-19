package in.drdo.gis.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class GeoUtilsTest {

    @Test
    void haversineReturnsApproximatelyCorrectDistance() {
        // Delhi to Mumbai ~ 1148 km
        double dist = GeoUtils.haversineM(28.6139, 77.2090, 19.0760, 72.8777);
        assertThat(dist).isCloseTo(1_148_000, offset(10_000.0));
    }

    @Test
    void haversineSamePointIsZero() {
        double dist = GeoUtils.haversineM(28.6, 77.2, 28.6, 77.2);
        assertThat(dist).isCloseTo(0.0, offset(0.001));
    }

    @Test
    void offsetLatLonShiftsNorthward() {
        double[] result = GeoUtils.offsetLatLon(28.6, 77.2, 0, 1000);
        assertThat(result[0]).isGreaterThan(28.6);   // lat increases going north
        assertThat(result[1]).isCloseTo(77.2, offset(0.0001)); // lon unchanged
    }

    @Test
    void offsetLatLonShiftsEastward() {
        double[] result = GeoUtils.offsetLatLon(28.6, 77.2, 1000, 0);
        assertThat(result[0]).isCloseTo(28.6, offset(0.0001)); // lat unchanged
        assertThat(result[1]).isGreaterThan(77.2);  // lon increases going east
    }

    @Test
    void bearingNorthIsZero() {
        double bearing = GeoUtils.bearingDeg(28.6, 77.2, 29.6, 77.2);
        assertThat(bearing).isCloseTo(0.0, offset(0.5));
    }

    @Test
    void bearingEastIs90() {
        double bearing = GeoUtils.bearingDeg(28.6, 77.2, 28.6, 78.2);
        assertThat(bearing).isCloseTo(90.0, offset(1.0));
    }
}