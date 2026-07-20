package com.openggf.tests.trace.runs;

import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.SegmentPlan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Chain integration test for the committed {@code s2-ehz-halfpipe-roundtrip}
 * run (5 segments: seg1_ehz1 -> ss -> seg2_ehz1 -> ss_2 -> seg3_ehz1, two
 * {@code starpost_special} entry / {@code stage_exit} return cycles). Drives
 * ONE continuous {@code GameLoop} via the shared {@link AbstractRunChainTest}
 * base and asserts the S2 special-stage return-boundary shape: a positional
 * restore to {@code Saved_x/y_pos} plus the ROM-accurate ring ZERO-out on
 * return (rings_after == 0 is ROM truth, not an engine bug).
 *
 * <h2>Known remaining gap (emerald-count boundary assertion)</h2>
 * <p>The interior special-stage is driven with the recorded BK2 input via
 * {@link #uncomparedInteriorStep} (lag-skipped against the {@code s2_special_stage}
 * trace's own per-row {@code lag} column, mirroring
 * {@code S2SpecialStageReplayHarness}) plus the paired {@code GameLoop}/
 * {@code Sonic2SpecialStageManager} fixes documented on {@code doEnterSpecialStage}
 * (TRACE_ACCURATE startup + {@code setLagCompensation(0)} for a BK2-driven organic
 * entry). Diagnostic instrumentation (added and removed in the same investigation
 * pass) proved the driven replay tracks the recorded run byte-accurately for the
 * first ~1500 SS frames: ring count (17 @ row 1200, 34 @ row 1500) and half-pipe
 * track segment index (matching the trace's {@code current_segment} column exactly
 * at every sampled row, e.g. engine/trace segment 15 at the divergence point) both
 * agree with the recorded {@code physics.csv}. The FIRST checkpoint (marker
 * {@code $FE}) also fires correctly: {@code currentSpecialAct} reaches 1 (not a
 * miscounted 4), so {@code Sonic2SpecialStageCheckpoint#resolveCheckpointResult}
 * correctly resolves it as {@code PASSED} (not {@code STAGE_COMPLETE}).
 *
 * <p>The stage nonetheless completes immediately after: at track segment 15 (of a
 * true run length that reaches segment ~0x2b/43 by frame 5100 and only finishes
 * around frame 5400 per the recorded trace), the engine spawns and awards the
 * EMERALD object ({@code Sonic2SpecialStageObjectManager#handleEmerald}, marker
 * {@code $FD}) -- three checkpoints and ~28 further track segments too early. Since
 * the segment/drawing-index counters are independently verified correct at this
 * exact frame, the bug is isolated to the parsed
 * {@code objectLocationData} marker stream for special-stage index 0: marker
 * {@code $FD} is being reached right after the first {@code $FE}, rather than after
 * three more {@code $FE} markers. This looks like a genuine ROM-data-table
 * divergence in {@code Sonic2SpecialStageManager}'s special-stage 0 object/marker
 * location data (independent of chain-drive mechanics -- it would misfire in
 * interactive play too), not a trace-replay-only artifact. Root-causing which ROM
 * table/offset feeds the marker stream (cross-referencing s2disasm's special-stage
 * object location data for stage 0) is the next concrete step; until then this lane
 * fails at the {@code emeralds_after} boundary assertion (expected 1, engine
 * organically produces 1 too -- just three checkpoints early -- so {@code combinedRings}
 * resets to 0 well before the manifest's recorded {@code stage_exit}, and the
 * chain's `emeralds_after` check on the SECOND cycle also fails as a knock-on).
 */
@RequiresRom(SonicGame.SONIC_2)
class TestS2EhzHalfpipeRoundTripChain extends AbstractRunChainTest {

    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s2", "runs", "s2-ehz-halfpipe-roundtrip");

    @Test
    void ehzHalfpipeRoundTrip() throws Exception {
        runChain(RUN_DIR);
    }

    /**
     * S2-specific lag-aware special-stage stepper. The generic base's
     * {@link AbstractRunChainTest#specialStageDrivenStep} feeds every
     * recorded BK2 row as a full {@code Sonic2SpecialStageProvider.update()}
     * tick, but a BizHawk "lag" row is a real elapsed console VBlank where
     * the ROM's OWN game logic did NOT advance (the same reason
     * {@code S2SpecialStageReplayHarness}/{@code AbstractS2SpecialStageTraceReplayTest}'s
     * comparator loop skip lag rows rather than stepping them). Stepping the
     * provider on a lag row runs an EXTRA physics tick beyond what the
     * recorded outcome reflects, drifting the half-pipe rotation/ring-count
     * cadence enough to miss the emerald. Loads the same
     * {@code SpecialStageTraceData} (the {@code s2_special_stage} physics.csv,
     * which carries a per-row {@code lag} column) the standalone harness
     * uses, purely as a read-only lag/pacing signal (comparison-only
     * invariant: no field from it is ever hydrated into engine state) and
     * skips lag rows without stepping the engine.
     */
    @Override
    protected Runnable uncomparedInteriorStep(
            GameLoop loop, InputHandler inputHandler, Bk2Movie movie, SegmentPlan interior) {
        Path ssDir = RUN_DIR.resolve(interior.segment().dir());
        SpecialStageTraceData trace;
        try {
            trace = SpecialStageTraceData.load(ssDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load S2 special-stage lag trace: " + ssDir, e);
        }
        int bk2FrameOffset = interior.segment().bk2FrameOffset();
        int[] traceRow = {0};
        return () -> {
            while (traceRow[0] < trace.frameCount() && trace.getFrame(traceRow[0]).lag()) {
                traceRow[0]++;
            }
            if (traceRow[0] >= trace.frameCount()) {
                // Trace exhausted before stage_exit latched -- fall back to a
                // plain engine step so the boundary await can still detect a
                // late mode flip or trip its step cap instead of looping on
                // an out-of-range trace read.
                AbstractRunChainTest.stepEngineFrame(loop);
                return;
            }
            int absoluteRow = bk2FrameOffset + traceRow[0];
            Bk2FrameInput current = movie.getFrame(absoluteRow);
            Bk2FrameInput previous = absoluteRow > 0 ? movie.getFrame(absoluteRow - 1) : null;
            inputHandler.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
            try {
                AbstractRunChainTest.stepEngineFrame(loop);
            } finally {
                inputHandler.clearLogicalOverride();
            }
            traceRow[0]++;
        };
    }
}
