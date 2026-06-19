package in.drdo.gis.config;

import in.drdo.gis.entity.ThresholdConfig;
import in.drdo.gis.repository.ThresholdConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ThresholdConfigRepository thresholdRepo;

    @Override
    public void run(ApplicationArguments args) {
        if (thresholdRepo.findByConfigKey("default").isEmpty()) {
            thresholdRepo.save(ThresholdConfig.builder()
                .configKey("default")
                .slopeThresholdDegrees(15.0)
                .roughnessThreshold(0.3)
                .suitabilityMinScore(0.5)
                .description("Default terrain threshold configuration")
                .build());
            log.info("Seeded default ThresholdConfig");
        }
    }
}