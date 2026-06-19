package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;

@Entity
@Table(name = "deployment_geometry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentGeometry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Deployment deployment;

    @Column(name = "geometry_type", length = 32)
    @Enumerated(EnumType.STRING)
    private GeometryType geometryType;

    @Column(name = "geom", columnDefinition = "GEOMETRY(Polygon, 4326)")
    private Polygon geom;

    @Column(name = "geom_wkt", columnDefinition = "TEXT")
    private String geomWkt;

    @Column(name = "geojson", columnDefinition = "TEXT")
    private String geojson;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_valid")
    @Builder.Default
    private Boolean isValid = true;

    @Column(name = "validation_message", columnDefinition = "TEXT")
    private String validationMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum GeometryType {
        ELLIPSE, BEZIER_ADAPTIVE, BEZIER_CUSTOM
    }
}
