package in.drdo.gis.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class GeoJsonFeatureDto {
    private String type = "Feature";
    private Object geometry;
    private Map<String, Object> properties = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getProperties() { return properties; }

    @JsonAnySetter
    public void setProperty(String key, Object value) { properties.put(key, value); }
}
