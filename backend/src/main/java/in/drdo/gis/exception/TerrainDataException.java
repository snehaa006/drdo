package in.drdo.gis.exception;

public class TerrainDataException extends RuntimeException {
    public TerrainDataException(String message) { super(message); }
    public TerrainDataException(String message, Throwable cause) { super(message, cause); }
}
