package in.drdo.gis.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class DeploymentResponseDto {
    private Long id;
    private String deploymentUid;
    private String name;
    private String status;
    private Double centerLat;
    private Double centerLon;
    private Double headingDegrees;
    private DeploymentParametersDto parameters;
    private DeploymentGeometryDto geometry;
    private TerrainAnalysisDto terrainAnalysis;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
