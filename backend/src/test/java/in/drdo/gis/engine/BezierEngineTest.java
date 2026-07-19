package in.drdo.gis.engine;

import in.drdo.gis.entity.ControlPoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BezierEngineTest {

    private final BezierEngine engine = new BezierEngine();

    @Test
    void buildEllipseProducesValidPolygon() {
        Polygon p = engine.buildEllipse(28.6, 77.2, 250, 125, 0, 72);
        assertThat(p).isNotNull();
        assertThat(p.isValid()).isTrue();
        assertThat(p.getArea()).isGreaterThan(0);
    }

    @Test
    void buildBezierPolygonFromControlPoints() {
        List<ControlPoint> cps = List.of(
            cp(0, 28.602, 77.198, 28.602, 77.196, 28.602, 77.200),
            cp(1, 28.600, 77.202, 28.601, 77.202, 28.599, 77.202),
            cp(2, 28.598, 77.198, 28.598, 77.200, 28.598, 77.196),
            cp(3, 28.600, 77.194, 28.601, 77.194, 28.599, 77.194)
        );
        Polygon poly = engine.buildBezierPolygon(cps, 32);
        assertThat(poly).isNotNull();
        assertThat(poly.getNumPoints()).isGreaterThan(10);
    }

    @Test
    void smoothHandlesGeneratedForAllAnchors() {
        double[] lats = {28.602, 28.600, 28.598, 28.600};
        double[] lons = {77.198, 77.202, 77.198, 77.194};
        double[][] handles = engine.generateSmoothHandles(lats, lons, 0.4);
        assertThat(handles).hasNumberOfRows(4);
        for (double[] h : handles) assertThat(h).hasSize(4);
    }

    private ControlPoint cp(int idx, double lat, double lon,
                             double h1lat, double h1lon,
                             double h2lat, double h2lon) {
        return ControlPoint.builder()
            .pointIndex(idx).lat(lat).lon(lon)
            .handleLat1(h1lat).handleLon1(h1lon)
            .handleLat2(h2lat).handleLon2(h2lon)
            .build();
    }
}