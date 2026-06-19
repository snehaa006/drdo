package in.drdo.gis.exception;

public class GeometryException extends RuntimeException {
    public GeometryException(String message) { super(message); }
    public GeometryException(String message, Throwable cause) { super(message, cause); }
}
