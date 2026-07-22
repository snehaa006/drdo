package in.drdo.gis.service;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.dto.*;
import in.drdo.gis.engine.*;
import in.drdo.gis.entity.*;
import in.drdo.gis.exception.DeploymentNotFoundException;
import in.drdo.gis.repository.*;
import in.drdo.gis.util.GeoJsonWriter;
import in.drdo.gis.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepo;
    private final DeploymentParametersRepository paramsRepo;
    private final DeploymentGeometryRepository geomRepo;
    private final TerrainAnalysisRepository terrainRepo;
    private final SlopeAnalysisRepository slopeRepo;
    private final ControlPointRepository cpRepo;
    private final TerrainEngine terrainEngine;
    private final GeometryEngine geometryEngine;
    private final BezierEngine bezierEngine;
    private final GeoJsonWriter geoJsonWriter;
    private final GisProperties gisProperties;

    @Transactional
    public DeploymentResponseDto createDeployment(DeploymentRequestDto req) {
        log.info("Creating deployment at {}/{}", req.getCenterLat(), req.getCenterLon());

        // 1 - persist deployment
        Deployment deployment = Deployment.builder()
            .name(req.getName() != null ? req.getName() : "Deployment " + System.currentTimeMillis())
            .centerLat(req.getCenterLat())
            .centerLon(req.getCenterLon())
            .centerGeom(GeoUtils.createPoint(req.getCenterLon(), req.getCenterLat()))
            .headingDegrees(req.getHeadingDegrees() != null ? req.getHeadingDegrees() : 0.0)
            .status(Deployment.DeploymentStatus.COMPUTING)
            .createdBy("system")
            .build();
        deployment = deploymentRepo.save(deployment);

        // 2 - persist params
        DeploymentParameters params = DeploymentParameters.builder()
            .deployment(deployment)
            .frontageM(req.getFrontageM())
            .depthM(req.getDepthM())
            .slopeThresholdDegrees(req.getSlopeThresholdDegrees())
            .headingDegrees(req.getHeadingDegrees())
            .terrainAdaptive(req.getTerrainAdaptive())
            .bezierSmoothing(req.getBezierSmoothing())
            .build();
        paramsRepo.save(params);

        // 3 - terrain analysis
        double heading = req.getHeadingDegrees() != null ? req.getHeadingDegrees() : 0.0;
        double slopeThreshold = req.getSlopeThresholdDegrees() != null
            ? req.getSlopeThresholdDegrees()
            : gisProperties.getTerrain().getSlopeThresholdDefault();
        TerrainEngine.TerrainResult tr = terrainEngine.analyse(
            req.getCenterLat(), req.getCenterLon(),
            req.getFrontageM(), req.getDepthM(), heading, slopeThreshold);

        TerrainAnalysis ta = TerrainAnalysis.builder()
            .deployment(deployment)
            .meanElevationM(tr.meanElevationM())
            .minElevationM(tr.minElevationM())
            .maxElevationM(tr.maxElevationM())
            .meanSlopeDegrees(tr.meanSlopeDegrees())
            .maxSlopeDegrees(tr.maxSlopeDegrees())
            .terrainRoughness(tr.terrainRoughness())
            .suitabilityScore(tr.suitabilityScore())
            .isPlanar(tr.isPlanar())
            .sampleCount(tr.sampleCount())
            .build();
        ta = terrainRepo.save(ta);

        // slope samples
        for (TerrainEngine.SlopeSample s : tr.slopeSamples()) {
            SlopeAnalysis sa = SlopeAnalysis.builder()
                .terrainAnalysis(ta)
                .samplePoint(GeoUtils.createPoint(s.lon(), s.lat()))
                .elevationM(s.elevationM())
                .slopeDegrees(s.slopeDegrees())
                .aspectDegrees(s.aspectDegrees())
                .slopeNs(s.slopeNs())
                .slopeEw(s.slopeEw())
                .build();
            slopeRepo.save(sa);
        }

        // 4 - directional factors
        double[] factors = new double[8];
        if (Boolean.TRUE.equals(req.getTerrainAdaptive())) {
            factors = terrainEngine.directionalSlopeFactors(
                req.getCenterLat(), req.getCenterLon(),
                Math.max(req.getFrontageM(), req.getDepthM()) / 2.0,
                req.getSlopeThresholdDegrees());
        } else {
            java.util.Arrays.fill(factors, 1.0);
        }

        // 5 - geometry generation
        GeometryEngine.GeometryResult gr = geometryEngine.generate(
            req.getCenterLat(), req.getCenterLon(),
            req.getFrontageM(), req.getDepthM(),
            heading, tr.isPlanar(), factors);

        String geojson = geoJsonWriter.toFeatureJson(gr.polygon(),
            java.util.Map.of("deploymentUid", deployment.getDeploymentUid(),
                             "type", gr.geometryType().name()));
        String wkt = new WKTWriter().write(gr.polygon());

        DeploymentGeometry dg = DeploymentGeometry.builder()
            .deployment(deployment)
            .geometryType(gr.geometryType())
            .geom(gr.polygon())
            .geomWkt(wkt)
            .geojson(geojson)
            .isValid(gr.isValid())
            .validationMessage(gr.validationMessage())
            .build();
        geomRepo.save(dg);

        // 6 - control points
        for (ControlPoint cp : gr.controlPoints()) {
            cp.setDeployment(deployment);
            cp.setGeom(GeoUtils.createPoint(cp.getLon(), cp.getLat()));
            cpRepo.save(cp);
        }

        // 7 - mark ready
        deployment.setStatus(Deployment.DeploymentStatus.READY);
        deployment = deploymentRepo.save(deployment);

        return toResponseDto(deployment, params, dg, ta);
    }

    @Transactional(readOnly = true)
    public DeploymentResponseDto getDeployment(String uid) {
        Deployment d = deploymentRepo.findByDeploymentUid(uid)
            .orElseThrow(() -> new DeploymentNotFoundException(uid));
        DeploymentParameters p = paramsRepo.findByDeploymentId(d.getId()).orElse(null);
        DeploymentGeometry g   = geomRepo.findByDeploymentId(d.getId()).orElse(null);
        TerrainAnalysis ta     = terrainRepo.findByDeploymentId(d.getId()).orElse(null);
        return toResponseDto(d, p, g, ta);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponseDto> listDeployments() {
        return deploymentRepo.findAll().stream()
            .map(d -> {
                DeploymentParameters p = paramsRepo.findByDeploymentId(d.getId()).orElse(null);
                DeploymentGeometry g   = geomRepo.findByDeploymentId(d.getId()).orElse(null);
                TerrainAnalysis ta     = terrainRepo.findByDeploymentId(d.getId()).orElse(null);
                return toResponseDto(d, p, g, ta);
            }).collect(Collectors.toList());
    }

    @Transactional
    public DeploymentResponseDto updateControlPoints(String uid, ControlPointUpdateDto dto) {
        Deployment d = deploymentRepo.findByDeploymentUid(uid)
            .orElseThrow(() -> new DeploymentNotFoundException(uid));

        cpRepo.deleteByDeploymentId(d.getId());
        List<ControlPoint> savedCps = dto.getControlPoints().stream().map(cpDto -> {
            ControlPoint cp = ControlPoint.builder()
                .deployment(d)
                .pointIndex(cpDto.getPointIndex())
                .pointType(ControlPoint.PointType.BEZIER_HANDLE)
                .lat(cpDto.getLat())
                .lon(cpDto.getLon())
                .geom(GeoUtils.createPoint(cpDto.getLon(), cpDto.getLat()))
                .handleLat1(cpDto.getHandleLat1())
                .handleLon1(cpDto.getHandleLon1())
                .handleLat2(cpDto.getHandleLat2())
                .handleLon2(cpDto.getHandleLon2())
                .isLocked(cpDto.getIsLocked() != null && cpDto.getIsLocked())
                .build();
            return cpRepo.save(cp);
        }).collect(Collectors.toList());

        // Regenerate geometry from updated control points
        DeploymentGeometry dg = geomRepo.findByDeploymentId(d.getId()).orElse(new DeploymentGeometry());
        dg.setDeployment(d);

        int steps = gisProperties.getGeometry().getBezierSmoothingSteps();
        Polygon poly = bezierEngine.buildBezierPolygon(savedCps, steps);
        String geojson = geoJsonWriter.toFeatureJson(poly,
            java.util.Map.of("deploymentUid", uid, "type", "BEZIER_CUSTOM"));
        dg.setGeom(poly);
        dg.setGeomWkt(new WKTWriter().write(poly));
        dg.setGeojson(geojson);
        dg.setGeometryType(DeploymentGeometry.GeometryType.BEZIER_CUSTOM);
        dg.setVersion(dg.getVersion() != null ? dg.getVersion() + 1 : 1);
        dg.setIsValid(poly.isValid());
        geomRepo.save(dg);

        d.setStatus(Deployment.DeploymentStatus.EDITED);
        deploymentRepo.save(d);

        DeploymentParameters p = paramsRepo.findByDeploymentId(d.getId()).orElse(null);
        TerrainAnalysis ta     = terrainRepo.findByDeploymentId(d.getId()).orElse(null);
        return toResponseDto(d, p, dg, ta);
    }

    @Transactional
    public void deleteDeployment(String uid) {
        Deployment d = deploymentRepo.findByDeploymentUid(uid)
            .orElseThrow(() -> new DeploymentNotFoundException(uid));
        deploymentRepo.delete(d);
    }

    // ------------------------------------------------------------------ //

    private DeploymentResponseDto toResponseDto(Deployment d, DeploymentParameters p,
                                                 DeploymentGeometry g, TerrainAnalysis ta) {
        DeploymentResponseDto dto = new DeploymentResponseDto();
        dto.setId(d.getId());
        dto.setDeploymentUid(d.getDeploymentUid());
        dto.setName(d.getName());
        dto.setStatus(d.getStatus().name());
        dto.setCenterLat(d.getCenterLat());
        dto.setCenterLon(d.getCenterLon());
        dto.setHeadingDegrees(d.getHeadingDegrees());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setUpdatedAt(d.getUpdatedAt());

        if (p != null) {
            DeploymentParametersDto pd = new DeploymentParametersDto();
            pd.setId(p.getId()); pd.setFrontageM(p.getFrontageM());
            pd.setDepthM(p.getDepthM());
            pd.setSlopeThresholdDegrees(p.getSlopeThresholdDegrees());
            pd.setHeadingDegrees(p.getHeadingDegrees());
            pd.setTerrainAdaptive(p.getTerrainAdaptive());
            pd.setBezierSmoothing(p.getBezierSmoothing());
            pd.setCreatedAt(p.getCreatedAt());
            dto.setParameters(pd);
        }

        if (g != null) {
            DeploymentGeometryDto gd = new DeploymentGeometryDto();
            gd.setId(g.getId());
            gd.setGeometryType(g.getGeometryType() != null ? g.getGeometryType().name() : null);
            gd.setGeojson(g.getGeojson());
            gd.setGeomWkt(g.getGeomWkt());
            gd.setVersion(g.getVersion());
            gd.setIsValid(g.getIsValid());
            gd.setValidationMessage(g.getValidationMessage());
            gd.setUpdatedAt(g.getUpdatedAt());
            dto.setGeometry(gd);
        }

        if (ta != null) {
            TerrainAnalysisDto tad = new TerrainAnalysisDto();
            tad.setId(ta.getId());
            tad.setMeanElevationM(ta.getMeanElevationM());
            tad.setMinElevationM(ta.getMinElevationM());
            tad.setMaxElevationM(ta.getMaxElevationM());
            tad.setMeanSlopeDegrees(ta.getMeanSlopeDegrees());
            tad.setMaxSlopeDegrees(ta.getMaxSlopeDegrees());
            tad.setTerrainRoughness(ta.getTerrainRoughness());
            tad.setSuitabilityScore(ta.getSuitabilityScore());
            tad.setIsPlanar(ta.getIsPlanar());
            tad.setSampleCount(ta.getSampleCount());
            tad.setComputedAt(ta.getComputedAt());
            dto.setTerrainAnalysis(tad);
        }

        // Control points drive the "Edit Geometry" tool in the UI; they must be
        // delivered with every deployment so the client has draggable handles.
        List<ControlPointDto> cps = cpRepo.findByDeploymentIdOrderByPointIndexAsc(d.getId())
            .stream().map(this::toControlPointDto).collect(Collectors.toList());
        dto.setControlPoints(cps);

        return dto;
    }

    private ControlPointDto toControlPointDto(ControlPoint cp) {
        ControlPointDto dto = new ControlPointDto();
        dto.setId(cp.getId());
        dto.setPointIndex(cp.getPointIndex());
        dto.setPointType(cp.getPointType() != null ? cp.getPointType().name() : null);
        dto.setLat(cp.getLat());
        dto.setLon(cp.getLon());
        dto.setHandleLat1(cp.getHandleLat1());
        dto.setHandleLon1(cp.getHandleLon1());
        dto.setHandleLat2(cp.getHandleLat2());
        dto.setHandleLon2(cp.getHandleLon2());
        dto.setIsLocked(cp.getIsLocked());
        return dto;
    }
}
