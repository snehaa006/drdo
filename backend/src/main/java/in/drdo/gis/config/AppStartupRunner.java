package in.drdo.gis.config;

import in.drdo.gis.service.TileManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppStartupRunner implements ApplicationRunner {

    private final TileManagementService tileManagementService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Scanning for offline GeoTIFF tiles on startup...");
        try {
            int count = tileManagementService.scanAndRegisterGeoTiffs();
            log.info("Startup tile scan complete. Registered {} new tiles.", count);
        } catch (Exception ex) {
            log.warn("Tile scan on startup failed (non-fatal): {}", ex.getMessage());
        }
    }
}