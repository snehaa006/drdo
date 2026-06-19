package in.drdo.gis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gis")
public class GisProperties {

    private Terrain terrain = new Terrain();
    private Geometry geometry = new Geometry();
    private Projection projection = new Projection();

    @Data
    public static class Terrain {
        private String dtedBasePath = "/data/terrain/dted";
        private String geotiffBasePath = "/data/terrain/geotiff";
        private int tileCacheSize = 100;
        private double slopeThresholdDefault = 15.0;
        private double elevationSampleDistanceM = 30.0;
    }

    @Data
    public static class Geometry {
        private int bezierSmoothingSteps = 64;
        private int ellipseSegments = 72;
        private int controlPointCount = 8;
        private double minFrontageM = 10.0;
        private double maxFrontageM = 5000.0;
        private double minDepthM = 5.0;
        private double maxDepthM = 2000.0;
    }

    @Data
    public static class Projection {
        private String defaultCrs = "EPSG:4326";
        private boolean utmZoneAutoDetect = true;
    }
}
