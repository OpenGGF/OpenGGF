package com.openggf.tests.trace.runs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.PlaybackDebugManager;
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
 * each via its own lane subclass: {@link TestS3kMultiBonusSpecialStageRunChain}
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
    protected void runChain(Path runDir) throws Exception {
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

        // Mirrors TestPachinkoTitleCardIntegration's engine setup: a
        // HeadlessTestFixture build initializes the headless engine before a real
        // GameLoop is constructed. Its sprite()/gameplayMode() are never read
        // afterward -- TraceReplayDriver.start() performs its own full reset +
        // team registration + level load, so a stale cached sprite reference
        // would desync from the engine's actual roster.
        HeadlessTestFixture.builder().withZoneAndAct(bootZone, bootAct).build();
        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);

        // Must run before driver.start()'s internal loadZoneAndAct (recorded
        // team, cross-game off, S3K intro-skip derived from trace metadata).
        TraceReplaySessionBootstrap.prepareConfiguration(trace0, trace0.metadata());

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
                BoundaryObservation obs =
                        TraceRunReplayWalker.awaitBoundary(probe, exit, stepCap, loop::step);
                assertTrue(obs.observed(),
                        "Interior exit boundary (stage_exit) was never observed within the "
                                + "boundary window for " + runDir);
                maybeWriteReport(run.runId(), i, activeComparator);
                assertReturnBoundary(plans, i, runDir);
                activeComparator = attachLevelSegment(playback, probe, movie, plans.get(i + 1), fixture);
                i++;
            } else {
                // This segment is a LEVEL; its exit is an ENTRY boundary into the
                // interior at i+1. Await the transient entry request, then hand
                // off into the interior mode.
                BoundaryObservation obs =
                        TraceRunReplayWalker.awaitBoundary(probe, exit, stepCap, loop::step);
                assertTrue(obs.observed(), "Segment exit boundary (" + exit.entryKind()
                        + ") was never observed within the boundary window for " + runDir);
                maybeWriteReport(run.runId(), i, activeComparator);
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
            case POSITIONAL_RESTORE -> assertPositionalRestore(entry, runDir);
            case CHECKPOINT_RESTORE -> assertCheckpointRestore(entry, runDir);
            case NEXT_ACT -> assertNextActAdvance(plans, interiorIndex, returnLevel, runDir);
            case RINGS_EMERALDS_ONLY -> { /* rings + emeralds only, asserted below */ }
        }
        assertRingsAndEmeralds(exit, runDir);
    }

    /** S2 starpost_special: player restored to Saved_x/y (ROM Saved_x/y_pos). */
    protected void assertPositionalRestore(TraceRunManifest.Transition entry, Path runDir) {
        AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
        assertNotNull(sprite, "Focused sprite missing on special-stage return for " + runDir);
        if (entry.savedXPos() != null) {
            assertEquals(entry.savedXPos().intValue(), (int) sprite.getCentreX(),
                    "Saved_x_pos restore after special-stage return for " + runDir);
        }
        if (entry.savedYPos() != null) {
            assertEquals(entry.savedYPos().intValue(), (int) sprite.getCentreY(),
                    "Saved_y_pos restore after special-stage return for " + runDir);
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
            assertEquals(engineAct(returnAct).intValue(), GameServices.level().getCurrentAct(),
                    "Next-act advance (act) after special-stage return for " + runDir);
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
     */
    protected void assertRingsAndEmeralds(TraceRunManifest.Transition exit, Path runDir) {
        if (exit.ringsAfter() != null) {
            int actualRings = GameServices.level().getLevelGamestate().getRings();
            assertEquals(exit.ringsAfter().intValue(), actualRings,
                    "Ring carry-over after stage exit for " + runDir);
        }
        if (exit.emeraldsAfter() != null) {
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

    private static void stepFrames(GameLoop loop, int frameCount) {
        for (int f = 0; f < frameCount; f++) {
            loop.step();
        }
    }

    /**
     * Steps until the mode reaches {@code target} or the step cap is exhausted.
     * The cap is the same manifest-derived bound {@link TraceRunReplayWalker#awaitBoundary}
     * uses, so a hung transition fails fast instead of looping forever.
     */
    protected int waitForMode(GameLoop loop, GameMode target, int maxSteps) {
        int steps = 0;
        while (loop.getCurrentGameMode() != target) {
            loop.step();
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
            loop.step();
            steps++;
            if (steps >= maxSteps) {
                throw new AssertionError("Mode never left " + from + " within "
                        + maxSteps + " frames (expected an act/zone title-card cycle)");
            }
        }
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
