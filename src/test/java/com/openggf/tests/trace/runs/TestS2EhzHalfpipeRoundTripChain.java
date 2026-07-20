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
 * <h2>Root cause of the remaining RED (corrected — supersedes the earlier
 * "premature checkpoint gate" hypothesis, which iteration-6 instrumentation
 * DISPROVED)</h2>
 *
 * <p><b>The earlier checkpoint-gate root cause is wrong.</b> Live instrumentation of
 * this chain vs the standalone {@code TestS2SpecialStageTraceReplay} showed the
 * checkpoint gate fires at the <em>identical</em> internal frame in both
 * ({@code frameCounter == 939}); {@code lagCompensation} is already {@code 0.0} on
 * the organic entry (the {@code doEnterSpecialStage} {@code bk2Driven} branch is
 * correctly taken, {@code cursor == 3795}); and the team is correctly Sonic+Tails.
 * The gate timing is NOT premature. What differs is the ring count at the gate: the
 * standalone had 44 rings, this chain had 36 — the checkpoint-fail is a SYMPTOM of
 * under-collection, not a cause.
 *
 * <p><b>The real blocker is a fixture-data gap.</b> The multi-segment run recorder
 * captured each special-stage segment's {@code physics.csv} (frames + a {@code lag}
 * column) but wrote an <b>empty</b> {@code aux_state.jsonl.gz} — zero bytes, no
 * {@code run_objects_end} pass snapshots, no control-state transitions (confirmed for
 * {@code ss}, {@code ss_2}, and the S1 run's {@code ss}). The S2 half-pipe is uniquely
 * ROM-object-pass paced: the standalone must-stay-green harness drives it via
 * {@link com.openggf.trace.SpecialStageRunObjectsPassBinder}, binding each recurring
 * {@code RunObjects} pass to the exact BK2 row the ROM's V-int sampled. Without those
 * pass records the chain can only frame-pace the BK2 (one tick per non-lag row). A
 * per-frameCounter input diff proved the consequence: from {@code fc == 539} the
 * ROM's actual V-int sample carried a steering press (held bit {@code 0x08}) that the
 * chain's sequential-row feed reads as neutral, so the half-pipe player drifts off the
 * rings and stalls at 36 while the recording climbs past 40. The comparison-only
 * invariant forbids recovering the unrecorded V-int sampling from any engine/trace
 * state, so no chain-side code can win this special stage from this fixture.
 *
 * <p><b>Cascade.</b> Because the special stage is LOST rather than WON, its
 * results/return timeline diverges from the recorded WON run, and the subsequent level
 * segment {@code seg2_ehz1} — entered through the special-stage-return reload rather
 * than a fresh boot — desyncs completely (comparator error count ~45324 vs {@code 0}
 * for the fresh-booted {@code seg1_ehz1}; player ends ~1500px off the recorded X with
 * 1 ring vs the recorded 69). The engine therefore never re-accumulates 50 rings and
 * the second {@code starpost_special} entry is never raised: the chain now fails at
 * the {@code seg2 -> ss_2} entry boundary. Making the S2 half-pipe winnable — and thus
 * greening the full round-trip — requires RE-RECORDING {@code ss}/{@code ss_2} WITH
 * the special-stage aux state ({@code run_objects_end} pass log), which is outside a
 * comparison-only test change.
 *
 * <h2>What iteration 6 fixed (boundary-model correction)</h2>
 * <p>Asserting the {@code emeralds_after} delta across an <b>advance-uncompared</b>
 * special stage (SS-INTERIOR policy v1) was itself a boundary-model over-reach: an
 * emerald is only awarded when the stage is WON, so its post-return value is a
 * faithful comparison target only for a REPRODUCED interior. See
 * {@link AbstractRunChainTest#emeraldCarryOverIsVerifiable(SegmentPlan)}:
 * the emerald carry-over is now asserted only across a compared {@code bonus_stage}
 * interior; across an uncompared {@code special_stage} it is a recorded diagnostic.
 * The always-safe carry-overs (the ROM's on-return position restore and ring
 * zero-out, which occur whether the stage was won or lost) remain asserted, so the
 * first {@code stage_exit} cycle now passes those; the RED has advanced to the
 * fixture-blocked {@code seg2 -> ss_2} entry described above.
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
