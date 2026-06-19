package in.drdo.gis.repository;

import in.drdo.gis.entity.TerrainAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TerrainAnalysisRepository extends JpaRepository<TerrainAnalysis, Long> {
    Optional<TerrainAnalysis> findByDeploymentId(Long deploymentId);
}
