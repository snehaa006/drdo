package in.drdo.gis.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DeploymentRequestDto {
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double centerLat;

    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double centerLon;

    @NotNull @DecimalMin("10.0") @DecimalMax("5000.0")
    private Double frontageM;

    @NotNull @DecimalMin("5.0") @DecimalMax("2000.0")
    private Double depthM;

    @DecimalMin("0.0") @DecimalMax("90.0")
    private Double slopeThresholdDegrees = 15.0;

    @DecimalMin("0.0") @DecimalMax("360.0")
    private Double headingDegrees;

    private Boolean terrainAdaptive = true;
    private Boolean bezierSmoothing = true;
    private String name;
}
