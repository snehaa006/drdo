package in.drdo.gis.repository;

import in.drdo.gis.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Optional<Deployment> findByDeploymentUid(String deploymentUid);

    List<Deployment> findByStatusOrderByCreatedAtDesc(Deployment.DeploymentStatus status);

    @Query(value = """
        SELECT d.* FROM deployment d
        WHERE ST_DWithin(
            d.center_geom::geography,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :radiusMeters
        )
        ORDER BY ST_Distance(
            d.center_geom::geography,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
        )
        """, nativeQuery = true)
    List<Deployment> findDeploymentsWithinRadius(
        @Param("lat") double lat,
        @Param("lon") double lon,
        @Param("radiusMeters") double radiusMeters
    );

    @Query(value = """
        SELECT d.* FROM deployment d
        WHERE ST_Within(
            d.center_geom,
            ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
        )
        """, nativeQuery = true)
    List<Deployment> findDeploymentsInBbox(
        @Param("minLat") double minLat,
        @Param("minLon") double minLon,
        @Param("maxLat") double maxLat,
        @Param("maxLon") double maxLon
    );

    boolean existsByDeploymentUid(String deploymentUid);
}
