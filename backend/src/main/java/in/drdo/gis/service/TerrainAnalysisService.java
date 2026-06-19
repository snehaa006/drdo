package in.drdo.gis.service;

import in.drdo.gis.dto.TerrainAnalysisDto;
import in.drdo.gis.engine.TerrainEngine;
import in.drdo.gis.entity.*;
import in.drdo.gis.exception.DeploymentNotFoundException;
import in.drdo.gis.repository.*;
import in.drdo.gis.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerrainAnalysisService {

    private final DeploymentRepository deploymentRepo;
    private final DeploymentParametersRepository paramsRepo;
    private final TerrainAnalysisRepository terrainRepo;
    private final SlopeAnalysisRepository slopeRepo;
    private final TerrainEngine terrainEngine;

    @Transactional
    public TerrainAnalysisDto recomputeForDeployment(String uid) {
        Deployment deployment = deploymentRepo.findByDeploymentUid(uid)
            .orElseThrow(() -> new DeploymentNotFoundException(uid));
        DeploymentParameters params = paramsRepo.findByDeploymentId(deployment.getId())
            .orElseThrow(() -> new IllegalStateException("No parameters for deployment: " + uid));

        double heading = params.getHeadingDegrees() != null ? params.getHeadingDegrees() : 0.0;

        TerrainEngine.TerrainResult tr = terrainEngine.analyse(
            deployment.getCenterLat(), deployment.getCenterLon(),
            params.getFrontageM(), params.getDepthM(), heading);

        TerrainAnalysis existing = terrainRepo.findByDeploymentId(deployment.getId()).orElse(null);
        if (existing != null) {
            slopeRepo.deleteByTerrainAnalysisId(existing.getId());
            terrainRepo.delete(existing);
        }

        TerrainAnalysis ta = TerrainAnalysis.builder()
            .deployment(deployment)
            .meanElevationM(tr.meanElevationM()).minElevationM(tr.minElevationM())
            .maxElevationM(tr.maxElevationM()).meanSlopeDegrees(tr.meanSlopeDegrees())
            .maxSlopeDegrees(tr.maxSlopeDegrees()).terrainRoughness(tr.terrainRoughness())
            .suitabilityScore(tr.suitabilityScore()).isPlanar(tr.isPlanar())
            .sampleCount(tr.sampleCount()).build();
        ta = terrainRepo.save(ta);

        final TerrainAnalysis savedTa = ta;
        tr.slopeSamples().forEach(s -> slopeRepo.save(SlopeAnalysis.builder()
            .terrainAnalysis(savedTa)
            .samplePoint(GeoUtils.createPoint(s.lon(), s.lat()))
            .elevationM(s.elevationM()).slopeDegrees(s.slopeDegrees())
            .aspectDegrees(s.aspectDegrees()).slopeNs(s.slopeNs()).slopeEw(s.slopeEw())
            .build()));

        TerrainAnalysisDto dto = new TerrainAnalysisDto();
        dto.setId(ta.getId());
        dto.setMeanElevationM(ta.getMeanElevationM());
        dto.setMinElevationM(ta.getMinElevationM());
        dto.setMaxElevationM(ta.getMaxElevationM());
        dto.setMeanSlopeDegrees(ta.getMeanSlopeDegrees());
        dto.setMaxSlopeDegrees(ta.getMaxSlopeDegrees());
        dto.setTerrainRoughness(ta.getTerrainRoughness());
        dto.setSuitabilityScore(ta.getSuitabilityScore());
        dto.setIsPlanar(ta.getIsPlanar());
        dto.setSampleCount(ta.getSampleCount());
        dto.setComputedAt(ta.getComputedAt());
        return dto;
    }
}