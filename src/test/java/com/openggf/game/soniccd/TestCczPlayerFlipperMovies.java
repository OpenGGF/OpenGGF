package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Collision Chaos's pinball flipper ($1E) under the player, against the boss lane's complete native fights
 * ({@code <soniccd.native.root>/rehost-boss-ccz/R33C-fight}, {@code R33D-fight}; trace data comparison-only, the BK2
 * drives the engine). Player and camera rows only; the boss slots are {@code TestCczBossNative}'s.
 *
 * <ul>
 *   <li>Landing rolling on a flipper: its {@code StandOnObject} clears the roll, raises the player by 5 and writes
 *       Walk (Object Solid.asm:84-101); a player already standing on it keeps its speeds (:103-118). R33C row 1099,
 *       R33D row 1805.</li>
 *   <li>{@code MoveObject} moves a player standing on a flipper by its X speed less $100 (src/common/object.asm:65-96):
 *       one pixel a frame towards the pivot, also at rest. R33D rows 1806-1832.</li>
 *   <li>The standing balance test's flipper branch writes Walk instead of the balance pose (Main.asm:884-888). R33D
 *       rows 1829-1832 (without it: pose $32 at row 1829).</li>
 * </ul>
 *
 * <p>R33D replays green to its last row. R33C is equal through row 1140: at row 1141 the native player's on-object bit
 * is still set because the row is sampled before the flipper's slot runs in that pass (from row 1138 the bomb in slot
 * 47 and the flipper in slot 95 lag the player by a pass with no lag row); the flipper's {@code GetOffObject} clears
 * it in the engine's whole level frame.
 */
class TestCczPlayerFlipperMovies {
    @TempDir
    Path tempDir;

    @Test
    void r33dFightReplaysToTheEnd() throws Exception {
        var outcome = replay("R33D-fight", CdStagePayload.R33D);
        assertTrue(outcome.firstErrorRow() < 0,
                () -> "R33D-fight diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
        assertTrue(outcome.rowsCompared() > 1832, "the movie runs past the flipper rows");
    }

    @Test
    void r33cFightReplaysPastTheFlipperLanding() throws Exception {
        var outcome = replay("R33C-fight", CdStagePayload.R33C);
        assertTrue(outcome.firstErrorRow() < 0 || outcome.firstErrorRow() >= 1141,
                () -> "R33C-fight diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
    }

    private CdFixtureReplay.Outcome replay(String name, CdStagePayload payload) throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-boss-ccz/" + name + "/movie.bk2");
        var fixture = new CdFixtureReplay.Fixture(payload, movie.getParent().resolve("trace"), movie);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            Path config = Files.createDirectories(tempDir.resolve(name));
            return CdFixtureReplay.replay(disc, config, fixture, Integer.MAX_VALUE, true, -1, -1);
        }
    }
}
