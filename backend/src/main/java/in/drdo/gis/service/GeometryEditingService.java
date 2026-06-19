package in.drdo.gis.service;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.dto.ControlPointDto;
import in.drdo.gis.dto.ControlPointUpdateDto;
import in.drdo.gis.dto.DeploymentGeometryDto;
import in.drdo.gis.engine.BezierEngine;
import in.drdo.gis.entity.ControlPoint;
import in.drdo.gis.entity.Deployment;
import in.drdo.gis.entity.DeploymentGeometry;
import in.drdo.gis.exception.DeploymentNotFoundException;
import in.drdo.gis.repository.ControlPointRepository;
import in.drdo.gis.repository.DeploymentGeometryRepository;
import in.drdo.gis.repository.DeploymentRepository;
import in.drdo.gis.util.GeoJsonWriter;
import in.drdo.gis.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeometryEditingService {

    private final DeploymentRepository deploymentRepo;
    private final DeploymentGeometryRepository geomRepo;
    private final ControlPointRepository cpRepo;
    private final PolygonValidationService validationService;
    private final GeoJsonWriter geoJsonWriter;
    private final GisProperties gisProperties;

    @Transactional
    public DeploymentGeometryDto applyControlPointEdit(String uid, ControlPointUpdateDto updateDto) {
        Deployment deployment = deploymentRepo.findByDeploymentUid(uid)
            .orElseThrow(() -> new DeploymentNotFoundException(uid));

        cpRepo.deleteByDeploymentId(deployment.getId());

        List<ControlPoint> updatedCps = updateDto.getControlPoints().stream()
            .map(dto -> buildControlPoint(dto, deployment))
            .map(cpRepo::save)
            .collect(Collectors.toList());

        BezierEngine bezierEngine = new BezierEngine();
        int steps = gisProperties.getGeometry().getBezierSmoothingSteps();
        Polygon raw = bezierEngine.buildBezierPolygon(updatedCps, steps);

        PolygonValidationService.ValidationResult vr = validationService.validate(raw);
        Polygon finalPoly = vr.repaired() != null ? vr.repaired() : raw;

        String geojson = geoJsonWriter.toFeatureJson(finalPoly,
            Map.of("deploymentUid", uid, "type", "BEZIER_CUSTOM", "edited", true));
        String wkt = new WKTWriter().write(finalPoly);

        DeploymentGeometry dg = geomRepo.findByDeploymentId(deployment.getId())
            .orElse(DeploymentGeometry.builder().deployment(deployment).build());
        dg.setGeom(finalPoly);
        dg.setGeomWkt(wkt);
        dg.setGeojson(geojson);
        dg.setGeometryType(DeploymentGeometry.GeometryType.BEZIER_CUSTOM);
        dg.setVersion(dg.getVersion() != null ? dg.getVersion() + 1 : 1);
        dg.setIsValid(finalPoly.isValid());
        dg.setValidationMessage(vr.valid() ? null : vr.message());
        dg = geomRepo.save(dg);

        deployment.setStatus(Deployment.DeploymentStatus.EDITED);
        deploymentRepo.save(deployment);

        DeploymentGeometryDto dto = new DeploymentGeometryDto();
        dto.setId(dg.getId());
        dto.setGeometryType(dg.getGeometryType().name());
        dto.setGeojson(dg.getGeojson());
        dto.setGeomWkt(dg.getGeomWkt());
        dto.setVersion(dg.getVersion());
        dto.setIsValid(dg.getIsValid());
        dto.setValidationMessage(dg.getValidationMessage());
        dto.setUpdatedAt(dg.getUpdatedAt());
        return dto;
    }

    private ControlPoint buildControlPoint(ControlPointDto dto, Deployment deployment) {
        return ControlPoint.builder()
            .deployment(deployment)
            .pointIndex(dto.getPointIndex())
            .pointType(ControlPoint.PointType.BEZIER_HANDLE)
            .lat(dto.getLat())
            .lon(dto.getLon())
            .geom(GeoUtils.createPoint(dto.getLon(), dto.getLat()))
            .handleLat1(dto.getHandleLat1())
            .handleLon1(dto.getHandleLon1())
            .handleLat2(dto.getHandleLat2())
            .handleLon2(dto.getHandleLon2())
            .isLocked(dto.getIsLocked() != null && dto.getIsLocked())
            .build();
    }
}