package in.drdo.gis.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Component
public class GeoJsonWriter {
    private final ObjectMapper mapper = new ObjectMapper();

    public String toFeatureJson(Polygon polygon, java.util.Map<String, Object> properties) {
        ObjectNode feature = mapper.createObjectNode();
        feature.put("type", "Feature");
        feature.set("geometry", toGeometryNode(polygon));
        ObjectNode props = mapper.createObjectNode();
        if (properties != null) {
            properties.forEach((k, v) -> props.putPOJO(k, v));
        }
        feature.set("properties", props);
        return feature.toString();
    }

    public String toGeometryJson(Polygon polygon) {
        return toGeometryNode(polygon).toString();
    }

    private ObjectNode toGeometryNode(Polygon polygon) {
        ObjectNode geom = mapper.createObjectNode();
        geom.put("type", "Polygon");
        ArrayNode coords = mapper.createArrayNode();
        ArrayNode ring = mapper.createArrayNode();
        for (Coordinate c : polygon.getExteriorRing().getCoordinates()) {
            ArrayNode pt = mapper.createArrayNode();
            pt.add(c.x);
            pt.add(c.y);
            ring.add(pt);
        }
        coords.add(ring);
        geom.set("coordinates", coords);
        return geom;
    }
}
