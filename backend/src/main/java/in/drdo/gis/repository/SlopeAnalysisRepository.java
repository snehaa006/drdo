package in.drdo.gis.repository;

import in.drdo.gis.entity.SlopeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlopeAnalysisRepository extends JpaRepository<SlopeAnalysis, Long> {
    List<SlopeAnalysis> findByTerrainAnalysisId(Long terrainAnalysisId);
    void deleteByTerrainAnalysisId(Long terrainAnalysisId);
}
