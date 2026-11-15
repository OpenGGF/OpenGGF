package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.ppz.PpzBossEggman;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Palmtree Panic boss against native references recorded for it
 * ({@code ~/Downloads/Sonic-CD/native/rehost-boss-ppz/<movie>/}: BK2, v5 trace, and the boss probe's post-frame
 * RAM in {@code probe/globals.csv} and {@code probe/objects.csv}; comparison only). Each movie enters the stage
 * through the retail stage-select secret, runs to the arena, fights and, for the two fights, breaks the capsule.
 * Per trace row (row N = BK2 row {@code bk2_frame_offset + N} = probe frame {@code bk2_frame_offset + N + 1}):
 * <ul>
 *   <li>the player and camera rows equal the trace (the shared replay comparison);</li>
 *   <li>the boss, part and capsule appearances (ID, slot, row) equal the aux {@code object_appeared} events;</li>
 *   <li>every boss part and capsule slot equals native RAM in ID, X, Y and routine, Eggman's collision type and
 *       hit count too, and the camera's left, right and bottom bounds equal {@code leftBound}, {@code rightBound},
 *       {@code bottomBound}.</li>
 * </ul>
 */
class TestPpzBossNative {
    /** Eggman and its parts ($2A-$32) and the capsule ($15); hits are Eggman's {@code var_2b} in routine 4. */
    private static final CdBossNativeReplay.Spec SPEC = new CdBossNativeReplay.Spec("rehost-boss-ppz",
            java.util.Set.of(0x15, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32), PpzBossEggman.ID, 4, 13,
            part -> ((PpzBossEggman) part).hitsLeft(), 0x15);

    @TempDir
    Path tempDir;

    /**
     * Every row to the movie's end: after the capsule the player runs right to the 56-px screen boundary ($EFF at row
     * 2434), where the probe past the layout's last column reads a blank chunk, not the first column again
     * (GetLevelBlock, Collision Floor.asm:475-478).
     */
    @Test
    void r13cFightWithAHitReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.checkSlotsExact(tempDir, SPEC, "R13C-fight", CdStagePayload.R13C, true);
    }

    @Test
    void r13dCleanFightReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.checkSlotsExact(tempDir, SPEC, "R13D-fight", CdStagePayload.R13D, true);
    }

    @Test
    void r13cPlayerHitReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.checkSlotsExact(tempDir, SPEC, "R13C-hit", CdStagePayload.R13C);
    }

    /** The player jumps into the front bumper from the left: five frames of the $400 throw at rows 1472-1476. */
    @Test
    void r13cBumperBounceReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.checkSlotsExact(tempDir, SPEC, "R13C-bumper", CdStagePayload.R13C);
    }

    /** The player waits at the arena's edge, is hit twice and dies (routine 6 at row 2035). */
    @Test
    void r13cDeathInTheFightReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.checkSlotsExact(tempDir, SPEC, "R13C-death", CdStagePayload.R13C);
    }

    /**
     * The act end on the R13C fight movie run on to the stage exit (R13C-actend). Native's results object reaches
     * {@code ObjResults_NextLevel} (routine 8) on the trace's last row, 3125, when its timer runs out
     * ({@code subq.w #1,oResultsTimer; bpl; addq.b #2,oRoutine}, Results/Main.asm:193-209); the next frame sets
     * {@code levelRestart} and the stage program returns, so the trace has no row for it. The movie entered by the
     * stage select, so IPX's {@code StageSelect} then returns to the title (IPX.asm:726-727). The engine's results must
     * reach routine 8 on the same row, and one frame later report the act's end through {@code CdStageExit.actCleared},
     * which the flow answers with the title.
     */
    @Test
    void r13cActEndReachesTheStageExitOnTheNativeRow() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-ppz/R13C-actend/manifest.json").getParent();
        Path traceDir = root.resolve("trace");
        int lastRow = com.openggf.trace.TraceData.load(traceDir).frameCount() - 1;
        int[] nextLevelRow = {-1};
        List<String> route = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R13C, traceDir,
                    root.resolve("movie.bk2")), lastRow, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            // The stage select's row 21 is R13C (IPX StageSels), as the movie's route chose.
                            if (row == 0) coordinator().stageSelected(21);
                            if (nextLevelRow[0] < 0 && resultsRoutine() == 8) nextLevelRow[0] = row;
                        }

                        @Override
                        public void after(com.openggf.tests.HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            runner.stepFrame(false, false, false, true, false);
                            route.addAll(coordinator().route());
                        }
                    });
        }
        System.out.println("BOSS1 R13C-actend native NextLevel row " + lastRow + ", engine " + nextLevelRow[0]
                + ", route " + route);
        assertEquals(lastRow, nextLevelRow[0], "the row the results reach ObjResults_NextLevel");
        assertTrue(route.stream().anyMatch(r -> r.startsWith("stage ended")), "actCleared reported: " + route);
        assertTrue(route.getLast().contains("Title"), "stage select returns to the title: " + route);
    }

    private static com.openggf.game.soniccd.program.CdSceneCoordinator coordinator() {
        return GameServices.module().getGameService(com.openggf.game.soniccd.program.CdSceneCoordinator.class);
    }

    /** The live results object's routine (the first line's), or -1. */
    private static int resultsRoutine() {
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof com.openggf.game.soniccd.objects.common.CdResults) || o.isDestroyed()) continue;
            try {
                var field = o.getClass().getDeclaredField("routine");
                field.setAccessible(true);
                int routine = field.getInt(o);
                if (routine == 8) return 8;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return -1;
    }

}
