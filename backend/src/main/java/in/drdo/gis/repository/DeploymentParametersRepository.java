package in.drdo.gis.repository;

import in.drdo.gis.entity.DeploymentParameters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeploymentParametersRepository extends JpaRepository<DeploymentParameters, Long> {
    Optional<DeploymentParameters> findByDeploymentId(Long deploymentId);
}
