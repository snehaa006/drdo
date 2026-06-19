
package in.drdo.gis.service;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import static org.assertj.core.api.Assertions.assertThat;

class PolygonValidationServiceTest {

    private final PolygonValidationService svc = new PolygonValidationService();
    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void validSquarePassesValidation() {
        Polygon square = gf.createPolygon(new Coordinate[]{
            new Coordinate(0,0), new Coordinate(0,1),
            new Coordinate(1,1), new Coordinate(1,0), new Coordinate(0,0)
        });
        var result = svc.validate(square);
        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @Test
    void selfIntersectingPolygonDetected() {
        // Bowtie polygon — self-intersecting
        Polygon bowtie = gf.createPolygon(new Coordinate[]{
            new Coordinate(0,0), new Coordinate(1,1),
            new Coordinate(1,0), new Coordinate(0,1), new Coordinate(0,0)
        });
        assertThat(svc.hasSelfIntersection(bowtie)).isTrue();
    }

    @Test
    void repairablePolygonGetsFixed() {
        // A slightly invalid polygon that buffer(0) should fix
        Polygon bowtie = gf.createPolygon(new Coordinate[]{
            new Coordinate(0,0), new Coordinate(1,1),
            new Coordinate(1,0), new Coordinate(0,1), new Coordinate(0,0)
        });
        var result = svc.validate(bowtie);
        // Should not be valid originally
        assertThat(result.valid()).isFalse();
        // But should produce a repaired version
        assertThat(result.repaired()).isNotNull();
        assertThat(result.repaired().isValid()).isTrue();
    }
}
