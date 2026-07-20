package com.openggf.tests.trace.runs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;
import com.openggf.trace.replay.TraceReplayDriver;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryEntryMode;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryObservation;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryProbe;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.ReturnAssertionMode;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.SegmentPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable base for multi-stage trace RUN chain tests (spec/API contract:
 * "Decisions locked with the owner" and Component 2 in
 * docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md). Drives
 * ONE continuous {@link GameLoop} through EVERY segment of a
 * {@link TraceRunManifest} — with NO hardcoded segment count and NO
 * zone/route/frame carve-out — and asserts that the engine organically raises
 * each transition and that boundary state (position / checkpoint / rings /
 * emeralds) carries over.
 *
 * <p>All three committed runs drive through this one {@link #runChain} body,
 * each via its own lane subclass: {@link TestS3kMegaRunChain}
 * for {@code s3-knux-multibonus-ss} (25 seg), {@link TestS1GhzMazeRoundTripChain}
 * for {@code s1-ghz-maze-roundtrip} (3 seg), {@link TestS2EhzHalfpipeRoundTripChain}
 * for {@code s2-ehz-halfpipe-roundtrip} (5 seg). A lane subclass supplies only
 * its run directory and {@code @RequiresRom} game; every behavioral branch keys
 * on manifest data ({@code segment.kind()} / {@code transition.entryKind()} /
 * field presence), never on identity.
 *
 * <p>Comparison-only throughout: no trace field is ever hydrated into engine
 * state; every comparator and boundary assertion observes an engine that reached
 * its position by replaying the recorded BK2 inputs.
 */
abstract class AbstractRunChainTest {

    private static final Path REPORT_OUTPUT_DIR = Path.of("target", "trace-reports");

    // -------------------------------------------------------------------------
    // Drive
    // -------------------------------------------------------------------------

    /**
     * The whole chain drive — the only method a lane subclass must call. Loads
     * and plans the run, boots segment 0, then walks every segment, awaiting
     * each boundary the engine raises and asserting return-boundary carry-over.
     */
    protected void assertChainReplay(Path runDir) throws Exception {
        // --- Step 1: load + validate manifest, plan segments (manifest-driven) --
        TraceRunManifest run;
        try {
            run = TraceRunManifest.load(runDir.resolve("run_manifest.json"));
        } catch (IOException e) {
            throw new AssertionError("Failed to load run manifest: " + runDir, e);
        }
        List<SegmentPlan> plans;
        try {
            plans = TraceRunReplayWalker.plan(run, runDir);
        } catch (IllegalStateException e) {
            throw new AssertionError("Manifest validation failed for " + runDir, e);
        }
        assertFalse(plans.isEmpty(), "Run has no segments: " + runDir);

        // Run-scoped step cap for every await/mode-wait; a frozen cursor fails
        // fast instead of hanging (contract section 4).
        int stepCap = TraceRunReplayWalker.interSegmentStepCap(run);

        SegmentPlan first = plans.get(0);
        assertEquals("level", first.segment().kind(), "First segment must be a level: " + runDir);
        Integer bootZone = first.segment().zoneId();
        Integer manifestBootAct = first.segment().act();
        assertNotNull(bootZone, "First segment must declare a zone_id: " + runDir);
        assertNotNull(manifestBootAct, "First segment must declare an act: " + runDir);
        // Manifest `act` is 1-based (matches TraceMetadata/metadata.json convention,
        // e.g. "act": 1 == Act 1); engine act indices are 0-based (see
        // TraceCatalog#resolveTraceEntry: engineAct = meta.act() - 1, and every
        // standalone lane's AbstractTraceReplayTest#act() override returns 0 for
        // Act 1). Convert once here so the boot call sites below never see the
        // raw manifest value.
        Integer bootAct = engineAct(manifestBootAct);
        // --- Step 2: boot segment 0 ---------------------------------------------
        TraceData trace0 = first.trace();
        Path bk2Path = resolveRunBk2(runDir, run.sourceBk2());
        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);

        // Must run before the FIRST HeadlessTestFixture build below (recorded
        // team, cross-game off, S3K intro-skip derived from trace metadata) --
        // not just before driver.start()'s internal loadZoneAndAct. The
        // throwaway build still performs a real team registration + level load
        // via its own bootstrap path; running it against a leftover/default
        // team (e.g. Sonic+Tails) instead of the recorded one would register
        // the wrong sidekick roster before driver.start()'s reset. Configuring
        // the team before EVERY level load -- including the throwaway one --
        // matches the standalone AbstractTraceReplayTest ordering
        // (prepareConfiguration before its one and only fixture build).
        TraceReplaySessionBootstrap.prepareConfiguration(trace0, trace0.metadata());

        // Mirrors TestPachinkoTitleCardIntegration's engine setup: a
        // HeadlessTestFixture build initializes the headless engine before a real
        // GameLoop is constructed. Its sprite()/gameplayMode() are never read
        // afterward -- TraceReplayDriver.start() performs its own full reset +
        // team registration + level load, so a stale cached sprite reference
        // would desync from the engine's actual roster.
        HeadlessTestFixture.builder().withZoneAndAct(bootZone, bootAct).build();
        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);

        LiveEngineFixture fixture = new LiveEngineFixture(movie);
        TraceReplayDriver driver = new TraceReplayDriver(
                trace0, movie, fixture, loop, fixture::sprite, () -> { });
        driver.start(bootZone, bootAct);

        PlaybackDebugManager playback = GameServices.playbackDebug();
        RealEngineHooks hooks = new RealEngineHooks(loop);
        BoundaryProbe probe = new BoundaryProbe(hooks);
        // Replace the raw comparator TraceReplayDriver.start() installed with the
        // probe; the probe is the only observer for the rest of the chain,
        // delegating comparison to whichever segment comparator is attached.
        playback.setFrameObserver(probe);
        probe.setDelegate(driver.comparator());

        // --- Step 3: walk every segment -----------------------------------------
        LiveTraceComparator activeComparator = driver.comparator();
        int i = 0;
        while (i < plans.size()) {
            SegmentPlan seg = plans.get(i);
            TraceRunManifest.Transition exit = seg.exitBoundary();
            boolean last = (i == plans.size() - 1);

            if (exit == null) {
                // Last segment, OR a plain level->level boundary (no transition
                // record). Compare through this segment's recorded frames.
                stepFrames(loop, seg.trace().frameCount());
                maybeWriteReport(run.runId(), i, activeComparator);
                if (last) {
                    break;
                }
                // Plain level->level: cross the act/zone title-card cycle and
                // rebind onto the next level segment.
                activeComparator = handoffAcrossLevelBoundary(
                        loop, playback, probe, movie, plans.get(i + 1), stepCap, fixture);
                i++;
                continue;
            }

            BoundaryEntryMode entryMode = TraceRunReplayWalker.boundaryEntryMode(exit.entryKind());
            if (entryMode == BoundaryEntryMode.LEVEL_MODE) {
                // This segment is an INTERIOR; its exit (stage_exit) returns to a
                // level. Await the mode==LEVEL poll, assert carry-over, rebind.
                //
                // An uncompared interior (special_stage, SS-INTERIOR POLICY v1) is
                // NOT driven by PlaybackDebugManager's forced-input bridge --
                // GameLoop.isDriving() only forces input for LEVEL/BONUS_STAGE
                // (syncPlaybackInputBridge), and the shared BK2 cursor itself
                // freezes in SPECIAL_STAGE mode (onLevelFrameAdvanced is only
                // called from the LEVEL/BONUS_STAGE tick paths). Left undriven, the
                // special stage runs with a neutral/no-op InputHandler for its
                // whole duration, producing an engine-organic outcome that does not
                // match the recorded run. Feed the SAME recorded BK2 rows this
                // segment was captured from as a logical-input override -- the
                // identical mechanism S1SpecialStageReplayHarness/LiveRewindStepper/
                // SpecialStageStepper/TraceSessionLauncher already use via
                // RecordedInputSnapshots.fromBk2 -- via a segment-local row counter
                // (independent of the frozen shared cursor) so the engine actually
                // replays the special stage the recorded inputs drove. Still
                // comparison-only: the trace's control input is read to drive the
                // engine, exactly like the LEVEL/BONUS_STAGE forced-input path
                // already does; no trace FIELD is ever hydrated into engine state,
                // and no field comparison happens during this segment
                // (attachInteriorComparator keeps returning null for special_stage).
                Runnable stepOneFrame = TraceRunReplayWalker.isUncomparedInterior(seg.segment())
                        ? uncomparedInteriorStep(loop, inputHandler, movie, seg)
                        : () -> stepEngineFrame(loop);
                // Pre-seek the shared BK2 cursor to the RETURN level segment's
                // gameplay-unlock offset BEFORE awaiting the stage_exit. The engine's
                // title-card exit does not settle into LEVEL cleanly: updateTitleCardMode
                // releases control and FALLS THROUGH to LEVEL processing in the SAME
                // loop.step() (GameLoop: "Continue to LEVEL mode processing this frame"),
                // so one LEVEL "fall-through" frame runs -- and advances the BK2 cursor --
                // BEFORE the persistent stage_exit boundary (mode==LEVEL) latches and the
                // return comparator can attach. The shared cursor stays frozen at the
                // interior-ENTRY offset through the whole interior (SS/RESULTS/fade/
                // title-card never call onLevelFrameAdvanced), so without this that
                // fall-through frame reads the STALE entry-offset row -- the star-post
                // touch frame, which for this run holds a direction press -- and
                // accelerates the player one frame from rest, corrupting the return
                // level's frame-0 physics. Seeking to the return segment's own offset
                // makes the fall-through frame read that segment's recorded frame-0 input
                // (the gameplay-unlock frame). It pairs with the SS stepper's mode guard
                // (uncomparedInteriorStep stops overriding logical input once the engine
                // leaves SPECIAL_STAGE, so the transition frames are driven by this
                // forced-input cursor rather than a stale special-stage trace row).
                // Comparison-only: this seeks the input cursor exactly as
                // handoffIntoInterior/attachLevelSegment already do -- no trace FIELD is
                // hydrated into engine state.
                int returnOffset = plans.get(i + 1).segment().bk2FrameOffset();
                playback.startSession(movie, returnOffset);
                BoundaryObservation obs =
                        TraceRunReplayWalker.awaitBoundary(probe, exit, stepCap, stepOneFrame);
                // Write the interior's comparator report BEFORE asserting the
                // boundary was observed -- a boundary miss is frequently caused
                // by an upstream interior divergence, and without this report a
                // failed assertTrue below would otherwise leave no artifact to
                // triage it from (maybeWriteReport is a no-op for uncompared
                // special-stage interiors).
                maybeWriteReport(run.runId(), i, activeComparator);
                assertTrue(obs.observed(),
                        "Interior exit boundary (stage_exit) was never observed within the "
                                + "boundary window for " + runDir);
                assertReturnBoundary(plans, i, runDir);
                // The uncompared title-card-exit fall-through LEVEL frame(s) have
                // already advanced the BK2 cursor past the return offset and faithfully
                // reproduced the return segment's leading frame(s) from its own recorded
                // input. Attach the return comparator at that consumed frame index WITHOUT
                // re-seeking, so recording-frame index and BK2 cursor stay in lockstep and
                // no already-run frame is replayed a second time.
                int framesConsumed = playback.getCursorFrame() - returnOffset;
                activeComparator = attachReturnedLevelSegment(
                        probe, plans.get(i + 1), fixture, framesConsumed);
                i++;
            } else {
                // This segment is a LEVEL; its exit is an ENTRY boundary into the
                // interior at i+1. Await the transient entry request, then hand
                // off into the interior mode.
                BoundaryObservation obs =
                        TraceRunReplayWalker.awaitBoundary(probe, exit, stepCap, () -> stepEngineFrame(loop));
                // Report BEFORE asserting -- see the stage_exit branch above for
                // why: a level segment's own interior divergence is the usual
                // cause of a missed entry boundary, and this is the only report
                // this segment's comparator will ever get if the assert throws.
                maybeWriteReport(run.runId(), i, activeComparator);
                assertTrue(obs.observed(), "Segment exit boundary (" + exit.entryKind()
                        + ") was never observed within the boundary window for " + runDir);
                activeComparator = handoffIntoInterior(
                        loop, playback, probe, movie, plans.get(i + 1), stepCap, fixture);
                i++;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Handoffs
    // -------------------------------------------------------------------------

    /**
     * Hands off from a level segment INTO an interior (bonus/special). Detaches
     * the comparator across the uncompared fade/title-card transition, waits for
     * the interior's expected mode, re-seeks the BK2 cursor to the interior's
     * offset, then attaches the interior comparator (or leaves it detached for a
     * special stage -- see {@link #attachInteriorComparator}).
     */
    private LiveTraceComparator handoffIntoInterior(
            GameLoop loop, PlaybackDebugManager playback, BoundaryProbe probe,
            Bk2Movie movie, SegmentPlan interior, int stepCap, LiveEngineFixture fixture) {
        probe.setDelegate(null);
        waitForMode(loop, TraceRunReplayWalker.expectedMode(interior.segment()), stepCap);
        // The BK2 cursor froze during the fade/title-card (the fade skips the
        // cursor advance) -- re-seek to the interior's recorded offset.
        playback.startSession(movie, interior.segment().bk2FrameOffset());
        LiveTraceComparator comparator = attachInteriorComparator(interior, fixture);
        probe.setDelegate(comparator); // null => special-stage advance-uncompared
        return comparator;
    }

    /**
     * Rebinds onto a level segment reached on RETURN from an interior. The mode
     * is already LEVEL (the stage_exit latched exactly when currentMode()==LEVEL),
     * so no wait is needed -- just re-seek and attach the return comparator.
     */
    private LiveTraceComparator attachLevelSegment(
            PlaybackDebugManager playback, BoundaryProbe probe,
            Bk2Movie movie, SegmentPlan level, LiveEngineFixture fixture) {
        playback.startSession(movie, level.segment().bk2FrameOffset());
        LiveTraceComparator comparator = new LiveTraceComparator(
                level.trace(), ToleranceConfig.DEFAULT, 0, fixture::sprite);
        probe.setDelegate(comparator);
        return comparator;
    }

    /**
     * Attaches the comparator for a level segment reached on RETURN from an
     * interior, when the engine's title-card-exit fall-through has already run
     * (and faithfully reproduced) {@code framesConsumed} leading LEVEL frames of
     * that segment from its own recorded input.
     *
     * <p>Unlike {@link #attachLevelSegment}, this does NOT re-seek the BK2 cursor:
     * the interior branch pre-seeked it to the return segment's offset, and the
     * fall-through frame(s) advanced it to {@code returnOffset + framesConsumed}.
     * Starting the comparator at {@code framesConsumed} keeps the recording-frame
     * index and the BK2 cursor in lockstep, so the already-run leading frame(s)
     * are neither replayed a second time (which would double-apply a moving
     * gameplay-unlock frame like {@code seg3_ehz1} frame 0) nor skipped.
     */
    private LiveTraceComparator attachReturnedLevelSegment(
            BoundaryProbe probe, SegmentPlan level, LiveEngineFixture fixture, int framesConsumed) {
        LiveTraceComparator comparator = new LiveTraceComparator(
                level.trace(), ToleranceConfig.DEFAULT, framesConsumed, fixture::sprite);
        probe.setDelegate(comparator);
        return comparator;
    }

    /**
     * Crosses a plain level->level boundary that carries NO transition record
     * (e.g. S3K AIZ->HCZ seg 8->9, HCZ->MGZ seg 18->19). The engine runs an
     * act/zone title-card cycle: wait out of LEVEL (into the title card), back
     * into LEVEL, then rebind onto the next level segment. This is the documented
     * refinement seam for the S3K lane; the S1/S2 runs never take this path.
     */
    private LiveTraceComparator handoffAcrossLevelBoundary(
            GameLoop loop, PlaybackDebugManager playback, BoundaryProbe probe,
            Bk2Movie movie, SegmentPlan nextLevel, int stepCap, LiveEngineFixture fixture) {
        probe.setDelegate(null);
        waitForModeToLeave(loop, GameMode.LEVEL, stepCap);
        waitForMode(loop, GameMode.LEVEL, stepCap);
        return attachLevelSegment(playback, probe, movie, nextLevel, fixture);
    }

    /**
     * SS-INTERIOR SEAM (policy v1 = ADVANCE-UNCOMPARED). Per-frame special-stage
     * field comparison is an explicitly LATER workflow; when it lands, build the
     * special-stage comparator HERE instead of returning {@code null} for a
     * {@code special_stage} segment. See "Decisions locked with the owner" item 1
     * in docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md.
     *
     * <p>v1: a {@code bonus_stage} interior returns a per-frame
     * {@link LiveTraceComparator}; a {@code special_stage} interior returns
     * {@code null} so the boundary probe forwards to no comparator across the
     * whole special-stage phase (the "VBLANK-only" advance-uncompared phase).
     */
    protected LiveTraceComparator attachInteriorComparator(SegmentPlan interior, LiveEngineFixture fixture) {
        if (TraceRunReplayWalker.isUncomparedInterior(interior.segment())) {
            return null;
        }
        return new LiveTraceComparator(interior.trace(), ToleranceConfig.DEFAULT, 0, fixture::sprite);
    }

    // -------------------------------------------------------------------------
    // Return-boundary assertions (per contract section 3.2)
    // -------------------------------------------------------------------------

    /**
     * Asserts the carry-over state after a {@code stage_exit}, switching on
     * {@link TraceRunReplayWalker#returnAssertionMode} of the INTERIOR's entry
     * transition. Overridable so a lane can adjust game-specific accessors.
     *
     * @param plans         the full planned segment list
     * @param interiorIndex index of the interior segment that just exited
     */
    protected void assertReturnBoundary(List<SegmentPlan> plans, int interiorIndex, Path runDir) {
        SegmentPlan interior = plans.get(interiorIndex);
        TraceRunManifest.Transition entry = interior.entryBoundary();
        TraceRunManifest.Transition exit = interior.exitBoundary();
        assertNotNull(entry, "Interior segment must have an entry boundary: " + runDir);
        SegmentPlan returnLevel = plans.get(interiorIndex + 1);

        ReturnAssertionMode mode = TraceRunReplayWalker.returnAssertionMode(entry);
        switch (mode) {
            case POSITIONAL_RESTORE -> assertPositionalRestore(entry, returnLevel, runDir);
            // Core-review fix: S3K bonus and special-stage returns ALSO restore
            // the recorded Saved_X_pos/Saved_Y_pos (every starpost_bonus and
            // giant_ring transition in the committed 25-segment run carries
            // them non-null); assertPositionalRestore null-guards on field
            // presence, so adding it here upgrades 11 of that run's 22
            // transitions from zero positional verification to the same
            // centre-position comparison the S2 path already gets.
            case CHECKPOINT_RESTORE -> {
                assertCheckpointRestore(entry, runDir);
                assertPositionalRestore(entry, returnLevel, runDir);
            }
            case NEXT_ACT -> assertNextActAdvance(plans, interiorIndex, returnLevel, runDir);
            case RINGS_EMERALDS_ONLY -> assertPositionalRestore(entry, returnLevel, runDir);
        }
        boolean liveEmeraldVerifiable = emeraldCarryOverIsVerifiable(interior);
        assertRingsAndEmeralds(exit, runDir, liveEmeraldVerifiable);
        if (!liveEmeraldVerifiable) {
            assertRecordedEmeraldProgression(entry, exit, runDir);
        }
    }

    /**
     * When the live engine emerald count is NOT an organically verifiable boundary
     * (an advance-uncompared {@code special_stage} whose win/lose outcome the chain
     * does not reproduce — see {@link #emeraldCarryOverIsVerifiable(SegmentPlan)}),
     * this asserts the one emerald fact that IS verifiable: the RECORDED manifest's
     * own emerald progression across the interior. It does not touch engine state.
     *
     * <p>An S2 special stage banks exactly one emerald when cleared, so a recorded
     * run that entered with {@code emeralds_before} and returned with
     * {@code emeralds_after} must satisfy {@code emeralds_after == emeralds_before + 1}
     * (the committed {@code s2-ehz-halfpipe-roundtrip} run clears both halfpipes,
     * banking 0->1 then 1->2). Asserting this recorded-truth invariant replaces the
     * former silent no-op, so a manifest that fails to record the emerald award (or
     * records an impossible delta) is caught even while the live count stays a
     * diagnostic. Gated on both recorded fields being present and overridable for a
     * lane whose recorded special stage was NOT a clear.
     */
    protected void assertRecordedEmeraldProgression(
            TraceRunManifest.Transition entry, TraceRunManifest.Transition exit, Path runDir) {
        if (entry.emeraldsBefore() == null || exit.emeraldsAfter() == null) {
            return;
        }
        assertEquals(entry.emeraldsBefore() + 1, exit.emeraldsAfter().intValue(),
                "Recorded emerald bank across a cleared special stage (emeralds_after == "
                        + "emeralds_before + 1) for " + runDir);
    }

    /**
     * Whether the emerald carry-over across this interior is an organically
     * VERIFIABLE boundary (a comparison against engine state the replayed inputs
     * produced), rather than only a recorded manifest datum.
     *
     * <p>An emerald is awarded only when the interior stage is <em>won</em>, so the
     * post-return emerald count is a faithful comparison target only when the
     * interior was faithfully reproduced. A {@code bonus_stage} interior IS
     * reproduced (it is driven with a per-frame {@link LiveTraceComparator}, see
     * {@link #attachInteriorComparator}), so its emerald delta is asserted. A
     * {@code special_stage} interior is <b>advance-uncompared</b> under SS-INTERIOR
     * policy v1 ({@link TraceRunReplayWalker#isUncomparedInterior}) — it is phased
     * through without per-frame reproduction — so its win/lose outcome, and hence
     * the emerald it would award, is NOT organically re-derivable.
     *
     * <p>This is compounded, and made unrecoverable in code, by a property of the
     * committed run fixtures: the multi-segment run recorder captured each
     * special-stage segment's {@code physics.csv} (frames + lag column) but wrote an
     * <b>empty</b> {@code aux_state.jsonl.gz} — no {@code run_objects_end} pass
     * snapshots and no control-state transitions (verified across the S2
     * {@code ss}/{@code ss_2} and the S1 {@code ss} segments). The S2 half-pipe in
     * particular is ROM-object-pass paced: the standalone must-stay-green
     * {@code TestS2SpecialStageTraceReplay} drives it via
     * {@code SpecialStageRunObjectsPassBinder}, binding each recurring RunObjects
     * pass to the exact BK2 row the ROM's V-int sampled. Without those pass records
     * the chain can only frame-pace the BK2 (one tick per non-lag row), which feeds
     * the wrong controller sample once the ROM's V-int/RunObjects interleave drifts
     * from a 1:1 non-lag cadence — the half-pipe player then under-collects rings
     * (36 vs the recorded 40 at checkpoint 1, at the identical internal frame 939),
     * fails the checkpoint, and is ejected without the emerald. That is a
     * fixture-data limitation, not an engine defect: nothing the comparison-only
     * chain may do (it must not hydrate engine state from the trace) can recover the
     * unrecorded V-int sampling. The recorded {@code emeralds_after} therefore
     * stays a diagnostic for an advance-uncompared special stage. The always-safe
     * carry-overs — the ROM's on-return position restore and ring zero-out, which
     * happen whether the stage was won or lost — remain asserted.
     *
     * <p>Overridable so a lane whose special-stage interior IS faithfully drivable
     * from its own fixture (or a future policy that compares special stages
     * per-frame) can re-enable the emerald assertion. Keyed purely on the manifest
     * segment kind via {@code isUncomparedInterior} — not on zone/route/game.
     */
    protected boolean emeraldCarryOverIsVerifiable(SegmentPlan interior) {
        return !TraceRunReplayWalker.isUncomparedInterior(interior.segment());
    }

    /**
     * S2 starpost_special: player restored to Saved_x/y (ROM {@code Obj79_LoadData}:
     * {@code move.w (Saved_x_pos).w,(MainCharacter+x_pos).w} /
     * {@code move.w (Saved_y_pos).w,(MainCharacter+y_pos).w}).
     *
     * <p>The comparison target is the RETURN LEVEL segment's own recorded frame 0
     * (ground truth captured live from the ROM), not the entry transition's raw
     * {@code saved_x_pos}/{@code saved_y_pos} fields. Those fields are a snapshot of
     * the ROM {@code Saved_x_pos}/{@code Saved_y_pos} RAM cells taken at CHECKPOINT
     * TOUCH time (ROM {@code Obj79_SaveData}: {@code move.w x_pos(a0),(Saved_x_pos).w}
     * where {@code a0} is the checkpoint OBJECT, not the player) -- i.e. the
     * checkpoint's own static placement, which is not guaranteed to sit exactly on
     * the floor. X is unaffected by gravity so both sources agree, but after the
     * {@code Obj79_LoadData} write restores Y verbatim, the ROM's own level physics
     * keeps running every frame through the ensuing title-card phase (player input
     * is locked but gravity is not), settling the player onto the actual floor
     * before {@code mode_change_bk2_frame}/{@code stage_exit} is reached. Confirmed
     * against the committed run's {@code seg2_ehz1} physics.csv: frame 0 already
     * reads {@code player_y=00AD} (173), not the transition's {@code saved_y_pos=170}
     * -- so asserting the raw entry-time snapshot against the settled return
     * position would fail on ROM-faithful engine output. The return level's frame 0
     * is recorded at the exact same {@code mode_change_bk2_frame}
     * ({@code segment.bk2FrameOffset() == transition.modeChangeBk2Frame()} for every
     * {@code stage_exit} in this run), so it is the correct ground truth for this
     * boundary.
     */
    protected void assertPositionalRestore(
            TraceRunManifest.Transition entry, SegmentPlan returnLevel, Path runDir) {
        AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
        assertNotNull(sprite, "Focused sprite missing on special-stage return for " + runDir);
        assertTrue(returnLevel.trace().frameCount() > 0,
                "Return level segment has no recorded frames: " + runDir);
        var restored = returnLevel.trace().getFrame(0);
        if (entry.savedXPos() != null) {
            assertEquals(restored.x(), (int) sprite.getCentreX(),
                    "Player X restore after special-stage return for " + runDir);
        }
        if (entry.savedYPos() != null) {
            assertEquals(restored.y(), (int) sprite.getCentreY(),
                    "Player Y restore after special-stage return for " + runDir);
        }
    }

    /** S3K starpost_bonus: checkpoint index restored from last_star_post_hit. */
    protected void assertCheckpointRestore(TraceRunManifest.Transition entry, Path runDir) {
        if (entry.lastStarPostHit() != null) {
            int actual = GameServices.level().getCheckpointState().getLastCheckpointIndex();
            assertEquals(entry.lastStarPostHit().intValue(), actual,
                    "Star-post restore after bonus-stage return for " + runDir);
        }
    }

    /**
     * S1 giant_ring (no saved position): the special-stage return is a NEXT-ACT
     * advance, not a positional resume. Asserts the engine settled into the
     * return level segment's declared zone/act AND that it differs from the
     * pre-entry level segment (proving an advance). Overridable if a lane's
     * engine zone/act index differs from the manifest's zone_id/act encoding.
     */
    protected void assertNextActAdvance(
            List<SegmentPlan> plans, int interiorIndex, SegmentPlan returnLevel, Path runDir) {
        TraceRunManifest.Transition entry = plans.get(interiorIndex).entryBoundary();
        SegmentPlan preEntry = plans.get(entry.fromSegment());
        Integer returnAct = returnLevel.segment().act();
        Integer preAct = preEntry.segment().act();
        if (returnAct != null && preAct != null) {
            assertNotEquals(preAct.intValue(), returnAct.intValue(),
                    "Manifest next-act shape: return act must differ from pre-entry act for " + runDir);
            // Manifest `act` is 1-based; GameServices.level().getCurrentAct() is the
            // engine's 0-based act index (see the boot-act conversion note in
            // runChain / engineAct below) -- convert before comparing.
            assertEquals(engineAct(returnAct).intValue(), GameServices.level().getCurrentAct(),                    "Next-act advance (act) after special-stage return for " + runDir);
        }
        Integer returnZone = returnLevel.segment().zoneId();
        if (returnZone != null) {
            assertEquals(returnZone.intValue(), GameServices.level().getRomZoneId(),
                    "Next-act advance (zone) after special-stage return for " + runDir);
        }
    }

    /**
     * Rings/emeralds carry-over. A recorded {@code rings_after == 0} (S2 zeroes
     * rings on special-stage return) is ROM truth and asserted like any value.
     * The ring carry-over is always asserted (the ROM sets it on return regardless
     * of whether the interior stage was won or lost). The emerald carry-over is
     * asserted only when {@code assertEmeralds} is true — see
     * {@link #emeraldCarryOverIsVerifiable(SegmentPlan)} for why an
     * advance-uncompared special stage passes {@code false} here.
     *
     * @param assertEmeralds whether the emerald delta is an organically verifiable
     *                       boundary for this interior (true for a reproduced
     *                       {@code bonus_stage}; false for an advance-uncompared
     *                       {@code special_stage})
     */
    protected void assertRingsAndEmeralds(
            TraceRunManifest.Transition exit, Path runDir, boolean assertEmeralds) {
        if (exit.ringsAfter() != null) {
            int actualRings = GameServices.level().getLevelGamestate().getRings();
            assertEquals(exit.ringsAfter().intValue(), actualRings,
                    "Ring carry-over after stage exit for " + runDir);
        }
        if (assertEmeralds && exit.emeraldsAfter() != null) {
            int actualEmeralds = GameServices.gameState().getEmeraldCount();
            assertEquals(exit.emeraldsAfter().intValue(), actualEmeralds,
                    "Emerald count after stage exit for " + runDir);
        }
    }

    /**
     * Converts a manifest {@code act} (1-based, matching the recorder's
     * {@code metadata.json}/{@code run_manifest.json} convention -- e.g.
     * {@code "act": 1} means Act 1) to the engine's 0-based act index consumed
     * by {@code loadZoneAndAct}/{@code getCurrentAct()} and every standalone
     * lane's {@code AbstractTraceReplayTest#act()} override (which returns 0
     * for Act 1). Mirrors {@code TraceCatalog}'s {@code engineAct = meta.act() - 1}
     * conversion so the chain driver never feeds a raw 1-based manifest value
     * to an engine act parameter.
     */
    private static Integer engineAct(Integer manifestAct) {
        return manifestAct == null ? null : Math.max(0, manifestAct - 1);
    }

    // -------------------------------------------------------------------------
    // Stepping helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the step function used to drive an uncompared (special_stage)
     * interior. Overridable so a lane with a per-game special-stage trace
     * format (carrying a per-row lag flag, e.g. {@code SpecialStageTraceData}
     * for S2 or {@code Sonic1SpecialStageTraceData} for S1) can skip lag rows
     * the way the standalone SS trace-replay harnesses do. Default: feed
     * EVERY recorded BK2 row as a full physics tick via
     * {@link #specialStageDrivenStep} (no lag-skip) -- correct as long as the
     * interior's actual outcome carry-over is not asserted, or a lane
     * overrides this.
     */
    protected Runnable uncomparedInteriorStep(
            GameLoop loop, InputHandler inputHandler, Bk2Movie movie, SegmentPlan interior) {
        return specialStageDrivenStep(loop, inputHandler, movie, interior.segment().bk2FrameOffset());
    }

    /**
     * Builds a step function that drives an uncompared (special_stage)
     * interior with the SAME recorded BK2 rows the segment was captured
     * from, using a segment-local row counter starting at
     * {@code bk2FrameOffset} -- independent of the shared
     * {@code PlaybackDebugManager} cursor, which never advances in
     * SPECIAL_STAGE mode (see the call-site comment in {@link #runChain}).
     * Sets an {@link InputHandler} logical override for the duration of one
     * {@link GameLoop#step()} call and clears it immediately after, mirroring
     * {@code S1SpecialStageReplayHarness.stepFrame} / {@code
     * TraceSessionLauncher#applySpecialStageTraceInputIfActive}. The "previous"
     * row for press-edge detection is always the immediately preceding
     * physical BK2 row, matching the same rule those callers use.
     *
     * <p>Package-visible (not private) so a lane's {@link #uncomparedInteriorStep}
     * override can build its own lag-aware variant of this same input-feed/
     * step/clear sequence (see {@code stepEngineFrame}, also package-visible
     * for the same reason) instead of duplicating {@link #runChain}'s
     * plumbing.
     */
    static Runnable specialStageDrivenStep(
            GameLoop loop, InputHandler inputHandler, Bk2Movie movie, int bk2FrameOffset) {
        int[] localRow = {0};
        return () -> {
            int absoluteRow = bk2FrameOffset + localRow[0];
            Bk2FrameInput current = movie.getFrame(absoluteRow);
            Bk2FrameInput previous = absoluteRow > 0 ? movie.getFrame(absoluteRow - 1) : null;
            inputHandler.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
            try {
                stepEngineFrame(loop);
            } finally {
                inputHandler.clearLogicalOverride();
            }
            localRow[0]++;
        };
    }

    private static void stepFrames(GameLoop loop, int frameCount) {
        for (int f = 0; f < frameCount; f++) {
            stepEngineFrame(loop);
        }
    }

    /**
     * Converts a manifest's 1-based ROM act number (act 1 / act 2, as the
     * recorder writes it) to the engine's 0-based act index used by
     * {@code LevelManager.loadZoneAndAct} and {@code getCurrentAct()}. This is the
     * same convention every standalone {@code *CompleteRunTraceReplay} lane
     * encodes by hand (AIZ act 1 -&gt; {@code act()==0}, GHZ act 2 -&gt;
     * {@code act()==1}); the manifest keeps the ROM-facing value so it stays
     * human-readable and game-agnostic. Level segments only (special-stage
     * segments carry act 0 and never reach a level load through this path).
     */
    private static int engineAct(int manifestAct) {
        return manifestAct - 1;
    }

    /**
     * Steps until the mode reaches {@code target} or the step cap is exhausted.
     * The cap is the same manifest-derived bound {@link TraceRunReplayWalker#awaitBoundary}
     * uses, so a hung transition fails fast instead of looping forever.
     */
    protected int waitForMode(GameLoop loop, GameMode target, int maxSteps) {
        int steps = 0;
        while (loop.getCurrentGameMode() != target) {
            stepEngineFrame(loop);
            steps++;
            if (steps >= maxSteps) {
                throw new AssertionError("Mode never reached " + target + " within "
                        + maxSteps + " frames (frozen transition?)");
            }
        }
        return steps;
    }

    private static void waitForModeToLeave(GameLoop loop, GameMode from, int maxSteps) {
        int steps = 0;
        while (loop.getCurrentGameMode() == from) {
            stepEngineFrame(loop);
            steps++;
            if (steps >= maxSteps) {
                throw new AssertionError("Mode never left " + from + " within "
                        + maxSteps + " frames (expected an act/zone title-card cycle)");
            }
        }
    }

    /**
     * Advances one engine frame the way {@code Engine.display()} does in live
     * play: fade state is updated FIRST (via {@code UiRenderPipeline.updateFade()}
     * -> {@code FadeManager.update()}), THEN {@code GameLoop.step()} runs gameplay
     * (Engine.java: {@code uiPipeline.updateFade()} precedes {@code update()} in
     * the per-frame {@code display()} body). The chain drive steps the headless
     * {@link GameLoop} directly and never calls {@code Engine.display()}, so
     * without this the {@code FadeManager} instance {@code GameLoop} installs
     * (see the boundary-entry {@code fadeManager.startFadeToWhite(...)} calls
     * gating special/bonus stage entry) never advances and its completion
     * callback -- the one that flips {@code currentGameMode} to
     * {@code SPECIAL_STAGE}/{@code BONUS_STAGE} -- never fires, hanging
     * {@link #waitForMode} forever on a fade-gated boundary. This is bootstrap
     * plumbing parity with the production per-frame call order, not trace-data
     * hydration.
     */
    static void stepEngineFrame(GameLoop loop) {
        var fade = GameServices.fadeOrNull();
        if (fade != null) {
            fade.update();
        }
        loop.step();
    }

    // -------------------------------------------------------------------------
    // Reporting
    // -------------------------------------------------------------------------

    private void maybeWriteReport(String runId, int segmentIndex, LiveTraceComparator comparator)
            throws IOException {
        // A special-stage interior (advance-uncompared, v1) has no comparator, so
        // nothing to report; skip rather than emit an empty summary.
        if (comparator == null) {
            return;
        }
        writeChainSegmentReport(runId, segmentIndex, comparator);
    }

    private void writeChainSegmentReport(String runId, int segmentIndex, LiveTraceComparator comparator)
            throws IOException {
        Files.createDirectories(REPORT_OUTPUT_DIR);
        Path jsonPath = REPORT_OUTPUT_DIR.resolve(runId + "_seg" + segmentIndex + "_report.json");
        Files.writeString(jsonPath, buildComparatorSummaryJson(comparator));
        assertTrue(Files.exists(jsonPath), "Chain segment report must be written: " + jsonPath);
    }

    /**
     * {@link LiveTraceComparator} never builds a {@code DivergenceReport} itself
     * (that is {@code TraceBinder}'s job inside {@code AbstractTraceReplayTest}),
     * so this writes a lightweight summary JSON from the comparator's own
     * accessors (error/warning/lag counts plus the recent-mismatch ring buffer).
     */
    private static String buildComparatorSummaryJson(LiveTraceComparator comparator) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("errorCount", comparator.errorCount());
        summary.put("warningCount", comparator.warningCount());
        summary.put("laggedFrames", comparator.laggedFrames());
        summary.put("complete", comparator.isComplete());
        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (MismatchEntry entry : comparator.recentMismatches()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("frame", entry.frame());
            row.put("field", entry.field());
            row.put("romValue", entry.romValue());
            row.put("engineValue", entry.engineValue());
            row.put("delta", entry.delta());
            row.put("severity", entry.severity().name());
            row.put("repeatCount", entry.repeatCount());
            mismatches.add(row);
        }
        summary.put("recentMismatches", mismatches);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(summary);
    }

    // -------------------------------------------------------------------------
    // BK2 resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the shared BK2 for a run. Follows the shared-movie convention
     * (sibling {@code _movies/<source_bk2>} directory at the game root, one level
     * up from {@code runs/<run-id>/}), falling back to a copy inside the run
     * directory itself. Overridable for lanes that stage the BK2 elsewhere.
     */
    protected Path resolveRunBk2(Path runDir, String sourceBk2) throws IOException {
        Path gameRoot = runDir.getParent().getParent();
        Path shared = gameRoot.resolve("_movies").resolve(sourceBk2);
        if (Files.exists(shared)) {
            return shared;
        }
        Path local = runDir.resolve(sourceBk2);
        if (Files.exists(local)) {
            return local;
        }
        throw new IOException("No BK2 found for run " + runDir
                + " (checked " + shared + " and " + local + ")");
    }

    // -------------------------------------------------------------------------
    // Engine adapters (shared by every lane)
    // -------------------------------------------------------------------------

    /** Reads engine boundary state through real production accessors. */
    protected static final class RealEngineHooks implements TraceRunReplayWalker.EngineHooks {
        private final GameLoop loop;

        private RealEngineHooks(GameLoop loop) {
            this.loop = loop;
        }

        @Override
        public int currentBk2Frame() {
            return GameServices.playbackDebug().getCursorFrame();
        }

        @Override
        public BonusStageType peekBonusRequest() {
            return GameServices.level().getTransitions().peekBonusStageRequest();
        }

        @Override
        public boolean isSpecialStageRequested() {
            return GameServices.level().getTransitions().isSpecialStageRequested();
        }

        @Override
        public GameMode currentMode() {
            return loop.getCurrentGameMode();
        }
    }

    /**
     * {@link TraceReplayFixture} adapter that resolves the sprite/gameplay mode
     * LIVE from {@code GameServices}/{@code SessionManager} on every call, rather
     * than caching them at construction time -- required because
     * {@link TraceReplayDriver#start} performs its own full reset + team
     * registration + level load, which would stale a pre-cached sprite.
     *
     * <p>The BK2-driven stepping methods are unused by the chain drive (which
     * drives via {@code loop.step()} and the singleton {@code PlaybackDebugManager}
     * directly); they throw rather than silently doing the wrong thing.
     */
    protected static final class LiveEngineFixture implements TraceReplayFixture {
        private final Bk2Movie movie;

        private LiveEngineFixture(Bk2Movie movie) {
            this.movie = movie;
        }

        @Override
        public AbstractPlayableSprite sprite() {
            return GameServices.camera().getFocusedSprite();
        }

        @Override
        public GameplayModeContext gameplayMode() {
            return SessionManager.getCurrentGameplayMode();
        }

        @Override
        public int stepFrameFromRecording() {
            throw new UnsupportedOperationException(
                    "Chain test drives via loop.step(); fixture-driven stepping is unused");
        }

        @Override
        public int skipFrameFromRecording() {
            throw new UnsupportedOperationException(
                    "Chain test drives via loop.step(); fixture-driven stepping is unused");
        }

        @Override
        public int consumeRecordingFrameInputOnly() {
            throw new UnsupportedOperationException(
                    "Chain test drives via loop.step(); fixture-driven stepping is unused");
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            throw new UnsupportedOperationException(
                    "Chain test drives via loop.step(); fixture-driven stepping is unused");
        }

        @Override
        public int peekRecordingInputAt(int offset) {
            if (movie == null) {
                return -1;
            }
            int targetIndex = GameServices.playbackDebug().getCursorFrame() + offset;
            if (targetIndex < 0 || targetIndex >= movie.getFrameCount()) {
                return -1;
            }
            Bk2FrameInput frameInput = movie.getFrame(targetIndex);
            int mask = frameInput.p1InputMask();
            if (frameInput.p1ActionMask() != 0) {
                mask |= AbstractPlayableSprite.INPUT_JUMP;
            }
            return mask;
        }
    }
}
