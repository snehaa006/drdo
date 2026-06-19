package in.drdo.gis.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class TerrainAnalysisDto {
    private Long id;
    private Double meanElevationM;
    private Double minElevationM;
    private Double maxElevationM;
    private Double meanSlopeDegrees;
    private Double maxSlopeDegrees;
    private Double terrainRoughness;
    private Double suitabilityScore;
    private Boolean isPlanar;
    private Integer sampleCount;
    private OffsetDateTime computedAt;
}
