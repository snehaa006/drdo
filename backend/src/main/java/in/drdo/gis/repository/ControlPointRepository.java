package in.drdo.gis.repository;

import in.drdo.gis.entity.ControlPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ControlPointRepository extends JpaRepository<ControlPoint, Long> {
    List<ControlPoint> findByDeploymentIdOrderByPointIndexAsc(Long deploymentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ControlPoint cp WHERE cp.deployment.id = :deploymentId")
    void deleteByDeploymentId(@Param("deploymentId") Long deploymentId);
}
