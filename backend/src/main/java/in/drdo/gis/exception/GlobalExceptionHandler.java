package in.drdo.gis.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeploymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DeploymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(TerrainDataException.class)
    public ResponseEntity<ErrorResponse> handleTerrainData(TerrainDataException ex) {
        log.error("Terrain data error", ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse("TERRAIN_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(GeometryException.class)
    public ResponseEntity<ErrorResponse> handleGeometry(GeometryException ex) {
        log.error("Geometry error", ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse("GEOMETRY_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(e -> {
            String field = ((FieldError) e).getField();
            errors.put(field, e.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", "Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    public record ErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors,
        OffsetDateTime timestamp
    ) {
        public ErrorResponse(String code, String message) {
            this(code, message, null, OffsetDateTime.now());
        }
        public ErrorResponse(String code, String message, Map<String, String> fieldErrors) {
            this(code, message, fieldErrors, OffsetDateTime.now());
        }
    }
}
