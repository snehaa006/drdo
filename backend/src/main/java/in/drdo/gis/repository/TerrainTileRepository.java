package in.drdo.gis.repository;

import in.drdo.gis.entity.TerrainTile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TerrainTileRepository extends JpaRepository<TerrainTile, Long> {
    Optional<TerrainTile> findByTileKey(String tileKey);

    @Query("""
        SELECT t FROM TerrainTile t
        WHERE t.minLat <= :lat AND t.maxLat >= :lat
          AND t.minLon <= :lon AND t.maxLon >= :lon
          AND t.tileType IN :types
        ORDER BY t.resolutionArcSec ASC
        """)
    List<TerrainTile> findTilesCoveringPoint(
        @Param("lat") double lat,
        @Param("lon") double lon,
        @Param("types") List<TerrainTile.TileType> types
    );

    @Query("""
        SELECT t FROM TerrainTile t
        WHERE t.maxLat >= :minLat AND t.minLat <= :maxLat
          AND t.maxLon >= :minLon AND t.minLon <= :maxLon
        """)
    List<TerrainTile> findTilesInBbox(
        @Param("minLat") double minLat,
        @Param("minLon") double minLon,
        @Param("maxLat") double maxLat,
        @Param("maxLon") double maxLon
    );
}
