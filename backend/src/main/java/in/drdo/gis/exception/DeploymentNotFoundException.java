package in.drdo.gis.exception;

public class DeploymentNotFoundException extends RuntimeException {
    public DeploymentNotFoundException(String uid) {
        super("Deployment not found: " + uid);
    }
    public DeploymentNotFoundException(Long id) {
        super("Deployment not found with id: " + id);
    }
}
