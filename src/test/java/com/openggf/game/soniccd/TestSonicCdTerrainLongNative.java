package com.openggf.game.soniccd;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native parity of the full-act routes (rehost-long: cold power-on movies from the stage start to the signpost) whose
 * stops were terrain or camera rules. Each stage must equal the native trace, player and camera, through the row pinned
 * here (a green stage is pinned at its row count: any divergence fails). The pins only move up. Comparison only; the
 * movie and trace are native.
 *
 * <ul>
 *   <li>R11A: from row 2794 the camera rises 2 pixels a row with the player still: {@code LevEvents_PPZ1} sets
 *       {@code camYCenter} to $88 while the player is in the second 3D ramp, X $1C16-$21C5 (Level/_Events.asm:86-95).</li>
 *   <li>R12A row 3122 and R32A row 5753: a jump pressed while rolling up a right-hand wall (angle $C4, $C0) is refused,
 *       because {@code Player_CheckLCeil} finds less than 6 pixels to the left (1 and 3; Collision Check.asm:341-378,
 *       Sonic/Main.asm:1801-1807): the shared headroom probe's single left-wall flip.</li>
 *   <li>R41B: the rotate block's floor probe at row 1144 reads two blank blocks as {@code $1F - (y & $F)}
 *       (Collision Floor.asm:697-702, 788-793); the stage stops at 1699 on water (another owner).</li>
 * </ul>
 */
class TestSonicCdTerrainLongNative {
    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0} equals native through row {1}")
    @CsvSource({"R11A, 4220", "R12A, 4244", "R32A, 6682", "R41B, 1699"})
    void routeEqualsNativeThroughItsPinnedRow(CdStagePayload payload, int pinnedFirstDivergence) throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path root = CdTestMedia.nativeFile("rehost-long/README.md").getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        org.junit.jupiter.api.Assumptions.assumeTrue(fixture.present(), "Native route absent: " + payload);
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            Path config = Files.createDirectories(tempDir.resolve(payload.name()));
            var outcome = CdFixtureReplay.replay(disc, config, fixture, Integer.MAX_VALUE, true, -1, -1);
            assertTrue(outcome.firstErrorRow() < 0 || outcome.firstErrorRow() >= pinnedFirstDivergence,
                    payload + " diverges from the native trace at row " + outcome.firstErrorRow()
                            + ", before the pinned row " + pinnedFirstDivergence + ": " + outcome.fields());
        }
    }
}
