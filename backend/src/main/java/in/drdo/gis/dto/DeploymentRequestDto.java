package in.drdo.gis.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DeploymentRequestDto {
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double centerLat;

    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double centerLon;

    // No upper limit — any size is allowed. A small positive floor is kept only so
    // the geometry (ellipse / Bézier) can't be built from a zero or negative extent.
    @NotNull @DecimalMin("1.0")
    private Double frontageM;

    @NotNull @DecimalMin("1.0")
    private Double depthM;

    @DecimalMin("0.0") @DecimalMax("90.0")
    private Double slopeThresholdDegrees = 15.0;

    @DecimalMin("0.0") @DecimalMax("360.0")
    private Double headingDegrees;

    private Boolean terrainAdaptive = true;
    private Boolean bezierSmoothing = true;
    private String name;
}
