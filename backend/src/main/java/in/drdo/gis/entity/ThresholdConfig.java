package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "threshold_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThresholdConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true, length = 64)
    private String configKey;

    @Column(name = "slope_threshold_degrees")
    @Builder.Default
    private Double slopeThresholdDegrees = 15.0;

    @Column(name = "roughness_threshold")
    @Builder.Default
    private Double roughnessThreshold = 0.3;

    @Column(name = "suitability_min_score")
    @Builder.Default
    private Double suitabilityMinScore = 0.5;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
