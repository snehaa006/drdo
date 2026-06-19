package in.drdo.gis.service;

import in.drdo.gis.exception.GeometryException;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PolygonValidationService {

    public record ValidationResult(boolean valid, String message, Polygon repaired) {}

    public ValidationResult validate(Polygon polygon) {
        if (polygon == null) return new ValidationResult(false, "Polygon is null", null);

        IsValidOp op = new IsValidOp(polygon);
        if (op.isValid()) return new ValidationResult(true, null, polygon);

        TopologyValidationError err = op.getValidationError();
        log.warn("Invalid polygon: {} at {}", err.getMessage(), err.getCoordinate());

        Polygon repaired = attemptRepair(polygon);
        if (repaired != null && repaired.isValid()) {
            return new ValidationResult(false, err.getMessage(), repaired);
        }
        return new ValidationResult(false, err.getMessage(), null);
    }

    public Polygon repair(Polygon polygon) {
        Polygon r = attemptRepair(polygon);
        if (r == null) throw new GeometryException("Cannot repair polygon: " + new IsValidOp(polygon).getValidationError().getMessage());
        return r;
    }

    private Polygon attemptRepair(Polygon p) {
        try {
            Geometry buffered = p.buffer(0);
            if (buffered instanceof Polygon poly && poly.isValid()) return poly;
            if (buffered instanceof MultiPolygon mp && mp.getNumGeometries() > 0) {
                Polygon largest = null;
                double maxArea = 0;
                for (int i = 0; i < mp.getNumGeometries(); i++) {
                    Polygon part = (Polygon) mp.getGeometryN(i);
                    if (part.getArea() > maxArea) { maxArea = part.getArea(); largest = part; }
                }
                return largest;
            }
        } catch (Exception e) {
            log.error("Repair failed: {}", e.getMessage());
        }
        return null;
    }

    public boolean hasSelfIntersection(Polygon polygon) {
        IsValidOp op = new IsValidOp(polygon);
        if (op.isValid()) return false;
        TopologyValidationError err = op.getValidationError();
        return err.getErrorType() == TopologyValidationError.SELF_INTERSECTION
            || err.getErrorType() == TopologyValidationError.RING_SELF_INTERSECTION;
    }
}