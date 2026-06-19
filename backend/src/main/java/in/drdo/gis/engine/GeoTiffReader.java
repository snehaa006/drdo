package in.drdo.gis.engine;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.exception.TerrainDataException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.stereotype.Component;

import java.awt.image.Raster;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads offline GeoTIFF files: extracts CRS, bounds, raster metadata and pixel values.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoTiffReader {

    private final GisProperties gisProperties;
    private final Map<String, GridCoverage2D> coverageCache = new HashMap<>();

    /** Metadata record returned for a GeoTIFF file. */
    public record GeoTiffMetadata(
        String filePath,
        String crsCode,
        double minLon, double minLat,
        double maxLon, double maxLat,
        int width, int height,
        double pixelSizeX, double pixelSizeY,
        int bandCount
    ) {}

    public GeoTiffMetadata readMetadata(String filePath) {
        try {
            GridCoverage2D coverage = loadCoverage(filePath);
            ReferencedEnvelope env = new ReferencedEnvelope(coverage.getEnvelope2D());
            CoordinateReferenceSystem crs = coverage.getCoordinateReferenceSystem2D();
            String crsCode = CRS.toSRS(crs, true);
            Raster raster = coverage.getRenderedImage().getData();
            int width  = raster.getWidth();
            int height = raster.getHeight();
            double pixX = env.getWidth()  / width;
            double pixY = env.getHeight() / height;
            int bands = raster.getNumBands();
            return new GeoTiffMetadata(filePath, crsCode,
                env.getMinX(), env.getMinY(),
                env.getMaxX(), env.getMaxY(),
                width, height, pixX, pixY, bands);
        } catch (Exception ex) {
            throw new TerrainDataException("Failed reading GeoTIFF metadata: " + filePath, ex);
        }
    }

    /**
     * Samples a GeoTIFF raster at the given geographic coordinate.
     * Returns an array of band values (e.g. [R, G, B] or [elevation]).
     */
    public double[] sampleAt(String filePath, double lon, double lat) {
        try {
            GridCoverage2D coverage = loadCoverage(filePath);
            double[] result = new double[coverage.getNumSampleDimensions()];
            coverage.evaluate(new org.geotools.geometry.DirectPosition2D(lon, lat), result);
            return result;
        } catch (Exception ex) {
            log.warn("Could not sample GeoTIFF {} at {}/{}: {}", filePath, lat, lon, ex.getMessage());
            return new double[0];
        }
    }

    /** Discovers all GeoTIFF files under the configured base path. */
    public java.util.List<String> discoverTiffFiles() {
        String basePath = gisProperties.getTerrain().getGeotiffBasePath();
        java.util.List<String> files = new java.util.ArrayList<>();
        try {
            Files.walk(Path.of(basePath))
                .filter(p -> p.toString().toLowerCase().endsWith(".tif")
                          || p.toString().toLowerCase().endsWith(".tiff"))
                .forEach(p -> files.add(p.toString()));
        } catch (Exception ex) {
            log.warn("GeoTIFF discovery failed under {}: {}", basePath, ex.getMessage());
        }
        return files;
    }

    private GridCoverage2D loadCoverage(String filePath) {
        return coverageCache.computeIfAbsent(filePath, fp -> {
            try {
                File f = new File(fp);
                if (!f.exists()) throw new TerrainDataException("GeoTIFF file not found: " + fp);
                GeoTiffFormat format = new GeoTiffFormat();
                if (!format.accepts(f)) throw new TerrainDataException("Not a valid GeoTIFF: " + fp);
                org.geotools.gce.geotiff.GeoTiffReader reader = format.getReader(f);
                return reader.read(null);
            } catch (TerrainDataException e) {
                throw e;
            } catch (Exception ex) {
                throw new TerrainDataException("Failed loading GeoTIFF: " + fp, ex);
            }
        });
    }
}
