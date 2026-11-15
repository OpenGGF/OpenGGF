package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A native movie recorded for Stardust Speedway's player beyond the stock rehost fixture
 * ({@code <soniccd.native.root>/rehost-zph/long-R71A/}, written by that directory's
 * {@code _tools/long_runs.sh}): the stock R71A gameplay (idle 60, Right held, A every 60 frames)
 * held for 2390 frames. Sonic crosses the stage's path-swap objects ($1D at x $720 and $7B8,
 * which toggle {@code layer}) and later runs over loop chunks whose collision chunk the
 * {@code layer} byte selects ({@code GetLevelBlock}'s Stardust Speedway branch, scddisasm
 * src/Level/Collision Floor.asm:557-597). Trace data is comparison-only: the BK2 drives the engine.
 * With the branch's layer test removed the replay leaves the native path at row 1364.
 */
class TestSszPlayerMovies {
    @TempDir
    Path tempDir;

    @Test
    void layerSelectsTheLoopChunksAfterThePathSwaps() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-zph/long-R71A/movie.bk2");
        var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R71A, movie.getParent().resolve("trace"), movie);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            Path config = Files.createDirectories(tempDir.resolve("long-R71A"));
            var outcome = CdFixtureReplay.replay(disc, config, fixture, Integer.MAX_VALUE, true, -1, -1);
            assertTrue(outcome.firstErrorRow() < 0,
                    () -> "long-R71A diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
            assertTrue(outcome.rowsCompared() > 1400, "the movie runs past the layer-selected loops");
        }
    }
}
