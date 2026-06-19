package in.drdo.gis.dto;

import jakarta.validation.Valid;
import lombok.Data;
import java.util.List;

@Data
public class ControlPointUpdateDto {
    @Valid
    private List<ControlPointDto> controlPoints;
}
