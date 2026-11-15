package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.boss.qqz.QqzBossTop;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Quartz Quadrant boss against native references recorded for it
 * ({@code ~/Downloads/Sonic-CD/native/rehost-boss-qqz/<movie>/}: BK2, v5 trace, and the QQZ boss probe's post-frame
 * RAM in {@code probe/globals.csv} and {@code probe/objects.csv}; comparison only). Each movie enters the stage through
 * the retail stage-select secret, runs to the arena (a peel-out over the first wall, then Right and jumps), and starts
 * the fight with a jump into the booth. Per trace row (row N = BK2 row {@code bk2_frame_offset + N} = probe frame
 * {@code bk2_frame_offset + N + 1}), through {@link QqzBossNativeReplay}:
 * <ul>
 *   <li>the player and camera rows equal the trace (the shared replay comparison);</li>
 *   <li>the boss, part, spark, bomb, explosion and capsule appearances (row, ID) equal the aux
 *       {@code object_appeared} events;</li>
 *   <li>every such slot equals native RAM in ID, X, Y, routine and collision type, the booth's wear and the belt's
 *       speed and travel too, and the camera's left, right and bottom bounds equal {@code leftBound},
 *       {@code rightBound}, {@code bottomBound}.</li>
 * </ul>
 */
class TestQqzBossNative {
    /** The capsule $15, sparks $20, spike wall $21, belt $22, explosion $23 and the boss $32-$39 (r5/object_index_3_act3.asm). */
    static final QqzBossNativeReplay.Spec SPEC = new QqzBossNativeReplay.Spec("rehost-boss-qqz",
            Set.of(0x15, 0x20, 0x21, 0x22, 0x23, 0x32, QqzBossTop.ID, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39), 0x34, 8, 21,
            part -> 0, 0x15);

    @TempDir
    Path tempDir;

    /**
     * Good future: a complete fight held against the belt, the booth gives out, the escape, the capsule and results,
     * every row to the stage exit (from row 4211 the player stands at the layout's right edge, where native's block
     * lookup reads a blank chunk; GetLevelBlock, Collision Floor.asm:475-478).
     */
    @Test
    void r53cFightReplaysAgainstNative() throws Exception {
        QqzBossNativeReplay.check(tempDir, SPEC, "R53C-fight", CdStagePayload.R53C, true);
    }

    /** Bad future: a complete fight (the belt colours and the music after it differ), every row to the stage exit. */
    @Test
    void r53dFightReplaysAgainstNative() throws Exception {
        QqzBossNativeReplay.check(tempDir, SPEC, "R53D-fight", CdStagePayload.R53D, true);
    }

    /** The player stands against the belt without jumping: hit by the small bombs, then killed by a later one. */
    @Test
    void r53cHitAndDeathReplayAgainstNative() throws Exception {
        QqzBossNativeReplay.check(tempDir, SPEC, "R53C-death", CdStagePayload.R53C);
    }

    /**
     * The act end on R53C-fight, which runs on to the stage exit. Native's results object reaches
     * ObjResults_NextLevel (Level/_Objects/Results/Main.asm:278), routine 8, on the trace's last row, when its timer
     * runs out (ObjResults_Bonus, :193-270); the next frame sets {@code levelRestart} and the stage program returns,
     * so the trace has no row for it. The movie entered by the stage select (row 41 of IPX's StageSels is R53C, as the
     * movie's route chose), so IPX then returns to the title (IPX.asm:726-727). The engine's results must reach routine 8 on the same row and, one frame later,
     * report the act's end through {@code CdStageExit.actCleared}, which the flow answers with the title.
     */
    @Test
    void r53cActEndReachesTheStageExitOnTheNativeRow() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-qqz/R53C-fight/manifest.json").getParent();
        Path traceDir = root.resolve("trace");
        int lastRow = com.openggf.trace.TraceData.load(traceDir).frameCount() - 1;
        int[] nextLevelRow = {-1};
        List<String> route = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R53C, traceDir,
                    root.resolve("movie.bk2")), lastRow, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            if (row == 0) coordinator().stageSelected(41);
                            if (nextLevelRow[0] < 0 && resultsRoutine() == 8) nextLevelRow[0] = row;
                        }

                        @Override
                        public void after(com.openggf.tests.HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            runner.stepFrame(false, false, false, true, false);
                            route.addAll(coordinator().route());
                        }
                    });
        }
        System.out.println("QQZBOSS R53C act end: native NextLevel row " + lastRow + ", engine " + nextLevelRow[0]
                + ", route " + route);
        assertEquals(lastRow, nextLevelRow[0], "the row the results reach ObjResults_NextLevel");
        assertTrue(route.stream().anyMatch(r -> r.startsWith("stage ended")), "actCleared reported: " + route);
        assertTrue(route.getLast().contains("Title"), "stage select returns to the title: " + route);
    }

    private static com.openggf.game.soniccd.program.CdSceneCoordinator coordinator() {
        return GameServices.module().getGameService(com.openggf.game.soniccd.program.CdSceneCoordinator.class);
    }

    /** The live results object's routine 8, or -1. */
    private static int resultsRoutine() {
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof com.openggf.game.soniccd.objects.common.CdResults) || o.isDestroyed()) continue;
            try {
                var field = o.getClass().getDeclaredField("routine");
                field.setAccessible(true);
                if (field.getInt(o) == 8) return 8;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return -1;
    }
}
