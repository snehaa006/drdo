package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "slope_analysis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlopeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_analysis_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TerrainAnalysis terrainAnalysis;

    @Column(name = "sample_point", columnDefinition = "GEOMETRY(Point, 4326)")
    private Point samplePoint;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "slope_degrees")
    private Double slopeDegrees;

    @Column(name = "aspect_degrees")
    private Double aspectDegrees;

    @Column(name = "slope_ns")
    private Double slopeNs;

    @Column(name = "slope_ew")
    private Double slopeEw;
}
