package in.drdo.gis.controller;

import in.drdo.gis.dto.TerrainAnalysisDto;
import in.drdo.gis.repository.TerrainAnalysisRepository;
import in.drdo.gis.repository.DeploymentRepository;
import in.drdo.gis.exception.DeploymentNotFoundException;
import in.drdo.gis.entity.TerrainAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/terrain")
@RequiredArgsConstructor
public class TerrainController {

    private final DeploymentRepository deploymentRepo;
    private final TerrainAnalysisRepository terrainRepo;

    @GetMapping("/deployment/{uid}")
    public ResponseEntity<TerrainAnalysisDto> getByDeployment(@PathVariable String uid) {
        var deployment = deploymentRepo.findByDeploymentUid(uid)
            .orElseThrow(() -> new DeploymentNotFoundException(uid));
        TerrainAnalysis ta = terrainRepo.findByDeploymentId(deployment.getId())
            .orElse(null);
        if (ta == null) return ResponseEntity.notFound().build();
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
        return ResponseEntity.ok(dto);
    }
}
