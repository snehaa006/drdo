
package in.drdo.gis.service;

import in.drdo.gis.entity.DepthConfig;
import in.drdo.gis.entity.FrontageConfig;
import in.drdo.gis.entity.ThresholdConfig;
import in.drdo.gis.repository.DepthConfigRepository;
import in.drdo.gis.repository.FrontageConfigRepository;
import in.drdo.gis.repository.ThresholdConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ThresholdConfigRepository thresholdRepo;
    private final FrontageConfigRepository  frontageRepo;
    private final DepthConfigRepository     depthRepo;

    private static final String DEFAULT_KEY = "default";

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemConfig() {
        ThresholdConfig tc = thresholdRepo.findByConfigKey(DEFAULT_KEY).orElse(new ThresholdConfig());
        FrontageConfig  fc = frontageRepo.findByConfigKey(DEFAULT_KEY).orElse(new FrontageConfig());
        DepthConfig     dc = depthRepo.findByConfigKey(DEFAULT_KEY).orElse(new DepthConfig());
        return Map.of(
            "threshold", Map.of(
                "slopeThresholdDegrees", tc.getSlopeThresholdDegrees() != null ? tc.getSlopeThresholdDegrees() : 15.0,
                "roughnessThreshold",    tc.getRoughnessThreshold()    != null ? tc.getRoughnessThreshold()    : 0.3,
                "suitabilityMinScore",   tc.getSuitabilityMinScore()   != null ? tc.getSuitabilityMinScore()   : 0.5
            ),
            "frontage", Map.of(
                "minM",     fc.getMinFrontageM()     != null ? fc.getMinFrontageM()     : 10.0,
                "maxM",     fc.getMaxFrontageM()     != null ? fc.getMaxFrontageM()     : 5000.0,
                "defaultM", fc.getDefaultFrontageM() != null ? fc.getDefaultFrontageM() : 200.0,
                "stepM",    fc.getStepSizeM()        != null ? fc.getStepSizeM()        : 10.0
            ),
            "depth", Map.of(
                "minM",     dc.getMinDepthM()     != null ? dc.getMinDepthM()     : 5.0,
                "maxM",     dc.getMaxDepthM()     != null ? dc.getMaxDepthM()     : 2000.0,
                "defaultM", dc.getDefaultDepthM() != null ? dc.getDefaultDepthM() : 100.0,
                "stepM",    dc.getStepSizeM()     != null ? dc.getStepSizeM()     : 5.0
            )
        );
    }

    @Transactional
    public void updateSlopeThreshold(double degrees) {
        ThresholdConfig tc = thresholdRepo.findByConfigKey(DEFAULT_KEY)
            .orElse(ThresholdConfig.builder().configKey(DEFAULT_KEY).build());
        tc.setSlopeThresholdDegrees(degrees);
        thresholdRepo.save(tc);
        log.info("Updated slope threshold to {}°", degrees);
    }
}
