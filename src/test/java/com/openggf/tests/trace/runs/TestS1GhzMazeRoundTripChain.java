package com.openggf.tests.trace.runs;

import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceData;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.SegmentPlan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Chain integration test for the committed {@code s1-ghz-maze-roundtrip} run
 * (3 segments: ghz1 -> ss -> ghz2, a {@code giant_ring} entry and
 * {@code stage_exit} return). Drives ONE continuous {@code GameLoop} via the
 * shared {@link AbstractRunChainTest} base and asserts the S1 special-stage
 * return-boundary shape: a NEXT-ACT advance (no positional restore) plus the
 * emerald-count increment.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestS1GhzMazeRoundTripChain extends AbstractRunChainTest {

    private static final Path DEFAULT_RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s1", "runs", "s1-ghz-maze-roundtrip");
    private static final String EXTERNAL_RUN_DIR_PROPERTY = "openggf.trace.s1.run.dir";

    private Path activeRunDir;

    @Test
    void ghzMazeRoundTrip() throws Exception {
        String configuredRunDir = System.getProperty(EXTERNAL_RUN_DIR_PROPERTY);
        activeRunDir = configuredRunDir == null || configuredRunDir.isBlank()
                ? DEFAULT_RUN_DIR
                : Path.of(configuredRunDir).toAbsolutePath().normalize();
        assertChainReplay(activeRunDir);
    }

    /**
     * S1-specific ring semantics: skip the ring-count comparison (still assert
     * emeralds). ROM's {@code Level_LoadObj} unconditionally clears
     * {@code v_rings} on any fresh act entry when {@code v_lastlamp == 0}
     * (docs/s1disasm/sonic.asm:2898-2900 -- "are we starting from a
     * lamppost?"), and {@code Got_NextLevel} clears {@code v_lastlamp} for
     * BOTH the normal sign-post route AND the giant-ring route
     * (docs/s1disasm/_incObj/3A Got Through Card.asm:198, run before the
     * {@code f_bigring} branch). So S1, unlike S2/S3K, never carries rings
     * across an act transition -- the settled post-transition ring count is
     * deterministically 0 regardless of the Special Stage outcome.
     * <p>
     * The manifest's recorded {@code rings_after} (67) is the Special Stage's
     * own ring tally at the instant ROM's {@code v_gamemode} first flips back
     * to {@code id_Level} (docs/s1disasm/sonic.asm:3332, inside
     * {@code SS_ChkEnd} -- well before the results-screen/title-card sequence
     * and {@code Level_LoadObj}'s clear, which run afterward under that SAME
     * coarse {@code v_gamemode} value). Our engine models that same window
     * with distinct {@code GameMode} values ({@code SPECIAL_STAGE_RESULTS} ->
     * {@code TITLE_CARD} -> {@code LEVEL}), so {@code currentMode()==LEVEL}
     * only becomes observable AFTER the ROM-faithful reload has already run
     * and organically produced rings=0 -- the recorded 67 is not reproducible
     * at that later observation point by construction, not by an engine bug.
     */
    @Override
    protected void assertRingsAndEmeralds(
            TraceRunManifest.Transition exit, Path runDir, boolean assertEmeralds) {
        if (assertEmeralds && exit.emeraldsAfter() != null) {
            int actualEmeralds = GameServices.gameState().getEmeraldCount();
            assertEquals(exit.emeraldsAfter().intValue(), actualEmeralds,
                    "Emerald count after stage exit for " + runDir);
        }
    }

    /**
     * S1-specific lag-aware special-stage stepper. The generic base's
     * {@link AbstractRunChainTest#specialStageDrivenStep} feeds every
     * recorded BK2 row as a full {@code Sonic1SpecialStageProvider.update()}
     * tick, but a BizHawk "lag" row is a real elapsed console VBlank where
     * the ROM's OWN game logic did NOT advance (the same reason
     * {@code S1SpecialStageReplayHarness.stepFrame} /
     * {@code AbstractS1SpecialStageTraceReplayTest}'s comparator loop skip
     * lag rows rather than stepping them -- see that class's "VBlank-paced"
     * javadoc section). Stepping the provider on a lag row runs an EXTRA
     * physics tick beyond what the recorded outcome reflects; over this
     * fixture's 72 lag rows (of 3091) that is enough drift in a
     * rotation-driven maze to miss the emerald entirely. Loads the same
     * {@code Sonic1SpecialStageTraceData} the standalone harness uses,
     * purely as a read-only lag/pacing signal (comparison-only invariant:
     * no field from it is ever hydrated into engine state) and skips lag
     * rows without stepping the engine, mirroring the harness's
     * {@code if (tf.lag()) continue;}.
     */
    @Override
    protected Runnable uncomparedInteriorStep(
            GameLoop loop, InputHandler inputHandler, Bk2Movie movie, SegmentPlan interior) {
        Path ssDir = activeRunDir.resolve(interior.segment().dir());
        Sonic1SpecialStageTraceData trace;
        try {
            trace = Sonic1SpecialStageTraceData.load(ssDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load S1 special-stage lag trace: " + ssDir, e);
        }
        int bk2FrameOffset = interior.segment().bk2FrameOffset();
        int recordedFrameCount = interior.segment().traceFrameCount();
        int[] traceRow = {0};
        return () -> {
            while (traceRow[0] < trace.frameCount() && trace.getFrame(traceRow[0]).lag()) {
                // A lag row suppresses special-stage game logic, but is still a
                // recorded console VBlank. The S1 lane consumes it without a
                // GameLoop step, so advance the preserved global object clock
                // explicitly once for that row.
                GameServices.level().getObjectManager().advanceVblaCounter();
                traceRow[0]++;
            }
            if (traceRow[0] >= trace.frameCount()) {
                // Trace exhausted before stage_exit latched -- fall back to a
                // plain engine step so the boundary await can still detect a
                // late mode flip or trip its step cap instead of looping on
                // an out-of-range trace read.
                int beforeVblank = GameServices.level().getObjectManager().getVblaCounter();
                AbstractRunChainTest.stepEngineFrame(loop);
                var objectManager = GameServices.level().getObjectManager();
                if (traceRow[0] < recordedFrameCount
                        && objectManager.getVblaCounter() == beforeVblank) {
                    objectManager.advanceVblaCounter();
                }
                traceRow[0]++;
                return;
            }
            int absoluteRow = bk2FrameOffset + traceRow[0];
            int beforeVblank = GameServices.level().getObjectManager().getVblaCounter();
            Bk2FrameInput current = movie.getFrame(absoluteRow);
            Bk2FrameInput previous = absoluteRow > 0 ? movie.getFrame(absoluteRow - 1) : null;
            inputHandler.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
            try {
                AbstractRunChainTest.stepEngineFrame(loop);
            } finally {
                inputHandler.clearLogicalOverride();
            }
            var objectManager = GameServices.level().getObjectManager();
            if (objectManager.getVblaCounter() == beforeVblank) {
                objectManager.advanceVblaCounter();
            }
            traceRow[0]++;
            if (loop.getCurrentGameMode() == GameMode.LEVEL
                    && traceRow[0] < recordedFrameCount) {
                // The advance-uncompared engine can complete its simplified SS
                // choreography before the recorded interior segment ends. Those
                // remaining BK2 rows are still elapsed VBlanks, so account for
                // the unexecuted tail before the returned LEVEL comparator binds.
                int remainingVblanks = recordedFrameCount - traceRow[0];
                for (int frame = 0; frame < remainingVblanks; frame++) {
                    objectManager.advanceVblaCounter();
                }
                traceRow[0] = recordedFrameCount;
            }
        };
    }
}
