package com.openggf.tests.trace.runs;

import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.SegmentPlan;
import com.openggf.tests.trace.RecordedInputRows;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * "premature checkpoint gate" AND "fixture-data gap / seg2 desyncs completely"
 * hypotheses, both DISPROVEN by frame-level instrumentation)</h2>
 *
 * <p><b>The primary root was the special-stage-RETURN handoff, now FIXED (see
 * {@link AbstractRunChainTest#runChain} LEVEL_MODE interior branch and this class's
 * {@link #uncomparedInteriorStep} mode guard).</b> The engine's title-card exit
 * does not settle into LEVEL cleanly: {@code updateTitleCardMode} releases control and
 * FALLS THROUGH to LEVEL processing in the same {@code loop.step()}, so one LEVEL
 * "fall-through" frame runs before the persistent {@code stage_exit} boundary
 * (mode==LEVEL) latches. Two bugs corrupted that frame: (1) the shared BK2 cursor
 * stayed frozen at the interior-ENTRY offset (the star-post row, holding a direction
 * press) through the whole interior, so the fall-through read a stale steering input;
 * and (2) the special-stage stepper kept overriding {@code InputHandler.logical()}
 * with the recorded SS-trace row even after the engine left {@code SPECIAL_STAGE},
 * injecting an SS steering row into the title-card / fall-through frames. Together
 * these accelerated the player one frame from rest (observed {@code xSpeed 0x0B /
 * gSpeed 0x0C} = the recorded frame-1 accel) so {@code seg2_ehz1} frame 0 compared an
 * already-moving player against the recorded at-rest gameplay-unlock state. The fix
 * pre-seeks the cursor to the return offset, stops the SS override once the engine
 * leaves {@code SPECIAL_STAGE}, and attaches the return comparator at the consumed
 * fall-through frame index without re-seeking. With it, {@code seg2_ehz1} now replays
 * the PLAYER faithfully for ~906 frames (was: diverged at frame 0).
 *
 * <p><b>The earlier "fixture-data gap" story is wrong.</b> It claimed the empty
 * {@code aux_state.jsonl.gz} (no {@code run_objects_end} pass log) made the half-pipe
 * unwinnable, so the LOST stage made {@code seg2} "desync completely (~45324 errors,
 * player ~1500px off)". Instrumentation disproves the causal chain: {@code seg2}'s
 * position restore, ring zero-out and 906 frames of player physics are all faithful
 * regardless of the SS win/lose outcome (position + rings + BK2 input are identical
 * either way). The SS-pass-pacing fixture gap only blocks a per-frame comparison of
 * the SS INTERIOR (which the chain never does — it is advance-uncompared) and the live
 * emerald count; it does NOT drive the seg2 divergence.
 *
 * <h2>SS-return oscillator-phase parity (RESOLVED, engine fix)</h2>
 * <p>Before the engine fix, {@code seg2_ehz1} diverged at frame 907: the engine player
 * LANDED on an {@code OscillationManager}-driven moving platform (object id 0x18,
 * {@code ARZPlatformObjectInstance}) that the recorded player falls past, so the
 * player's trajectory drifted and the second {@code starpost_special} was never
 * reached. Root cause: the engine advanced the GLOBAL oscillator on every locked
 * title-card frame, but the ROM runs {@code OscillateNumDo} only inside
 * {@code Level_MainLoop} (docs/s2disasm/s2.asm:5108) — the title-card wait loops
 * (s2.asm:4914-4924, 5060-5066) run {@code RunObjects} but NOT {@code OscillateNumDo},
 * so the oscillator holds at its {@code OscillateNumInit} baseline until gameplay
 * unlocks. Over the engine's title card (which includes an artificial display hold) the
 * oscillator over-advanced, phase-offsetting Obj18 when control returned. A fresh boot
 * hid this because {@code TraceReplaySessionBootstrap.applyBootstrap} SKIPS the real
 * title card and re-derives the oscillator phase from segment metadata; this chain, by
 * design (spec §"lets GameLoop run its real transition"), runs the engine's real title
 * card and must reproduce the ROM phase organically. Fixed in {@code src/main} by
 * gating the global-oscillator advance during the locked title-card object pass
 * ({@code GameLoop.updateTitleCardMode} -&gt;
 * {@code OscillationManager.suppressNextFrames(1)}), which is
 * game-general (S1 mirrors it: sonic.asm {@code OscillateNumDo} 3030 sits outside
 * {@code Level_TtlCardLoop} 2811-2839) and correct for live play — not a trace-derived
 * value or a re-bootstrap of the returning segment.
 *
 * <p><b>Known remaining diagnostic divergence (does NOT fail this test):</b> the CPU
 * sidekick still over-catches-up during the engine's longer-than-ROM title card
 * (recorded Tails at seg2 frame 1 is still LEFT of Sonic with g_speed 0x90; the engine's
 * Tails has overshot to Sonic's RIGHT at rest). That is a comparison-only diagnostic
 * mismatch on the {@code sidekick_*} fields; it does not affect Sonic's replayed
 * trajectory (CPU Tails does not collide with or steer Sonic), so both {@code starpost}
 * boundaries and every carry-over assertion still hold. Closing it is a separate
 * title-card-duration parity item and is left for a follow-up.
 *
 * <h2>Emerald boundary-model correction (retained + tightened)</h2>
 * <p>Asserting the LIVE {@code emeralds_after} count across an <b>advance-uncompared</b>
 * special stage is a boundary-model over-reach (an emerald is awarded only on a WON
 * stage, which the chain does not reproduce) — see
 * {@link AbstractRunChainTest#emeraldCarryOverIsVerifiable(SegmentPlan)}. Instead of
 * silently no-op'ing, the base now asserts the RECORDED manifest's own emerald
 * progression across a cleared special stage
 * ({@link AbstractRunChainTest#assertRecordedEmeraldProgression}: emeralds_after ==
 * emeralds_before + 1). The always-safe carry-overs (position restore + ring zero-out)
 * remain asserted.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestS2EhzHalfpipeRoundTripChain extends AbstractRunChainTest {

    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s2", "runs", "s2-ehz-halfpipe-roundtrip");

    @Test
    void ehzHalfpipeRoundTrip() throws Exception {
        DynamicArtGapJournalEvidence evidence = assertChainReplay(RUN_DIR);
        DynamicArtStructuralGapEvidence returnGap =
                evidence.structuralGap("ss_2", "seg3_ehz1");
        assertTrue(returnGap.transitionCountAfterNextArm()
                        > evidence.transitionCountAfterFirstArm(),
                "the real S2 represented-segment -> named-run gap -> next-segment "
                        + "boundary must grow the journal beyond first-arm bootstrap");
        assertTrue(returnGap.transitionCountAfterNextArm()
                        > returnGap.transitionCountAtGapStart(),
                "the real S2 ss_2 -> seg3_ehz1 structural gap must append production art");
        assertTrue(returnGap.lastEdgeOrdinalAfterNextArm()
                        > evidence.lastEdgeOrdinalAfterFirstArm(),
                "the real S2 named-run gap must append a later production edge ordinal");
        assertTrue(returnGap.lastEdgeOrdinalAfterNextArm()
                        > returnGap.lastEdgeOrdinalAtGapStart(),
                "the real S2 ss_2 -> seg3_ehz1 structural gap must advance the edge ordinal");
        assertTrue(returnGap.transitionsAddedAcrossBoundary().stream()
                        .map(transition -> transition.edge())
                        .anyMatch(edge -> edge.movieLogicalFrame()
                                >= returnGap.gapStartMovieLogicalFrame()
                                && edge.movieLogicalFrame()
                                <= returnGap.nextSegmentArmMovieLogicalFrame()),
                "the real S2 named-run boundary must add a production art edge "
                        + "inside its structural gap");
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
    protected IntConsumer uncomparedInteriorStep(
            GameLoop loop, InputHandler inputHandler, Bk2Movie movie, SegmentPlan interior) {
        Path ssDir = RUN_DIR.resolve(interior.segment().dir());
        SpecialStageTraceData trace;
        try {
            trace = SpecialStageTraceData.load(ssDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load S2 special-stage lag trace: " + ssDir, e);
        }
        int bk2FrameOffset = interior.segment().bk2FrameOffset();
        RecordedInputRows recordedInputs = new RecordedInputRows(movie, bk2FrameOffset);
        return traceRow -> {
            // Once the engine has left the special stage itself (results screen,
            // fade, and the special-stage-return title card), stop feeding the
            // special-stage trace as a logical-input override. The recorded SS
            // physics.csv covers only the SPECIAL_STAGE phase; continuing to
            // override input through the RESULTS/TITLE_CARD/return frames would
            // inject a stale SS steering row into the title-card-exit fall-through
            // LEVEL frame, accelerating the player off the return segment's
            // gameplay-unlock rest state. Plain-step instead, so the forced-input
            // cursor (pre-seeked by runChain to the return segment's offset) drives
            // the fall-through frame.
            if (loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE) {
                if (trace.metadata()
                        .hasPerFrameDynamicArtTransferState()) {
                    stepUncomparedInteriorLifecycleRow(
                            traceRow < trace.frameCount()
                                    && trace.getFrame(traceRow).lag());
                }
                return;
            }
            if (traceRow < trace.frameCount()
                    && trace.getFrame(traceRow).lag()) {
                if (trace.metadata()
                        .hasPerFrameDynamicArtTransferState()) {
                    stepUncomparedInteriorLifecycleRow(true);
                }
                return;
            }
            recordedInputs.withLogicalOverride(traceRow, inputHandler, () -> {
                AbstractRunChainTest.stepEngineFrame(loop);
            });
        };
    }
}
