package in.drdo.gis.engine;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectionServiceTest {

    private final ProjectionService svc = new ProjectionService();

    @Test
    void latLonToUtmAndBackRoundTrips() {
        double lat = 28.6139, lon = 77.2090;
        double[] utm = svc.latLonToUtm(lat, lon);
        assertThat(utm[2]).isEqualTo(43); // UTM zone 43N for Delhi

        double[] back = svc.utmToLatLon(utm[0], utm[1], (int) utm[2], true);
        assertThat(back[0]).isCloseTo(lat, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(back[1]).isCloseTo(lon, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void metresPerDegreeApproxCorrect() {
        double mLat = svc.metresPerDegreeLat(0);
        double mLon = svc.metresPerDegreeLon(0);
        assertThat(mLat).isCloseTo(110574, org.assertj.core.data.Offset.offset(500.0));
        assertThat(mLon).isCloseTo(111320, org.assertj.core.data.Offset.offset(500.0));
    }
}