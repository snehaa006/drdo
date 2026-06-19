package in.drdo.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "terrain_tile", indexes = {
    @Index(name = "idx_terrain_tile_bounds", columnList = "min_lat, max_lat, min_lon, max_lon")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerrainTile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tile_key", nullable = false, unique = true, length = 64)
    private String tileKey;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "tile_type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private TileType tileType;

    @Column(name = "min_lat", nullable = false)
    private Double minLat;

    @Column(name = "max_lat", nullable = false)
    private Double maxLat;

    @Column(name = "min_lon", nullable = false)
    private Double minLon;

    @Column(name = "max_lon", nullable = false)
    private Double maxLon;

    @Column(name = "resolution_arc_sec")
    private Integer resolutionArcSec;

    @Column(name = "crs_code", length = 32)
    private String crsCode;

    @Column(name = "loaded_at")
    private OffsetDateTime loadedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public enum TileType {
        DTED0, DTED1, DTED2, GEOTIFF, SRTM
    }
}
