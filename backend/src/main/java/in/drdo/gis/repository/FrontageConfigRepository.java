
package in.drdo.gis.repository;

import in.drdo.gis.entity.FrontageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FrontageConfigRepository extends JpaRepository<FrontageConfig, Long> {
    Optional<FrontageConfig> findByConfigKey(String configKey);
}
