
package in.drdo.gis.repository;

import in.drdo.gis.entity.DepthConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DepthConfigRepository extends JpaRepository<DepthConfig, Long> {
    Optional<DepthConfig> findByConfigKey(String configKey);
}
