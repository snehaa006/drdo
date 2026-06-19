package in.drdo.gis.repository;

import in.drdo.gis.entity.ThresholdConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThresholdConfigRepository extends JpaRepository<ThresholdConfig, Long> {
    Optional<ThresholdConfig> findByConfigKey(String configKey);
}
