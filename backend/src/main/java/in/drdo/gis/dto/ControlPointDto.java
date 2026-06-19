package in.drdo.gis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ControlPointDto {
    private Long id;
    private Integer pointIndex;
    private String pointType;
    @NotNull private Double lat;
    @NotNull private Double lon;
    private Double handleLat1;
    private Double handleLon1;
    private Double handleLat2;
    private Double handleLon2;
    private Boolean isLocked;
}
