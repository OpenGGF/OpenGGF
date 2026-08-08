package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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
 * {@code LevelManager.suppressGlobalOscillationForTitleCardPass()}), which is
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
 * <h2>Current RED: the special-stage interior exits ~3704 rows early
 * (fixture gap — needs a RE-RECORD, not a harness change)</h2>
 *
 * <p>The paragraph above ("the earlier fixture-data gap story is wrong") remains true
 * of the {@code seg2} return handoff it was written about, but it is NOT the current
 * failure and must not be read as retiring the fixture gap. The chain now fails in
 * {@link AbstractRunChainTest} with {@code "special stage exited with 3704 represented
 * rows remaining in ss"}: the half-pipe reaches {@code Checkpoint 1 triggered:
 * required=40, collected=36}, FAILS, and is ejected at interior row ~2029 of 5733.
 *
 * <p>That is exactly the pass-pacing gap documented on
 * {@link AbstractRunChainTest#emeraldCarryOverIsVerifiable(SegmentPlan)}, and the
 * committed fixtures confirm it directly: this run's
 * {@code traces/s2/runs/s2-ehz-halfpipe-roundtrip/ss/aux_state.jsonl.gz} carries 5733
 * {@code dynamic_art_transfer_state} rows and ZERO {@code run_objects_end} entries,
 * while the standalone {@code traces/s2/special_stage/aux_state.jsonl.gz} — same stage
 * (special_stage_index 0, Sonic+Tails), driven green for its whole length by
 * {@code TestS2SpecialStageTraceReplay} — carries 2991 {@code run_objects_end} entries
 * over 5299 rows. The half-pipe track is ROM-object-pass paced, so binding one
 * {@code GameLoop.step()} per admitted row (what
 * {@link AbstractRunChainTest#uncomparedInteriorStep} falls back to when the segment
 * recorded no passes) advances the track ~1.9x too fast; the recorded ring-requirement
 * reloads land at interior rows 1325/1849/3482 while the engine hits checkpoint 1 at
 * row 2029. Pacing is necessary but, as measured below, not sufficient.
 *
 * <p><b>Blocker status, measured 2026-08-08 (supersedes the 2026-08-07 note).</b>
 * The driver side is now CLOSED: {@link AbstractRunChainTest#uncomparedInteriorStep}
 * consumes {@code newRunObjectsPassBinder}/{@code passPacedFromRow}, and
 * {@link com.openggf.GameLoop.SpecialStageObservationPacing} lets one V-blank row's
 * body run the row's completed-pass count (0, 1 or 2) inside its single PLC lifecycle
 * iteration — which is what {@code TraceRunSpecialStageRowDriver.publishAdmittedRow}
 * requires ("advertised special-stage row N was not published atomically"), since
 * {@code ss} rows own zero passes 3341 times, one pass 1793 times and two passes 599
 * times. Because the committed fixture records no passes, that path is inert here and
 * this lane is unchanged by it.
 *
 * <p>The recorder side is also settled: a scratch native re-capture of this run's own
 * movie reproduces all five committed segments' {@code physics.csv} byte-for-byte and
 * every level segment's aux byte-for-byte, adds 2991 {@code run_objects_end} records
 * to {@code ss} and 3291 to {@code ss_2} as a strict superset (removing those lines
 * restores the committed bytes exactly; {@code metadata.json} differs only in
 * {@code recording_date}), and additionally captures a sixth {@code seg4_ehz2} tail
 * segment plus its {@code level_advance} transition and gap edges — an ADDITIVE
 * widening of what this fixture claims to cover, so it is a separate publication
 * decision rather than something to fold in.
 *
 * <p><b>And a third blocker, newly measured, that neither of those closes.</b> With
 * the re-capture's pass stream overlaid locally and the interior pass-paced from it,
 * the engine ran 1014 object passes over the 2027 rows it reached (previously ~1600 —
 * pacing demonstrably changed) and STILL evaluated checkpoint 1 as
 * {@code required=40, collected=36}, still ejecting on the identical represented row
 * 2027. The recorded stream shows the ROM had cleared that requirement by pass 721
 * (segment-local frame 1574; {@code rings_togo} 0x17 appears at pass 568 and counts to
 * zero), whereas the engine reaches its own check around pass ~790 four rings short.
 * So the ring shortfall is an ENGINE defect that survives correct pacing, not the
 * "fixture-data limitation" this javadoc previously asserted. The prime suspect is the
 * interior's pre-start prefix (rows 0..423, before the recorded {@code control_state}
 * rise), which this chain runs through the production special-stage entry choreography
 * while the green standalone lane bootstraps
 * {@code SpecialStageStartupPolicy.TRACE_ACCURATE}; the recorded first ring group lands
 * at segment-local frames 795-824.
 *
 * <p>Loosening or skipping the remaining-rows assertion remains not an option: it is
 * correctly detecting a real premature exit. Deriving a pass cadence by measuring this
 * fixture's own rows would be a fitted model, and no engine state is hydrated from the
 * trace anywhere on this path.
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

}
