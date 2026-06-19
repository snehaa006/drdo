
package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "depth_config")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DepthConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true, length = 64)
    private String configKey;

    @Column(name = "min_depth_m", nullable = false)
    @Builder.Default private Double minDepthM = 5.0;

    @Column(name = "max_depth_m", nullable = false)
    @Builder.Default private Double maxDepthM = 2000.0;

    @Column(name = "default_depth_m")
    @Builder.Default private Double defaultDepthM = 100.0;

    @Column(name = "step_size_m")
    @Builder.Default private Double stepSizeM = 5.0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
