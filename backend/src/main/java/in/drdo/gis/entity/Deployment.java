package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deployment", indexes = {
    @Index(name = "idx_deployment_uid", columnList = "deployment_uid")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_uid", nullable = false, unique = true, length = 36)
    @Builder.Default
    private String deploymentUid = UUID.randomUUID().toString();

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "status", length = 32)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeploymentStatus status = DeploymentStatus.DRAFT;

    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lon", nullable = false)
    private Double centerLon;

    @Column(name = "center_geom", columnDefinition = "GEOMETRY(Point, 4326)")
    private Point centerGeom;

    @Column(name = "heading_degrees")
    private Double headingDegrees;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @OneToOne(mappedBy = "deployment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DeploymentParameters parameters;

    @OneToOne(mappedBy = "deployment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DeploymentGeometry geometry;

    @OneToOne(mappedBy = "deployment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TerrainAnalysis terrainAnalysis;

    @OneToMany(mappedBy = "deployment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("pointIndex ASC")
    @Builder.Default
    private List<ControlPoint> controlPoints = new ArrayList<>();

    public enum DeploymentStatus {
        DRAFT, COMPUTING, READY, EDITED, ARCHIVED
    }
}
