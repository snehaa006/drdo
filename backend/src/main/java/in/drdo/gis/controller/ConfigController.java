
package in.drdo.gis.controller;

import in.drdo.gis.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(configService.getSystemConfig());
    }

    @PatchMapping("/slope-threshold")
    public ResponseEntity<Void> updateSlopeThreshold(@RequestParam double degrees) {
        configService.updateSlopeThreshold(degrees);
        return ResponseEntity.noContent().build();
    }
}
