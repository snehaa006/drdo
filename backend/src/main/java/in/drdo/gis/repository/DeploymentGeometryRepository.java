package in.drdo.gis.repository;

import in.drdo.gis.entity.DeploymentGeometry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentGeometryRepository extends JpaRepository<DeploymentGeometry, Long> {
    Optional<DeploymentGeometry> findByDeploymentId(Long deploymentId);

    @Query(value = """
        SELECT dg.* FROM deployment_geometry dg
        WHERE ST_Intersects(
            dg.geom,
            ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
        )
        """, nativeQuery = true)
    List<DeploymentGeometry> findGeometriesInBbox(
        @Param("minLat") double minLat,
        @Param("minLon") double minLon,
        @Param("maxLat") double maxLat,
        @Param("maxLon") double maxLon
    );
}
