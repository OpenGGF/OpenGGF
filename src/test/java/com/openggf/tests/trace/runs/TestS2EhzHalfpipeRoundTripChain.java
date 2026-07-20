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
 * entry).
 *
 * <p><b>Corrected root cause (superseding an earlier, disproven "premature emerald /
 * corrupted marker-stream" hypothesis)</b>: a raw dump of the ROM's decompressed
 * {@code objectLocationData} for special-stage index 0 shows its stream is intact --
 * roughly 40 marker bytes span its full ~489-byte span (offsets 0xe-0x1f7), which
 * lines up with the recorded run's own ~44 {@code current_segment} transitions
 * (0..0x2b) to the emerald in {@code ss/physics.csv}. The object/marker table is
 * NOT corrupted or short. Temporary instrumentation in
 * {@code Sonic2SpecialStageObjectManager#handleMarker} /
 * {@code Sonic2SpecialStageManager#handleCheckpoint} /
 * {@code #handleCheckpointResolved} / {@code #markFailed} (added and removed in the
 * same investigation pass, not landed) pinned the actual failure: the engine
 * correctly consumes the no-checkpoint-enable marker ({@code $FC}) and the first
 * checkpoint marker ({@code $FE}) from the ROM stream at the right segment (matching
 * the trace's own rings-to-go-display cadence), and correctly resolves the team-mode
 * ring requirement for checkpoint 1 as 40 (verified directly against the raw ROM
 * bytes at {@code RING_REQ_TEAM_OFFSET}, stage 0 quarter 0 = 40; solo would be 30, so
 * team-mode detection itself is right). But
 * {@code Sonic2SpecialStageManager#tryStartPendingCheckpoint}'s straight-segment gate
 * ({@code CHECKPOINT_TRIGGER_FRAME}/{@code CHECKPOINT_TRIGGER_OFFSET}, comment:
 * "alignment tuned") fires on the FIRST straight-type segment/frame combination
 * reached after the checkpoint becomes pending, because
 * {@code Sonic2TrackAnimator#currentFrameInSegment} resets to 0 on every
 * {@code advanceSegment()} call -- so the single-exact-frame-match heuristic is
 * satisfied as soon as ANY straight-type segment begins, rather than modeling the
 * ROM's actual {@code SSTrack_last_mappings_copy} pointer-identity check
 * ({@code Obj5A_Init}, s2.asm:71409-71420, comparing the currently-drawn track
 * mapping address against {@code MapSpec_Straight4}/{@code MapSpec_Drop1}, which is
 * NOT segment-relative). Concretely, the instrumented run showed the gate firing and
 * resolving at engine frame ~1229 with only 36/40 rings collected
 * ({@code handleCheckpointResolved result=FAILED num=1 req=40 had=36 final=false}),
 * roughly 350 frames (about one full segment) before the recorded run's own
 * {@code rings_togo_bcd} column reaches 0 (40/40) at frame ~1580 in
 * {@code ss/physics.csv}. The premature evaluation comes up 4 rings short and
 * resolves checkpoint 1 as {@code FAILED} instead of {@code PASSED}, which
 * immediately calls {@code markFailed()} and ejects the player back to LEVEL mode --
 * the special stage never reaches the {@code $FD} emerald marker at all in this run.
 *
 * <p>Because {@code CHECKPOINT_TRIGGER_FRAME}/{@code CHECKPOINT_TRIGGER_OFFSET} is
 * empirically tuned against the standalone {@code s2_special_stage} fixture
 * ({@code TestS2SpecialStageTraceReplay}, ALSO special-stage index 0, must-stay-green)
 * and a ROM-faithful fix requires modeling the ROM's actual
 * {@code SSTrack_last_mappings_copy} pointer-range identity (verifying the
 * {@code Ani_SSTrack}-family table position of {@code MapSpec_Straight4} /
 * {@code MapSpec_Drop1} relative to segment boundaries) rather than re-tuning the
 * single-frame offset blind, that ROM-side verification is the next concrete step;
 * re-tuning the constant without it risks silently regressing the must-stay-green
 * standalone trace, which currently depends on the same tuned value producing ITS
 * OWN correct gate timing. Until then this lane fails at the {@code emeralds_after}
 * boundary assertion (expected 1, engine returns 0 because the special stage is
 * failed/ejected rather than completed).
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
