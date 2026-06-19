package in.drdo.gis.controller;

import in.drdo.gis.dto.ControlPointUpdateDto;
import in.drdo.gis.dto.DeploymentGeometryDto;
import in.drdo.gis.service.GeometryEditingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/geometry")
@RequiredArgsConstructor
public class GeometryEditingController {

    private final GeometryEditingService editingService;

    @PutMapping("/deployment/{uid}/edit")
    public ResponseEntity<DeploymentGeometryDto> editGeometry(
            @PathVariable String uid,
            @Valid @RequestBody ControlPointUpdateDto dto) {
        return ResponseEntity.ok(editingService.applyControlPointEdit(uid, dto));
    }
}