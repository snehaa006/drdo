package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

@Entity
@Table(name = "control_point")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ControlPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Deployment deployment;

    @Column(name = "point_index", nullable = false)
    private Integer pointIndex;

    @Column(name = "point_type", length = 32)
    @Enumerated(EnumType.STRING)
    private PointType pointType;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lon", nullable = false)
    private Double lon;

    @Column(name = "geom", columnDefinition = "GEOMETRY(Point, 4326)")
    private Point geom;

    @Column(name = "handle_lat_1")
    private Double handleLat1;

    @Column(name = "handle_lon_1")
    private Double handleLon1;

    @Column(name = "handle_lat_2")
    private Double handleLat2;

    @Column(name = "handle_lon_2")
    private Double handleLon2;

    @Column(name = "is_locked")
    @Builder.Default
    private Boolean isLocked = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum PointType {
        ANCHOR, BEZIER_HANDLE, TERRAIN_ADAPTED
    }
}
