package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "terrain_analysis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerrainAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Deployment deployment;

    @Column(name = "analysis_area", columnDefinition = "GEOMETRY(Polygon, 4326)")
    private Polygon analysisArea;

    @Column(name = "mean_elevation_m")
    private Double meanElevationM;

    @Column(name = "min_elevation_m")
    private Double minElevationM;

    @Column(name = "max_elevation_m")
    private Double maxElevationM;

    @Column(name = "mean_slope_degrees")
    private Double meanSlopeDegrees;

    @Column(name = "max_slope_degrees")
    private Double maxSlopeDegrees;

    @Column(name = "terrain_roughness")
    private Double terrainRoughness;

    @Column(name = "suitability_score")
    private Double suitabilityScore;

    @Column(name = "is_planar")
    private Boolean isPlanar;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @CreationTimestamp
    @Column(name = "computed_at", updatable = false)
    private OffsetDateTime computedAt;

    @OneToMany(mappedBy = "terrainAnalysis", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SlopeAnalysis> slopeAnalyses = new ArrayList<>();
}
