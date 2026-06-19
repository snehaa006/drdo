package in.drdo.gis.mapper;

import in.drdo.gis.dto.*;
import in.drdo.gis.entity.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeploymentMapper {

    @Mapping(target = "status", expression = "java(deployment.getStatus().name())")
    DeploymentResponseDto toDto(Deployment deployment);

    @Mapping(target = "geometryType",
             expression = "java(geom.getGeometryType() != null ? geom.getGeometryType().name() : null)")
    DeploymentGeometryDto toDto(DeploymentGeometry geom);

    DeploymentParametersDto toDto(DeploymentParameters params);

    TerrainAnalysisDto toDto(TerrainAnalysis ta);

    ControlPointDto toDto(ControlPoint cp);
}