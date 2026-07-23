package in.drdo.gis.controller;

import in.drdo.gis.dto.*;
import in.drdo.gis.service.DeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @PostMapping
    public ResponseEntity<DeploymentResponseDto> create(
            @Valid @RequestBody DeploymentRequestDto request) {
        DeploymentResponseDto response = deploymentService.createDeployment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DeploymentResponseDto>> list() {
        return ResponseEntity.ok(deploymentService.listDeployments());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<DeploymentResponseDto> get(@PathVariable String uid) {
        return ResponseEntity.ok(deploymentService.getDeployment(uid));
    }

    @GetMapping("/{uid}/geojson")
    public ResponseEntity<String> getGeoJson(@PathVariable String uid) {
        DeploymentResponseDto d = deploymentService.getDeployment(uid);
        String geojson = d.getGeometry() != null ? d.getGeometry().getGeojson() : "{}";
        return ResponseEntity.ok()
            .header("Content-Type", "application/geo+json")
            .body(geojson);
    }

    @PutMapping("/{uid}/control-points")
    public ResponseEntity<DeploymentResponseDto> updateControlPoints(
            @PathVariable String uid,
            @Valid @RequestBody ControlPointUpdateDto dto) {
        return ResponseEntity.ok(deploymentService.updateControlPoints(uid, dto));
    }

    /** Live terrain analysis for edited-but-unsaved control points (nothing is persisted). */
    @PostMapping("/{uid}/terrain-preview")
    public ResponseEntity<TerrainAnalysisDto> previewTerrain(
            @PathVariable String uid,
            @Valid @RequestBody ControlPointUpdateDto dto) {
        return ResponseEntity.ok(deploymentService.previewTerrain(uid, dto));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable String uid) {
        deploymentService.deleteDeployment(uid);
        return ResponseEntity.noContent().build();
    }
}
