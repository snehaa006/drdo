package in.drdo.gis.engine;

import in.drdo.gis.config.GisProperties;
import in.drdo.gis.entity.TerrainTile;
import in.drdo.gis.repository.TerrainTileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies DTED tiles are located by their UHL header origin, so real-world
 * folder layouts / file-name casing (not just the sample {@code nNN/eEEE.dt1})
 * are handled — e.g. the standard DTED {@code <lon>/<lat>.DT1} upper-case layout.
 */
class DtedDiscoveryTest {

    @TempDir Path tmp;

    @Test
    void findsTileRegardlessOfFolderLayoutAndCase() throws Exception {
        // The DTED sample shipped in the repo (relative to the backend module dir).
        Path sample = Path.of("..", "data", "terrain", "dted", "n28", "e077.dt1")
                          .toAbsolutePath().normalize();
        assumeTrue(Files.exists(sample), "sample DTED tile not present – skipping");

        // Re-arrange into a DRDO-style layout: <lon>/<lat>.DT1, upper-case, with
        // the opposite nesting to the sample. The name/path give no usable hint.
        Path drdoDir = tmp.resolve("E077");
        Files.createDirectories(drdoDir);
        Files.copy(sample, drdoDir.resolve("N28.DT1"));

        GisProperties props = new GisProperties();
        props.getTerrain().setDtedBasePath(tmp.toString());

        TerrainTileRepository repo = mock(TerrainTileRepository.class);
        when(repo.findTilesCoveringPoint(anyDouble(), anyDouble(), anyList()))
            .thenReturn(List.of());
        when(repo.save(any(TerrainTile.class))).thenAnswer(i -> i.getArgument(0));

        DtedReader reader = new DtedReader(props, repo);
        reader.getElevation(28.5, 77.5);   // inside the 28–29°N / 77–78°E cell

        // The tile was discovered purely from the header and registered against
        // the DRDO-style file — proving layout/case independence.
        ArgumentCaptor<TerrainTile> cap = ArgumentCaptor.forClass(TerrainTile.class);
        verify(repo, atLeastOnce()).save(cap.capture());
        assertThat(cap.getValue().getFilePath().replace('\\', '/')).endsWith("E077/N28.DT1");
    }
}
