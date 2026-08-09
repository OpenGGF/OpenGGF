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
 * <h2>Superseded: "the special-stage interior exits 519 rows early"</h2>
 *
 * <p><b>That eject was harness-side, and it is now closed.</b> It was not a short
 * results/fade phase: the run recorder cuts an {@code ss} segment on the raw ROM
 * byte, opening on the first {@code Game_Mode == GameModeID_SpecialStage} frame and
 * closing on the first frame that is no longer it
 * ({@code S2RunCaptureRunner} Blocks 1 and 2). The ROM keeps that mode well past
 * the half-pipe — {@code SS_MainLoop} leaves its object loop when
 * {@code SS_Check_Rings_flag} rises, and the emerald/perfect accounting,
 * {@code Pal_FadeToWhite}, the results-screen build and the whole {@code Obj6F}
 * tally loop below it all still run under it, with {@code Game_Mode} rewritten only
 * by the closing {@code move.b #GameModeID_Level,(Game_Mode).w}
 * (docs/s2disasm/s2.asm:6721-6800). The engine splits that one ROM mode into
 * {@code SPECIAL_STAGE} plus {@code SPECIAL_STAGE_RESULTS}, so the driver's
 * {@code == SPECIAL_STAGE} gate (and the coordinator's matching ownership test)
 * read the engine's internal boundary as a premature exit. Both now accept either
 * engine mode via {@code RunPlaybackObservation.insideRecordedSpecialStageMode},
 * and the interior runs all 5733 represented rows.
 *
 * <h2>Current RED: interior DPLC divergence (newly reachable)</h2>
 *
 * <p>With the interior completing, the previously unreached
 * {@code writeDynamicArtInteriorReport} assertion now runs and fails: 37933 errors
 * over 4518 of the 5733 rows, first at row 136. Two distinct shapes:
 * in-stage rows publish an edge one row early (row 136 engine {@code edges=[3,4,5]}
 * where the recording still has {@code outstanding=[0,1,2]}, then the reverse at
 * 137), and from row 5192 the engine keeps submitting SS player transfers through
 * the results tail where the ROM has none (it {@code clearRAM Object_RAM} before
 * loading {@code Obj6F}).
 *
 * <p><b>SUPERSEDED (2026-08-09). The paragraph below was accurate when written and
 * is no longer true: it states that this fixture's {@code ss} / {@code ss_2} carry
 * zero {@code run_objects_end} records. They carry 3172 and 3472 -- the recorder's
 * pre-start {@code SpecialStage_MainLoop} hook was added and both segments were
 * republished, so the "republish is the only correct closure" conclusion is spent.
 * The interior is now pass-paced and rows 0..5190 of 5733 compare clean.</b>
 *
 * <p><b>Current root cause (measured 2026-08-09): the engine runs the Obj59 emerald
 * sequence exactly one {@code RunObjects} pass early.</b> Recorded pass 3171 raises
 * {@code SS_Check_Rings_flag} at row 5191 and submits the ordinary special-stage
 * player DPLC pair -- ss-tails (mapping frame 0, {@code LoadSSTailsDynPLC}) and
 * ss-tails-tails (mapping frame 4, {@code LoadSSTailsTailsDynPLC}) -- which then
 * retire 39 frames later at 5230 only because no V-int services the DMA queue while
 * {@code Pal_FadeToWhite} / {@code ClearScreen} / {@code NemDec} run; it flushes on
 * the first {@code VintID_Level} {@code WaitForVint} of the {@code Obj6F} tally loop
 * (docs/s2disasm/s2.asm:6797-6800). Nothing in the results tail submits them, so
 * this is not a results/fade-duration item either.
 * The engine's routine-0 init occupies passes 2949..3008 -- the correct count of 60,
 * starting one pass early -- so {@code loc_36172}'s 100-decrement countdown raises the
 * flag on engine pass 3172 (row 5190) instead of recorded pass 3171 (row 5191).
 * Origin: {@code Sonic2SpecialStageManager.streamSpecialStageObjects()} calls
 * {@code executeStreamedObjectInitFallthrough()} at the streaming observation, and
 * that observation's own pass is then deferred to the next observation, so a streamed
 * object's routine 0 runs twice. Closing it needs that duplicate removed AND the last
 * scheduled SS pass given an observation inside the compared window; either alone
 * leaves the count at 45.
 *
 * <p><i>Historical, retained for provenance:</i>
 * <p><b>Root cause (measured 2026-08-08): this is the missing {@code run_objects_end}
 * pass log, not an engine defect and not a publication-phase offset.</b> The
 * interior here is FRAME-paced -- {@link AbstractRunChainTest#uncomparedInteriorStep}
 * runs exactly one {@code GameLoop.step()} per non-lag recorded row, because
 * {@code TraceRunSpecialStageRows.S2Rows.newRunObjectsPassBinder()} returns empty
 * for a segment with no recorded passes, and this fixture's {@code ss} / {@code ss_2}
 * have zero. But the S2 special-stage 68K loop is not one {@code RunObjects} pass
 * per V-blank ({@code SS_MainLoop} sets {@code VintID_S2SS}, waits for the V-int,
 * then runs the pass -- docs/s2disasm/s2.asm:6694-6721), so pass count and row count
 * diverge. The committed standalone stage-1 fixture measures the gap exactly: 3328
 * non-lag rows against 2991 actually-completed ROM passes, an 11% frame-paced
 * overrun. The recorded {@code ss} rows show the same shape directly -- 315 of its
 * 5733 rows carry DPLC edges stamped with TWO distinct {@code logical_frame}s (two
 * ROM passes surfacing on one observation; row 424 submits {@code ss-sonic} /
 * {@code ss-tails} on lag frame 423 and completes them on 424, publishing both at
 * 424), which a one-step-per-row engine cannot produce at all. It submits at 424 and
 * completes at 426, and is a row behind for the rest of the stage; the totals differ
 * too (5814 recorded edges against 5850 engine), so it is not a fixable constant
 * phase shift.
 *
 * <p>The control is decisive: stripping the 2991 {@code run_objects_end} records from
 * the standalone {@code src/test/resources/traces/s2/special_stage} fixture and
 * re-running {@code TestS2SpecialStageTraceReplay} against the copy via
 * {@code -Dopenggf.trace.candidate.dir} does not merely go red on DPLC -- it cannot
 * replay at all ("rings-to-go trigger clear at frame 1324 has no following completed
 * pass"). The green standalone lane is pass-paced by construction, and additionally
 * normalizes the 451 spilled submission edges through
 * {@code DynamicArtSpillNormalization}; the run-chain interior comparator
 * ({@code TraceRunReplayWalker.DynamicArtSegmentComparison}) compares raw recorded
 * rows and applies neither. Normalization alone does not close it (simulated: 4603
 * rows still divergent, against 4518 without it) -- the pass log is the load-bearing
 * half.
 *
 * <p><b>This is therefore a FIXTURE finding.</b> Per-observation pass counts are
 * sub-frame 68K execution timing; deriving them by measuring this fixture's own DPLC
 * rows would be a fitted model under hard rule 3, and no frame-granularity ROM state
 * predicts them. The only correct closure is republishing this run's {@code ss} /
 * {@code ss_2} segments from the already-committed native recorder (a scratch
 * re-capture adds 2991 and 3291 pass records as a strict superset, per the recorder
 * note below) -- which also yields a sixth {@code seg4_ehz2} segment this fixture
 * omits, so it remains a separate publication decision.
 *
 * <p>The paragraph above ("the earlier fixture-data gap story is wrong") remains true
 * of the {@code seg2} return handoff it was written about. The chain now fails in
 * {@link AbstractRunChainTest} with {@code "special stage exited with 519 represented
 * rows remaining in ss"}, at interior row 5213 of 5733 — after the emerald has been
 * won, not before checkpoint 1.
 *
 * <p><b>What the earlier checkpoint eject actually was.</b> Until 2026-08-08 this lane
 * ejected at row 2027 with {@code Checkpoint 1 triggered: required=40, collected=36},
 * and that was read first as a fixture gap and then as a pass-pacing gap. Neither was
 * right. Probing the interior row against the recorded {@code ss} rows showed
 * {@code current_segment}, {@code speed_factor}, {@code track_anim_frame} and the
 * combined ring count matching the recording on EVERY row: the engine held 42 rings by
 * row 1588, exactly as the ROM did. The defect was that the checkpoint was resolved
 * against the ring count captured when the marker was passed (36, row ~1538) instead
 * of the count when the rainbow finished. The ROM reads {@code (Ring_count)} and
 * {@code (Ring_count_2P)} live at {@code loc_35978} (docs/s2disasm/s2.asm:71843-71853),
 * reached only when the rainbow object's x hits {@code $E8} and it deletes itself, so
 * rings taken during the rainbow count. {@code Sonic2SpecialStageCheckpoint} now
 * resolves from a live ring supplier and the interior runs on to row 5213.
 *
 * <p><b>The remaining 519 rows.</b> The recorded {@code check_rings_flag} rises at
 * segment-local frame 5191; the ROM stays in special-stage mode for the rest of the
 * segment running the post-flag tail of {@code SS_MainLoop} (emerald/perfect
 * accounting, {@code Pal_FadeToWhite}, the results-screen build and its {@code Obj6F}
 * tally, s2.asm:6721-6800). The engine finishes at row 5213 — 22 rows late — and then
 * leaves {@code GameMode.SPECIAL_STAGE} almost at once. Closing this is a
 * special-stage results/fade duration item, independent of ring collection.
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
 * <p><b>The third blocker is closed.</b> It was the stale checkpoint ring read
 * described above, not pacing and not the prefix: with the fix, and with the committed
 * (pass-free) fixture still frame-paced, the interior reproduces the recording's track
 * and ring state row for row all the way to the emerald.
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
