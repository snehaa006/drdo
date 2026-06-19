package in.drdo.gis.engine;

import in.drdo.gis.config.GisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class TerrainEngineTest {

    @Mock DtedReader dtedReader;
    TerrainEngine terrainEngine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        GisProperties props = new GisProperties();
        props.getTerrain().setElevationSampleDistanceM(30.0);
        props.getTerrain().setSlopeThresholdDefault(15.0);
        // Return a flat grid: all elevations 100m
        when(dtedReader.sampleGrid(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt()))
            .thenAnswer(inv -> {
                int rows = inv.getArgument(4);
                int cols = inv.getArgument(5);
                double[][] g = new double[rows][cols];
                for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) g[r][c] = 100.0;
                return g;
            });
        when(dtedReader.getElevation(anyDouble(), anyDouble())).thenReturn(100.0);
        terrainEngine = new TerrainEngine(dtedReader, props);
    }

    @Test
    void flatTerrainIsClassifiedAsPlanar() {
        TerrainEngine.TerrainResult result =
            terrainEngine.analyse(28.6, 77.2, 500, 250, 0);
        assertThat(result.isPlanar()).isTrue();
        assertThat(result.meanSlopeDegrees()).isLessThan(5.0);
        assertThat(result.suitabilityScore()).isGreaterThan(0.6);
    }

    @Test
    void directionalFactorsAllOneForFlatTerrain() {
        double[] factors = terrainEngine.directionalSlopeFactors(28.6, 77.2, 300, 15.0);
        assertThat(factors).hasSize(8);
        for (double f : factors) assertThat(f).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }
}