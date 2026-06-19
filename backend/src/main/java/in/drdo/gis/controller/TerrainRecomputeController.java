package in.drdo.gis.controller;

import in.drdo.gis.dto.TerrainAnalysisDto;
import in.drdo.gis.service.TerrainAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/terrain")
@RequiredArgsConstructor
public class TerrainRecomputeController {

    private final TerrainAnalysisService terrainAnalysisService;

    @PostMapping("/deployment/{uid}/recompute")
    public ResponseEntity<TerrainAnalysisDto> recompute(@PathVariable String uid) {
        return ResponseEntity.ok(terrainAnalysisService.recomputeForDeployment(uid));
    }
}