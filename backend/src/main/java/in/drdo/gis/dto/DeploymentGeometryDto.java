package in.drdo.gis.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class DeploymentGeometryDto {
    private Long id;
    private String geometryType;
    private String geojson;
    private String geomWkt;
    private Integer version;
    private Boolean isValid;
    private String validationMessage;
    private OffsetDateTime updatedAt;
}
