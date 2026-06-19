package in.drdo.gis.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class DeploymentParametersDto {
    private Long id;
    private Double frontageM;
    private Double depthM;
    private Double slopeThresholdDegrees;
    private Double headingDegrees;
    private Boolean terrainAdaptive;
    private Boolean bezierSmoothing;
    private OffsetDateTime createdAt;
}
