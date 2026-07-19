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
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;
import com.openggf.trace.replay.TraceReplayDriver;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chain integration test for a level -> bonus-stage -> level round trip
 * (spec: docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md,
 * plan (c) Task 4). Drives ONE continuous {@link GameLoop} through all three
 * segments of a {@link TraceRunManifest}, asserting the engine organically
 * raises each transition and that boundary state (rings / star-post /
 * emeralds) carries over.
 *
 * <p>Skip-if-missing: both run directories are absent until the gumball and
 * pachinko round-trip recordings land, so both test methods currently SKIP
 * cleanly via {@link Assumptions#assumeTrue}. Comparison-only throughout: no
 * trace field is ever hydrated into engine state; every comparator observes
 * an engine that reached its position by playing the recorded inputs.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBonusRoundTripChain {

    private static final Path RUN_DIR_GUMBALL = Path.of(
            "src", "test", "resources", "traces", "s3k", "runs", "s3k-aiz-gumball-roundtrip");
    private static final Path RUN_DIR_PACHINKO = Path.of(
            "src", "test", "resources", "traces", "s3k", "runs", "s3k-aiz-pachinko-roundtrip");

    private static final Path REPORT_OUTPUT_DIR = Path.of("target", "trace-reports");

    /**
     * Safety cap for the fade -> TITLE_CARD -> {@code BONUS_STAGE} (and back)
     * mode-transition waits, which have no boundary-window semantics of their
     * own (unlike {@link TraceRunReplayWalker#awaitBoundary}, which fails
     * closed against the manifest's recorded edge). Sized generously against
     * {@link TraceRunReplayWalker#BOUNDARY_WINDOW_FRAMES} so a hung transition
     * fails the test instead of looping forever.
     */
    private static final int MAX_MODE_TRANSITION_FRAMES = TraceRunReplayWalker.BOUNDARY_WINDOW_FRAMES;

    @Test
    void gumballRoundTrip() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(RUN_DIR_GUMBALL),
                "Run directory not found: " + RUN_DIR_GUMBALL);
        runChain(RUN_DIR_GUMBALL);
    }

    @Test
    void pachinkoRoundTrip() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(RUN_DIR_PACHINKO),
                "Run directory not found: " + RUN_DIR_PACHINKO);
        runChain(RUN_DIR_PACHINKO);
    }

    private void runChain(Path runDir) throws Exception {
        // --- Step 1: load + validate manifest, plan segments ---------------
        TraceRunManifest run;
        try {
            run = TraceRunManifest.load(runDir.resolve("run_manifest.json"));
        } catch (IOException e) {
            throw new AssertionError("Failed to load run manifest: " + runDir, e);
        }
        List<TraceRunReplayWalker.SegmentPlan> plans;
        try {
            plans = TraceRunReplayWalker.plan(run, runDir);
        } catch (IllegalStateException e) {
            throw new AssertionError("Manifest validation failed for " + runDir, e);
        }
        assertTrue(plans.size() >= 3,
                "Expected at least 3 segments (level, bonus interior, level) in " + runDir);

        TraceRunReplayWalker.SegmentPlan seg0 = plans.get(0);
        TraceRunReplayWalker.SegmentPlan seg1 = plans.get(1);
        TraceRunReplayWalker.SegmentPlan seg2 = plans.get(2);
        assertEquals("level", seg0.segment().kind(), "Segment 0 must be a level segment");
        assertEquals("bonus_stage", seg1.segment().kind(), "Segment 1 must be a bonus_stage segment");
        assertEquals("level", seg2.segment().kind(), "Segment 2 must be a level segment");

        TraceRunManifest.Transition entryTransition = seg1.entryBoundary();
        TraceRunManifest.Transition exitTransition = seg1.exitBoundary();
        assertNotNull(entryTransition, "Segment 1 (bonus interior) must have an entry boundary");
        assertNotNull(exitTransition, "Segment 1 (bonus interior) must have an exit boundary");

        Integer seg0Zone = seg0.segment().zoneId();
        Integer seg0Act = seg0.segment().act();
        assertNotNull(seg0Zone, "Segment 0 must declare a zone_id");
        assertNotNull(seg0Act, "Segment 0 must declare an act");

        // --- Step 2: boot segment 0, then replace the driver's observer ----
        TraceData trace0 = seg0.trace();
        Path bk2Path = resolveRunBk2(runDir, run.sourceBk2());
        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);

        // Mirrors TestPachinkoTitleCardIntegration's engine setup: a
        // HeadlessTestFixture build initializes the headless engine
        // (GraphicsManager.initHeadless, TestEnvironment.resetPerTest) before
        // a real GameLoop is constructed. Its sprite()/gameplayMode() are
        // never read afterward -- TraceReplayDriver.start() performs its own
        // full reset + team registration + level load, so a stale cached
        // sprite reference would desync from the engine's actual roster.
        HeadlessTestFixture.builder().withZoneAndAct(seg0Zone, seg0Act).build();
        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);

        // Must run before driver.start()'s internal loadZoneAndAct (mirrors
        // AbstractTraceReplayTest / TraceCaptureTool step 3: recorded team,
        // cross-game off, S3K intro-skip derived from trace metadata).
        TraceReplaySessionBootstrap.prepareConfiguration(trace0, trace0.metadata());

        LiveEngineFixture fixture = new LiveEngineFixture(movie);
        TraceReplayDriver driver = new TraceReplayDriver(
                trace0, movie, fixture, loop, fixture::sprite, () -> { });
        driver.start(seg0Zone, seg0Act);

        PlaybackDebugManager playback = GameServices.playbackDebug();
        RealEngineHooks hooks = new RealEngineHooks(loop);
        TraceRunReplayWalker.BoundaryProbe probe = new TraceRunReplayWalker.BoundaryProbe(hooks);
        // Replace the raw comparator TraceReplayDriver.start() installed
        // (TraceReplayDriver.java:153) with the probe; the probe is the only
        // observer for the rest of the chain, delegating comparison to
        // whichever segment comparator is currently attached.
        playback.setFrameObserver(probe);
        probe.setDelegate(driver.comparator());

        // --- Step 3: step + compare segment 0 until its exit boundary ------
        TraceRunReplayWalker.BoundaryObservation obs0 =
                TraceRunReplayWalker.awaitBoundary(probe, entryTransition, loop::step);
        assertTrue(obs0.observed(), "Segment 0 exit boundary (" + entryTransition.entryKind()
                + ") was never observed within the boundary window for " + runDir);
        writeChainSegmentReport(run.runId(), 0, driver.comparator());

        // --- Step 4: handoff into the bonus interior ------------------------
        // Transition frames (fade, TITLE_CARD) are uncompared; detach so the
        // probe forwards nothing while GameLoop runs the real transition.
        probe.setDelegate(null);
        int transitionFrames = 0;
        while (loop.getCurrentGameMode() != GameMode.BONUS_STAGE) {
            loop.step();
            transitionFrames++;
            assertTrue(transitionFrames < MAX_MODE_TRANSITION_FRAMES,
                    "Mode never reached BONUS_STAGE within " + MAX_MODE_TRANSITION_FRAMES
                            + " frames of the entry boundary for " + runDir);
        }

        // The BK2 cursor froze during the transition (fade skips the
        // PlaybackDebugManager#onLevelFrameAdvanced advance; TITLE_CARD
        // advances nothing) -- re-seek to the interior's recorded offset.
        // startSession does NOT clear frameObserver (only endSession does),
        // so the probe stays installed with no re-registration needed; it
        // DOES call clearLastAppliedState(), resetting the forced-input
        // edge-detection counters (previousActionMask/previousStartPressed)
        // to a clean baseline for the interior's first frame, which is the
        // correct behavior here (no residual edge state leaks in from the
        // level segment).
        playback.startSession(movie, seg1.segment().bk2FrameOffset());
        LiveTraceComparator seg1Comparator =
                new LiveTraceComparator(seg1.trace(), ToleranceConfig.DEFAULT, 0, fixture::sprite);
        probe.setDelegate(seg1Comparator);

        // --- Compare through the bonus interior to its exit boundary -------
        TraceRunReplayWalker.BoundaryObservation obs1 =
                TraceRunReplayWalker.awaitBoundary(probe, exitTransition, loop::step);
        assertTrue(obs1.observed(), "Bonus interior exit boundary (stage_exit) was never observed"
                + " within the boundary window for " + runDir);
        // stage_exit is latched exactly when currentMode() == LEVEL, and
        // GameLoop.exitTitleCard() releases the title-card control lock
        // before flipping the mode on that same frame, so control is already
        // restored the instant this observation lands.
        writeChainSegmentReport(run.runId(), 1, seg1Comparator);

        // --- Step 5: assert boundary state ----------------------------------
        if (exitTransition.ringsAfter() != null) {
            int actualRings = GameServices.level().getLevelGamestate().getRings();
            assertEquals(exitTransition.ringsAfter().intValue(), actualRings,
                    "Ring carry-over after bonus stage exit for " + runDir);
        }
        if (entryTransition.lastStarPostHit() != null) {
            // NOTE (finding for follow-up): LevelTransitionCoordinator clears
            // bonusStageReturnCheckpointIndex back to -1 in
            // clearBonusStageReturn(), called from GameLoop.doExitBonusStage's
            // finally block immediately after loadZoneAndAct -- i.e. well
            // before the title-card phase completes and mode returns to
            // LEVEL. By the time this assertion runs (mode == LEVEL), this
            // accessor is very likely already -1 rather than the restored
            // star-post index. Implemented per the task brief's explicit
            // contract; flagged in the task report for the star-post-restore
            // source to be revisited (e.g. RespawnState/getCheckpointState()
            // instead, which is what doExitBonusStage actually restores into).
            int actualCheckpoint = GameServices.level().getTransitions().getBonusStageReturnCheckpointIndex();
            assertEquals(entryTransition.lastStarPostHit().intValue(), actualCheckpoint,
                    "Star-post restore after bonus stage exit for " + runDir);
        }
        if (exitTransition.emeraldsAfter() != null) {
            int actualEmeralds = GameServices.gameState().getEmeraldCount();
            assertEquals(exitTransition.emeraldsAfter().intValue(), actualEmeralds,
                    "Emerald count after bonus stage exit for " + runDir);
        }

        // --- Step 6: handoff back into the return level segment ------------
        playback.startSession(movie, seg2.segment().bk2FrameOffset());
        LiveTraceComparator seg2Comparator =
                new LiveTraceComparator(seg2.trace(), ToleranceConfig.DEFAULT, 0, fixture::sprite);
        probe.setDelegate(seg2Comparator);

        int seg2FrameCount = seg2.trace().frameCount();
        for (int i = 0; i < seg2FrameCount; i++) {
            loop.step();
        }
        writeChainSegmentReport(run.runId(), 2, seg2Comparator);
    }

    /**
     * Resolves the shared BK2 for a run. Follows the same shared-movie
     * convention as {@code AbstractTraceReplayTest.resolveBk2File}
     * (sibling {@code _movies/<source_bk2>} directory at the game root), one
     * level up from {@code runs/<run-id>/} since a run directory nests one
     * level deeper than a plain per-act trace directory. Falls back to a
     * copy inside the run directory itself. Assumption: real run directories
     * do not exist yet (both are skip-if-missing today), so this path is
     * unexercised; documented here for whoever lands the recordings.
     */
    private static Path resolveRunBk2(Path runDir, String sourceBk2) throws IOException {
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

    private void writeChainSegmentReport(String runId, int segmentIndex, LiveTraceComparator comparator)
            throws IOException {
        Files.createDirectories(REPORT_OUTPUT_DIR);
        Path jsonPath = REPORT_OUTPUT_DIR.resolve(runId + "_seg" + segmentIndex + "_report.json");
        Files.writeString(jsonPath, buildComparatorSummaryJson(comparator));
        assertTrue(Files.exists(jsonPath), "Chain segment report must be written: " + jsonPath);
    }

    /**
     * {@link LiveTraceComparator} never builds a {@link com.openggf.trace.DivergenceReport}
     * itself (that is {@code TraceBinder}'s job inside {@code AbstractTraceReplayTest});
     * the visual launcher ({@code TraceSessionLauncher}) only reads the
     * comparator's counters/ring-buffer for an on-screen HUD, it never writes
     * a report file either. So this writes a lightweight summary JSON from
     * the comparator's own accessors (error/warning/lag counts plus the
     * recent-mismatch ring buffer) instead of routing through
     * {@code AbstractTraceReplayTest.writeReport}'s {@code DivergenceReport}
     * shape, which this comparator has no accessor for.
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

    /** Reads engine boundary state through real production accessors. */
    private static final class RealEngineHooks implements TraceRunReplayWalker.EngineHooks {
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
     * {@link TraceReplayFixture} adapter that resolves the sprite/gameplay
     * mode LIVE from {@code GameServices}/{@code SessionManager} on every
     * call, rather than caching them at construction time. This is required
     * because {@link TraceReplayDriver#start} performs its own full
     * reset + team-registration + level-load internally; a fixture built
     * beforehand (the {@code HeadlessTestFixture} pattern) would cache a
     * sprite instance that becomes stale the moment that reset runs.
     * Modeled on {@code TraceCaptureTool.CaptureFixture}, which solves the
     * same problem for the headless capture tool.
     *
     * <p>The BK2-driven stepping methods are unused by this test (which
     * drives via {@code loop.step()} and the singleton
     * {@code PlaybackDebugManager} directly, matching the BONUS_STAGE
     * playback bridge); they throw rather than silently doing the wrong
     * thing if some bootstrap path unexpectedly calls them.
     */
    private static final class LiveEngineFixture implements TraceReplayFixture {
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
