package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Collision Chaos boss against native references recorded for it ({@code ~/Downloads/Sonic-CD/native/
 * rehost-boss-ccz/<movie>/}: BK2, v5 trace, and the boss probe's post-frame RAM in {@code probe/globals.csv} and
 * {@code probe/objects.csv}; comparison only; see {@link CczBossNativeReplay}). Each movie enters the stage through the
 * retail stage-select secret.
 */
class TestCczBossNative {
    /**
     * Rows of the top movies on which the bombs are compared: all of them. At row 740 of R33C-top (739 of R33D-top)
     * the first bomb moves right past ($3E6, $399) into an H- and V-flipped slope block whose row width is -12;
     * {@code FindLevelWall} negates it to 12 (Collision Floor.asm:874-953) and reads distance 13, no wall, and the bomb
     * flies on (native X $3E2, $3E6, $3EA).
     */
    static final int R33C_TOP_BOMB_ROWS = Integer.MAX_VALUE, R33D_TOP_BOMB_ROWS = Integer.MAX_VALUE;
    /**
     * Rows of the fights on which the bombs are compared: all of them (the fights equal native to the stage exit). At row
     * 624 of R33C-fight (620 of R33D-fight) a bomb rising at X $3C0 meets the full block above it: {@code CheckBlockUp}
     * (common/object_block_collide.asm:301-327) regresses to the block below the probe and moves it down 21 px
     * (Y $1C3 to $1D8).
     */
    static final int R33C_FIGHT_BOMB_ROWS = Integer.MAX_VALUE, R33D_FIGHT_BOMB_ROWS = Integer.MAX_VALUE;
    @TempDir
    Path tempDir;

    /**
     * R33C-top: the fixture's route (Right with a jump every 60 frames) runs along the top of the arena and holds Right
     * at the wall above it. The machine follows the player's Y down to its $DC limit with its gear, Eggman and the bomb
     * launcher; the launcher drops a bomb every 127 frames (120 counted plus seven firing frames), alternating sides;
     * the bombs bounce through the table and explode after $F0 frames. The entry barrier and its gear stand at the
     * arena's entry, the capsule at the top right. Every row must equal native.
     */
    @Test
    void r33cTopRouteTracksAndBombsLikeNative() throws Exception {
        assertWholeMovie(CczBossNativeReplay.check(tempDir, "R33C-top", CdStagePayload.R33C, R33C_TOP_BOMB_ROWS), "R33C-top");
    }

    /** R33D-top: the same route in the bad future. */
    @Test
    void r33dTopRouteTracksAndBombsLikeNative() throws Exception {
        assertWholeMovie(CczBossNativeReplay.check(tempDir, "R33D-top", CdStagePayload.R33D, R33D_TOP_BOMB_ROWS), "R33D-top");
    }

    /**
     * R33C-fight: the complete fight, found by a recorded input search: the entry barrier is struck and the boss music
     * starts, the player climbs the table on its flippers and bumpers (two glass blocks break, rows 1070 and 1138, each
     * ending that frame's object pass after the player), four barriers appear, three hits (release +1330, +1427, +1491)
     * defeat the machine, Eggman flies off, the capsule breaks (+1763) and the results run to the stage exit (2778
     * rows). Every row must equal native: player, camera, bounds, appearances and every boss slot, the exit barrier $3F
     * and the machine's fight routine included, the bombs on every row.
     */
    @Test
    void r33cFightMatchesNativeToTheStageExit() throws Exception {
        CczBossNativeReplay.Result r = CczBossNativeReplay.check(tempDir, "R33C-fight", CdStagePayload.R33C,
                R33C_FIGHT_BOMB_ROWS);
        assertWholeFight(r, "R33C-fight");
    }

    /**
     * R33D-fight: the complete bad-future fight (barriers release +1803/1882/1961/2023, hits +1812/1898/1969, capsule
     * +2141, results +2322, 3055 rows), compared as R33C-fight.
     */
    @Test
    void r33dFightMatchesNativeToTheStageExit() throws Exception {
        CczBossNativeReplay.Result r = CczBossNativeReplay.check(tempDir, "R33D-fight", CdStagePayload.R33D,
                R33D_FIGHT_BOMB_ROWS);
        assertWholeFight(r, "R33D-fight");
    }

    private static void assertWholeFight(CczBossNativeReplay.Result r, String movie) {
        assertWholeMovie(r, movie);
        assertTrue(r.rowsPerId().getOrDefault(0x3F, 0) > 0, movie + ": no rows compared for the exit barrier $3F");
        assertTrue(r.machineFightRows() > 0, movie + ": no rows of the machine's fight routine compared");
    }

    private static void assertWholeMovie(CczBossNativeReplay.Result r, String movie) {
        assertEquals(-1, r.firstErrorRow(), movie + ": player/camera rows must equal native: " + r.outcome().fields());
        assertWindow(r, movie);
        for (int id : new int[] {0x15, 0x30, 0x31, 0x32, 0x33, 0x3D, 0x3E}) {
            assertTrue(r.rowsPerId().getOrDefault(id, 0) > 0, movie + ": no rows compared for ID " + Integer.toHexString(id));
        }
    }

    static void assertWindow(CczBossNativeReplay.Result r, String movie) {
        assertTrue(r.boundMismatches().isEmpty(), movie + ": camera bounds differ: " + r.boundMismatches());
        assertEquals(r.nativeAppearances(), r.engineAppearances(), movie + ": boss appearances (row:ID:count)");
        assertTrue(r.mismatches().isEmpty(), movie + ": " + r.mismatches().size() + " slot mismatches, first "
                + (r.mismatches().isEmpty() ? "" : r.mismatches().getFirst()));
    }
}
