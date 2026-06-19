package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "deployment_parameters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Deployment deployment;

    @Column(name = "frontage_m", nullable = false)
    private Double frontageM;

    @Column(name = "depth_m", nullable = false)
    private Double depthM;

    @Column(name = "slope_threshold_degrees")
    @Builder.Default
    private Double slopeThresholdDegrees = 15.0;

    @Column(name = "heading_degrees")
    private Double headingDegrees;

    @Column(name = "terrain_adaptive")
    @Builder.Default
    private Boolean terrainAdaptive = true;

    @Column(name = "bezier_smoothing")
    @Builder.Default
    private Boolean bezierSmoothing = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
