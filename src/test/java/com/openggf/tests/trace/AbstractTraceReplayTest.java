package com.openggf.tests.trace;

import com.openggf.Engine;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.Aiz2BossEndSequenceState;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSignpostInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseDebugHit;
import com.openggf.level.objects.TouchResponseDebugState;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.s3k.S3kCheckpointProbe;
import com.openggf.trace.DivergenceGroup;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.EngineDiagnostics;
import com.openggf.trace.EngineSnapshot;
import com.openggf.trace.EngineSidekickCpuState;
import com.openggf.trace.EngineNearbyObject;
import com.openggf.trace.EngineNearbyObjectFormatter;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TouchResponseDebugHitFormatter;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceEventFormatter;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.tests.trace.s3k.S3kRequiredCheckpointGuard;
import com.openggf.tests.trace.s3k.S3kReplayCheckpointDetector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for trace replay tests. Subclasses provide game/zone/act/path;
 * this class handles level loading, BK2 playback, per-frame comparison, and report output.
 *
 * <p>Originally used JUnit 4 because the ROM fixture was exposed as a JUnit 4 rule.
 */
public abstract class AbstractTraceReplayTest {
    private static final Logger LOGGER = Logger.getLogger(AbstractTraceReplayTest.class.getName());

    /** Which game ROM this test requires. */
    protected abstract SonicGame game();

    /** Zone index (0-based). */
    protected abstract int zone();

    /** Act index (0-based). */
    protected abstract int act();

    /** Path to the trace directory containing metadata.json, physics.csv(.gz), and optionally a .bk2. */
    protected abstract Path traceDirectory();

    /** Override to supply custom tolerances. */
    protected ToleranceConfig tolerances() {
        return ToleranceConfig.DEFAULT;
    }

    /** Override to force a specific pre-trace oscillation frame count. Return -1 to use metadata. */
    protected int overridePreTraceOscFrames() { return -1; }

    /** Override to change report output directory. */
    protected Path reportOutputDir() {
        return Path.of("target/trace-reports");
    }

    /** Override only for explicitly diagnostic trace fixtures that are not release gates. */
    protected boolean allowDiagnosticOnlyTraceReplay() {
        return false;
    }

    /** Override only when warning-only reports are deliberately diagnostic debt. */
    protected boolean allowDiagnosticOnlyWarnings() {
        return false;
    }

    @Test
    public void replayMatchesTrace() throws Exception {
        // 0. Skip if trace directory or required files are missing
        Path traceDir = traceDirectory();
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);
        Assumptions.assumeTrue(Files.exists(traceDir.resolve("metadata.json")), "metadata.json not found in " + traceDir);
        Assumptions.assumeTrue(hasTracePayload(traceDir, "physics.csv"), "physics.csv(.gz) not found in " + traceDir);

        // 1. Load trace data (metadata is needed to resolve a shared, deduplicated BK2)
        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        List<String> releaseBlockers = TraceReplayBootstrap.releaseBlockersForTraceReplay(trace);
        if (!releaseBlockers.isEmpty() && !allowDiagnosticOnlyTraceReplay()) {
            fail(String.join(System.lineSeparator(), releaseBlockers));
        }

        // 2. Resolve BK2: prefer a shared movie referenced by metadata.source_bk2 (stored once
        //    under <game>/_movies/), else a legacy per-dir .bk2 copy.
        Path bk2Path = resolveBk2File(traceDir, meta);
        Assumptions.assumeTrue(bk2Path != null,
                "No BK2 found for " + traceDir + " (no _movies/<source_bk2> and no .bk2 in dir)");
        boolean requiresFreshLevelLoad =
                TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace);

        // 3. Validate test configuration matches metadata
        validateMetadata(meta);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);

        // 4. Load level and create fixture
        SharedLevel sharedLevel = requiresFreshLevelLoad
                ? null
                : SharedLevel.load(game(), zone(), act());
        try {
            HeadlessTestFixture.Builder fixtureBuilder = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));
            if (sharedLevel != null) {
                fixtureBuilder.withSharedLevel(sharedLevel);
            } else {
                fixtureBuilder.withZoneAndAct(zone(), act());
            }
            if (shouldApplyMetadataStartPosition(trace, meta)) {
                fixtureBuilder
                        .startPosition(meta.startX(), meta.startY())
                        .startPositionIsCentre();
            }
            HeadlessTestFixture fixture = fixtureBuilder.build();
            TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);

            if (GameServices.debugOverlay() != null) {
                GameServices.debugOverlay().setEnabled(DebugOverlayToggle.TOUCH_RESPONSE, true);
            }

            // 4. Shared replay bootstrap: timing prelude, read-only snapshot
            //    reporting, and replay cursor selection.
            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture,
                            overridePreTraceOscFrames());
            TraceReplayBootstrap.SnapshotReport snapshotReport = boot.snapshotReport();
            TraceReplayBootstrap.ReplayStartState replayStart = boot.replayStart();
            ObjectManager om = GameServices.level().getObjectManager();
            List<TraceEvent.ObjectStateSnapshot> preTraceSnapshots =
                    trace.preTraceObjectSnapshots();
            if (!preTraceSnapshots.isEmpty() && om != null) {
                System.out.printf(
                        "Reported %d/%d pre-trace object snapshots (%d warnings)%n",
                        snapshotReport.matched(), snapshotReport.attempted(),
                        snapshotReport.warnings().size());
                for (String warning : snapshotReport.warnings()) {
                    System.out.println("  WARN: " + warning);
                }
            }

            // 5. Run frame-by-frame comparison
            TraceBinder binder = new TraceBinder(tolerances());

            // Frame-0 bootstrap comparison. Active only for traces recorded
            // against the post-universal-title-card engine (TraceMetadata
            // .nativePreludeMode() derived from lua_script_version >= 9.2-s2);
            // a no-op return for legacy traces. Surfaces a BootstrapDivergence
            // category in the final DivergenceReport for any mismatch between
            // engine state at frame 0 and the recorded ROM frame-0 snapshot
            // (player history ring, Tails CPU state, per-slot SST values).
            EngineSnapshot frameZero = captureEngineSnapshot(fixture);
            binder.compareBootstrapFrame0(trace, frameZero);

            if ("s3k".equals(meta.game())) {
                replayS3kTrace(trace, meta, fixture, binder, replayStart);
            } else {
                int startTraceIndex = replayStart.startingTraceIndex();
                for (int i = startTraceIndex; i < trace.frameCount(); i++) {
                    TraceFrame expected = trace.getFrame(i);

                    // Drive replay from recorded ROM counters instead of inferring
                    // lag from unchanged physics state.
                    int bk2Input;
                    TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                    TraceExecutionPhase phase =
                        TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
                    if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                        bk2Input = fixture.skipFrameFromRecording();
                    } else {
                        bk2Input = fixture.stepFrameFromRecording();
                    }

                    if (!binder.validateInput(expected, bk2Input)) {
                        fail(String.format(
                            "Input alignment error at trace frame %d: " +
                            "BK2 input=0x%04X, trace input=0x%04X. " +
                            "Check bk2_frame_offset in metadata.json.",
                            i, bk2Input, expected.input()));
                    }
                    if (!TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                        continue;
                    }
                    TraceFrame comparisonExpected =
                            TraceReplayBootstrap.frameForGameplayComparison(
                                    trace, i, previous, expected, phase);

                    // ROM stores centre coordinates at $D008/$D00C. With startPositionIsCentre(),
                    // the sprite's xPixel/yPixel are set to the correct top-left position,
                    // so getCentreX()/getCentreY() now return the actual ROM centre values.
                    var sprite = fixture.sprite();

                    // Capture engine-side diagnostic state for context window
                    EngineDiagnostics engineDiag = captureEngineDiagnostics(sprite);
                    TraceCharacterState actualSidekick = captureFirstSidekickState();
                    String secondaryCharacterLabel = meta.recordedSidekicks().isEmpty()
                            ? "sidekick"
                            : meta.recordedSidekicks().getFirst();
                    String romDiag = combineDiagnostics(
                            comparisonExpected.hasExtendedData() ? comparisonExpected.formatDiagnostics() : "",
                            formatCharacterDiagnostics(secondaryCharacterLabel, expected.sidekick()));
                    romDiag = combineDiagnostics(
                            romDiag,
                            TraceEventFormatter.summariseFrameEvents(trace.getEventsForFrame(i)));
                    String engineDiagText = combineDiagnostics(
                            engineDiag.format(),
                            formatCharacterDiagnostics(secondaryCharacterLabel, actualSidekick));
                    TraceEvent.CpuState expectedSidekickCpu =
                            trace.cpuStateForFrame(comparisonExpected.frame(), secondaryCharacterLabel);
                    EngineSidekickCpuState actualSidekickCpu = captureFirstSidekickCpuState();
                    binder.compareFrame(comparisonExpected,
                        sprite.getCentreX(), sprite.getCentreY(),
                        sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                        sprite.getAngle(),
                        sprite.getAir(), sprite.getRolling(),
                        sprite.getGroundMode().ordinal(), romDiag,
                        EngineDiagnostics.formattedWithCamera(
                                engineDiag.cameraX(), engineDiag.cameraY(), engineDiagText),
                        secondaryCharacterLabel, actualSidekick,
                        expectedSidekickCpu, actualSidekickCpu);

                }
            }

            // 6. Build report
            DivergenceReport report = "s3k".equals(meta.game())
                    ? binder.buildReport(trace)
                    : binder.buildReport();

            // 7. Write report if there are any divergences
            if (report.hasErrors() || report.hasWarnings()) {
                writeReport(report, meta);
            }

            // 8. Log summary
            System.out.println(report.toSummary());

            // 9. Assert no errors
            if (report.hasErrors()) {
                System.err.println("\n=== Context window around first error ===");
                System.err.println(report.getContextWindow(firstReportErrorFrame(report), 10));
            }
            assertReportHasNoReleaseBlockingDivergences(report);
        } finally {
            if (sharedLevel != null) {
                sharedLevel.dispose();
            } else {
                TestEnvironment.resetAll();
            }
        }
    }

    protected void assertReportHasNoReleaseBlockingDivergences(DivergenceReport report) {
        if (report.hasErrors()) {
            fail(report.toSummary());
        }
        if (report.hasWarnings() && !allowDiagnosticOnlyWarnings()) {
            fail("Trace replay warning report is release-blocking by default: "
                    + report.toSummary());
        }
    }

    /**
     * Capture a read-only snapshot of engine state at frame 0 for the
     * bootstrap comparator. The comparator only activates for traces with
     * {@code lua_script_version >= 9.2-s2} (see
     * {@link TraceMetadata#nativePreludeMode()}); for legacy traces this
     * snapshot is built but discarded. Captures whatever is readily
     * available from the fixture; fields without an accessible source are
     * left null/empty (the comparator emits WARNING entries for those).
     */
    private EngineSnapshot captureEngineSnapshot(HeadlessTestFixture fixture) {
        AbstractPlayableSprite sprite = fixture != null ? fixture.sprite() : null;
        short[] xHistory = sprite != null ? sprite.copyXHistory() : null;
        short[] yHistory = sprite != null ? sprite.copyYHistory() : null;
        short[] inputHistory = sprite != null ? sprite.copyInputHistory() : null;
        byte[] statusHistory = sprite != null ? sprite.copyStatusHistory() : null;
        int historyPos = sprite != null ? sprite.historyPos() : 0;

        EngineSnapshot.SidekickCpuView tailsCpu = captureFirstSidekickCpuSnapshot();

        // Per-slot SST snapshots are still left empty; they require a stable
        // ObjectManager slot extraction path. Sidekick CPU state is now captured
        // read-only so bootstrap reports can flag native-prelude CPU drift.
        return EngineSnapshot.capture(xHistory, yHistory, inputHistory, statusHistory,
                historyPos, tailsCpu, java.util.Map.of());
    }

    private void validateMetadata(TraceMetadata meta) {
        String expectedGameId = switch (game()) {
            case SONIC_1 -> "s1";
            case SONIC_2 -> "s2";
            case SONIC_3K -> "s3k";
        };
        assertEquals(expectedGameId, meta.game(), "Metadata game mismatch (test says " + game()
            + " but metadata says " + meta.game() + ")");
    }

    private boolean shouldApplyMetadataStartPosition(TraceData trace, TraceMetadata meta) {
        // Power-on traces can begin before the level is actually live. In those
        // cases start_x/start_y reflect whatever was left in Player_1 RAM at the
        // recorder start, not the first replayable in-level position.
        return TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace);
    }

    private void replayS3kTrace(TraceData trace, TraceMetadata meta,
                                HeadlessTestFixture fixture, TraceBinder binder,
                                TraceReplayBootstrap.ReplayStartState replayStart) {
        int driveTraceIndex = replayStart.startingTraceIndex();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : driveTraceIndex > 0 ? trace.getFrame(driveTraceIndex - 1) : null;
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();
        S3kRequiredCheckpointGuard checkpointGuard = new S3kRequiredCheckpointGuard();

        if (replayStart.hasSeededTraceState()) {
            TraceFrame seededFrame = trace.getFrame(replayStart.seededTraceIndex());
            TraceReplayBootstrap.ReplayPrimaryState seededPrimary =
                    TraceReplayBootstrap.capturePrimaryReplayStateForComparison(
                            trace, seededFrame, fixture.sprite());
            EngineDiagnostics engineDiag = captureEngineDiagnostics(fixture.sprite());
            String romDiag = combineDiagnostics(
                    seededFrame.hasExtendedData() ? seededFrame.formatDiagnostics() : "",
                    TraceEventFormatter.summariseFrameEvents(
                            trace.getEventsForFrame(replayStart.seededTraceIndex())));
            TraceCharacterState seededSidekick = captureFirstSidekickState();
            String secondaryCharacterLabel = meta.recordedSidekicks().isEmpty()
                    ? "sidekick"
                    : meta.recordedSidekicks().getFirst();

            TraceEvent.CpuState expectedSidekickCpu =
                    trace.cpuStateForFrame(seededFrame.frame(), secondaryCharacterLabel);
            EngineSidekickCpuState actualSidekickCpu = captureFirstSidekickCpuState();
            binder.compareFrame(seededFrame,
                    seededPrimary.x(), seededPrimary.y(),
                    seededPrimary.xSpeed(), seededPrimary.ySpeed(), seededPrimary.gSpeed(),
                    seededPrimary.angle(),
                    seededPrimary.air(), seededPrimary.rolling(),
                    seededPrimary.groundMode(), romDiag,
                    engineDiag,
                    secondaryCharacterLabel,
                    seededSidekick,
                    expectedSidekickCpu,
                    actualSidekickCpu);

            for (int frame = 0; frame <= replayStart.seededTraceIndex(); frame++) {
                for (TraceEvent event : trace.getEventsForFrame(frame)) {
                    if (event instanceof TraceEvent.Checkpoint traceCheckpoint) {
                        detector.seedCheckpoint(traceCheckpoint.name());
                    }
                }
            }
        } else if (driveTraceIndex > 0) {
            // Seed only the diagnostic checkpoint detector for trace prefix
            // frames that the replay policy intentionally starts after.
            for (int frame = 0; frame < driveTraceIndex; frame++) {
                for (TraceEvent event : trace.getEventsForFrame(frame)) {
                    if (event instanceof TraceEvent.Checkpoint traceCheckpoint) {
                        detector.seedCheckpoint(traceCheckpoint.name());
                    }
                }
            }
        } else {
            // Seed the diagnostic detector for frame-0 checkpoints without
            // changing the comparison cursor.
            for (TraceEvent event : trace.getEventsForFrame(0)) {
                if (event instanceof TraceEvent.Checkpoint traceCheckpoint) {
                    detector.seedCheckpoint(traceCheckpoint.name());
                }
            }
        }

        // Align SpriteManager.frameCounter with ROM Level_frame_counter so Tails-CPU
        // AI gates that read (Level_frame_counter & MASK) fire on the same trace
        // frames as the ROM (sonic3k.asm:26761 loc_13E7C dx-256-frame check,
        // sonic3k.asm:26775 loc_13E9C 64-frame jump-cadence check, etc.).
        //
        // The trace records each frame's gfc. The first iteration steps fc by one,
        // so for AI on iter K=K_start to see fc == T_K_start.gfc (== ROM's
        // Level_frame_counter at T_K_start logic), we pre-set fc =
        // T_(K_start-1).gfc here. The trace's gfc increments monotonically per
        // recorded gameplay frame and stays put on lag/VBLANK_ONLY frames, while
        // the engine's fc++/skip behaviour matches that exact pattern, so this
        // single pre-set keeps the counters synced for the entire replay.
        //
        // Concrete examples:
        //   * CNZ:  T_0.gfc=1   (gameplay starts immediately) → fc 0→1, then iter K=1
        //                                 step fc 1→2 = ROM.gfc(T_1)=2 ✓
        //   * AIZ:  T_289.gfc=0 (still inside intro)         → fc 0→0  (no change),
        //                                 then iter K=290 step fc 0→1 = ROM.gfc(T_290)=1 ✓
        TraceReplaySessionBootstrap.alignFrameCountersForReplayStart(
                previousDriveFrame,
                driveTraceIndex < trace.frameCount() ? trace.getFrame(driveTraceIndex) : null);
        while (driveTraceIndex < trace.frameCount()) {
            TraceFrame driveFrame = trace.getFrame(driveTraceIndex);
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previousDriveFrame, driveFrame);
            int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                    ? fixture.skipFrameFromRecording()
                    : TraceReplayBootstrap.shouldUsePreviousRecordingInputForTraceReplay(trace)
                            ? fixture.stepFrameFromRecordingUsingPreviousInput()
                            : fixture.stepFrameFromRecording();

            if (!binder.validateInput(driveFrame, bk2Input)) {
                fail(String.format(
                    "Input alignment error at trace frame %d: " +
                    "BK2 input=0x%04X, trace input=0x%04X. " +
                    "Check bk2_frame_offset in metadata.json.",
                    driveTraceIndex, bk2Input, driveFrame.input()));
            }

            S3kCheckpointProbe probe = captureS3kProbe(driveFrame.frame(), fixture.sprite());
            TraceEvent.Checkpoint engineCheckpoint = detector.observe(probe);

            if (TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                TraceReplayBootstrap.ReplayPrimaryState actualPrimary =
                        TraceReplayBootstrap.capturePrimaryReplayStateForComparison(
                                trace, driveFrame, fixture.sprite());
                EngineDiagnostics engineDiag = captureEngineDiagnostics(fixture.sprite());
                String romDiag = combineDiagnostics(
                        driveFrame.hasExtendedData() ? driveFrame.formatDiagnostics() : "",
                        TraceEventFormatter.summariseFrameEvents(trace.getEventsForFrame(driveTraceIndex)));
                TraceCharacterState actualSidekick = captureFirstSidekickState();
                String secondaryCharacterLabel = meta.recordedSidekicks().isEmpty()
                        ? "sidekick"
                        : meta.recordedSidekicks().getFirst();
                TraceEvent.CpuState expectedSidekickCpu =
                        trace.cpuStateForFrame(driveFrame.frame(), secondaryCharacterLabel);
                EngineSidekickCpuState actualSidekickCpu = captureFirstSidekickCpuState();
                binder.compareFrame(driveFrame,
                        actualPrimary.x(), actualPrimary.y(),
                        actualPrimary.xSpeed(), actualPrimary.ySpeed(), actualPrimary.gSpeed(),
                        actualPrimary.angle(),
                        actualPrimary.air(), actualPrimary.rolling(),
                        actualPrimary.groundMode(), romDiag,
                        engineDiag,
                        secondaryCharacterLabel,
                        actualSidekick,
                        expectedSidekickCpu,
                        actualSidekickCpu);
            }

            TraceEvent.Checkpoint traceCheckpoint = trace.latestCheckpointAtOrBefore(driveTraceIndex);
            if (traceCheckpoint != null && traceCheckpoint.frame() == driveTraceIndex) {
                checkpointGuard.validateStrictEntry(
                        driveTraceIndex,
                        traceCheckpoint,
                        engineCheckpoint,
                        detector.requiredCheckpointNamesReached());
            }

            driveTraceIndex++;
            previousDriveFrame = driveFrame;
        }
    }

    private S3kCheckpointProbe captureS3kProbe(int replayFrame, AbstractPlayableSprite sprite) {
        boolean resultsActive = GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(S3kResultsScreenObjectInstance.class::isInstance);
        boolean signpostActive =
                S3kSignpostInstance.activeSignpost(GameServices.level().getObjectManager()) != null;
        boolean eventsFg5 =
                GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager
                        && manager.isEventsFg5();
        boolean fireTransitionActive =
                GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager
                        && manager.isFireTransitionActive();
        boolean hczTransitionActive =
                Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive() && !resultsActive;
        var titleCardProvider = GameServices.module().getTitleCardProvider();
        boolean titleCardOverlayActive = titleCardProvider != null && titleCardProvider.isOverlayActive();
        Integer traceGameMode = resolveS3kTraceGameMode(titleCardOverlayActive);

        return new S3kCheckpointProbe(
                replayFrame,
                GameServices.level().getCurrentZone(),
                GameServices.level().getCurrentAct(),
                GameServices.level().getApparentAct(),
                traceGameMode,
                sprite.getMoveLockTimer(),
                sprite.isControlLocked(),
                sprite.isObjectControlled(),
                sprite.isHidden(),
                eventsFg5,
                fireTransitionActive,
                hczTransitionActive,
                signpostActive,
                resultsActive,
                GameServices.camera().isLevelStarted(),
                titleCardOverlayActive);
    }

    private Integer resolveS3kTraceGameMode(boolean titleCardOverlayActive) {
        Engine engine = Engine.getInstance();
        GameMode currentMode = engine != null ? engine.getCurrentGameMode() : null;
        boolean levelStarted = GameServices.camera() != null && GameServices.camera().isLevelStarted();
        if (currentMode == null) {
            return levelStarted ? 0x0C : 0x04;
        }
        return switch (currentMode) {
            case LEVEL, TITLE_CARD -> levelStarted ? 0x0C : 0x04;
            case SPECIAL_STAGE -> 0x10;
            case SPECIAL_STAGE_RESULTS -> 0x14;
            case TITLE_SCREEN, MASTER_TITLE_SCREEN -> 0x00;
            case LEVEL_SELECT -> 0x08;
            case DATA_SELECT -> 0x18;
            case CREDITS_TEXT, CREDITS_DEMO, TRY_AGAIN_END, ENDING_CUTSCENE, EDITOR, BONUS_STAGE, LEGAL_DISCLAIMER -> null;
        };
    }

    /**
     * Resolve the BK2 movie for a trace. Prefers a shared, deduplicated movie named by
     * {@code metadata.source_bk2} and stored once under {@code <game>/_movies/} (sibling to the
     * per-act trace dirs) — so a complete-run movie used by 18 acts is committed once, not 18×.
     * Falls back to a legacy {@code .bk2} copy inside the trace dir.
     */
    private Path resolveBk2File(Path traceDir, TraceMetadata meta) throws IOException {
        if (meta != null && meta.sourceBk2() != null && !meta.sourceBk2().isBlank()) {
            Path shared = traceDir.getParent().resolve("_movies").resolve(meta.sourceBk2());
            if (Files.exists(shared)) {
                return shared;
            }
        }
        return findBk2File(traceDir);
    }

    private Path findBk2File(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files
                .filter(p -> p.toString().endsWith(".bk2"))
                .findFirst()
                .orElse(null);
        }
    }

    private static boolean hasTracePayload(Path dir, String fileName) {
        return Files.exists(dir.resolve(fileName))
                || Files.exists(dir.resolve(fileName + ".gz"));
    }

    private static String combineDiagnostics(String base, String extra) {
        if (base == null || base.isEmpty()) {
            return extra == null ? "" : extra;
        }
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        return base + " | " + extra;
    }

    private static String formatCharacterDiagnostics(String label, TraceCharacterState state) {
        if (state == null) {
            return "";
        }
        return state.formatDiagnostics(label);
    }

    private TraceCharacterState captureFirstSidekickState() {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return null;
        }
        return captureCharacterState(spriteManager.getSidekicks().getFirst());
    }

    private EngineSnapshot.SidekickCpuView captureFirstSidekickCpuSnapshot() {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return null;
        }
        SidekickCpuController controller = spriteManager.getSidekicks().getFirst().getCpuController();
        if (controller == null) {
            return null;
        }
        return new EngineSnapshot.SidekickCpuView(
                controller.getDiagnosticControlCounter(),
                controller.getDiagnosticRespawnCounter(),
                controller.getDiagnosticRomCpuRoutine(),
                (short) controller.targetX(),
                (short) controller.targetY(),
                controller.getDiagnosticInteractId(),
                controller.getDiagnosticJumpingFlag() != 0);
    }

    private EngineSidekickCpuState captureFirstSidekickCpuState() {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return null;
        }
        SidekickCpuController controller = spriteManager.getSidekicks().getFirst().getCpuController();
        if (controller == null) {
            return null;
        }
        return new EngineSidekickCpuState(
                controller.getDiagnosticControlCounter(),
                controller.getDiagnosticRespawnCounter(),
                controller.getDiagnosticInteractId(),
                controller.getDiagnosticRomCpuRoutine(),
                controller.targetX(),
                controller.targetY(),
                controller.getDiagnosticGeneratedHeldInput(),
                controller.getDiagnosticGeneratedPressedInput(),
                controller.getDiagnosticFollowHistorySlot(),
                controller.getDiagnosticJumpingFlag());
    }

    private TraceCharacterState captureCharacterState(AbstractPlayableSprite sprite) {
        ObjectManager om = GameServices.level() != null
                ? GameServices.level().getObjectManager() : null;
        int standOnSlot = -1;
        if (om != null) {
            ObjectInstance ridingObj = om.getRidingObject(sprite);
            if (ridingObj instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
                standOnSlot = aoi.getSlotIndex();
            }
        }

        int statusByte = 0;
        if (sprite.getDirection() == com.openggf.physics.Direction.LEFT) {
            statusByte |= 0x01;
        }
        if (sprite.getAir()) statusByte |= 0x02;
        if (sprite.getRolling()) statusByte |= 0x04;
        if (sprite.isOnObject()) statusByte |= 0x08;
        if (sprite.isInWater()) statusByte |= 0x40;

        int routine = sprite.isHurt() ? 0x04 : 0x02;

        return new TraceCharacterState(true,
                sprite.getCentreX(),
                sprite.getCentreY(),
                sprite.getXSpeed(),
                sprite.getYSpeed(),
                sprite.getGSpeed(),
                sprite.getAngle(),
                sprite.getAir(),
                sprite.getRolling(),
                sprite.getGroundMode().ordinal(),
                sprite.getXSubpixelRaw(),
                sprite.getYSubpixelRaw(),
                routine,
                statusByte,
                standOnSlot);
    }

    /**
     * Capture engine-side diagnostic state for the context window.
     * These values are NOT compared for pass/fail â€” they appear alongside
     * ROM trace diagnostics for human cross-referencing.
     */
    private EngineDiagnostics captureEngineDiagnostics(AbstractPlayableSprite sprite) {
        // Routine: S1 uses 0=init, 2=control, 4=hurt, 6=death
        int routine = sprite.isHurt() ? 0x04 : 0x02;

        // Riding object: which SST slot is the player standing on?
        int standOnSlot = -1;
        int standOnType = -1;
        ObjectManager om = GameServices.level() != null
                ? GameServices.level().getObjectManager() : null;
        if (om != null) {
            ObjectInstance ridingObj = om.getRidingObject(sprite);
            if (ridingObj instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
                standOnSlot = aoi.getSlotIndex();
                standOnType = aoi.getSpawn() != null ? aoi.getSpawn().objectId() : -1;
            }
        }

        // Ring count
        int rings = sprite.getRingCount();

        // Status byte (replicate ROM's status encoding)
        int statusByte = 0;
        if (sprite.getDirection() == com.openggf.physics.Direction.LEFT)
            statusByte |= 0x01;
        if (sprite.getAir()) statusByte |= 0x02;
        if (sprite.getRolling()) statusByte |= 0x04;
        if (sprite.isOnObject()) statusByte |= 0x08;
        if (sprite.isInWater()) statusByte |= 0x40;

        // Camera X/Y for ROM-trace cross-reference and camera_x/camera_y
        // comparison in TraceBinder.
        int camX = GameServices.camera() != null ? GameServices.camera().getX() & 0xFFFF : -1;
        int camY = GameServices.camera() != null ? GameServices.camera().getY() & 0xFFFF : -1;

        // Placement cursor state for ROMâ†”engine comparison
        int cursorIdx = -1, leftCursorIdx = -1, fwdCtr = -1, bwdCtr = -1;
        if (om != null) {
            int[] cursor = om.getPlacementCursorState();
            if (cursor != null) {
                cursorIdx = cursor[0];
                leftCursorIdx = cursor[1];
                fwdCtr = cursor[2];
                bwdCtr = cursor[3];
            }
        }

        // Subpixels for cross-referencing with ROM trace sub=(xsub,ysub)
        int xSub = sprite.getXSubpixelRaw();
        int ySub = sprite.getYSubpixelRaw();

        // Tri-state engine truth for "is this player riding any solid object?"
        // and the latched standing snapshot. These diverge from statusByte
        // bit 0x08 (live on-object flag) on platform release / walk-off /
        // mid-frame standing transitions, and are critical for diagnosing
        // late-game frontiers like WFZ f7065 where ROM transitions to
        // airborne one frame before engine drops the ride.
        int ridingObject = -1;
        int standingSnapshot = -1;
        if (om != null) {
            ridingObject = om.isRidingObject(sprite) ? 1 : 0;
            standingSnapshot = om.latestStandingSnapshot(sprite) ? 1 : 0;
        }

        String solidEvent = "";
        solidEvent = combineDiagnostics(solidEvent, String.format(
                "eng-player vel=(%04X,%04X) g=%04X dir=%s bal=%d rj=%s jump=%s shoes=%s accel=%04X max=%04X lock=%s moveLock=%d objCtrl=%s objSup=%s in=(%s,%s,%s,%s) look=%04X bias=%04X",
                sprite.getXSpeed() & 0xFFFF,
                sprite.getYSpeed() & 0xFFFF,
                sprite.getGSpeed() & 0xFFFF,
                sprite.getDirection(),
                sprite.getBalanceState(),
                sprite.getRollingJump(),
                sprite.isJumping(),
                sprite.hasSpeedShoes(),
                sprite.getRunAccel() & 0xFFFF,
                sprite.getMax() & 0xFFFF,
                sprite.isControlLocked(),
                sprite.getMoveLockTimer(),
                sprite.isObjectControlled(),
                sprite.isObjectControlSuppressesMovement(),
                sprite.isDownPressed(),
                sprite.isLeftPressed(),
                sprite.isRightPressed(),
                sprite.isJumpPressed(),
                sprite.getLookDelayCounter() & 0xFFFF,
                GameServices.camera() != null ? GameServices.camera().getYPosBias() & 0xFFFF : -1));
        if (om != null) {
            TouchResponseDebugState touchState = om.getTouchResponseDebugState();
            if (touchState != null) {
                solidEvent = combineDiagnostics(solidEvent, String.format(
                        "touchBox @%04X,%04X h=%d yr=%d crouch=%d",
                        touchState.getPlayerX() & 0xFFFF,
                        touchState.getPlayerY() & 0xFFFF,
                        touchState.getPlayerHeight(),
                        touchState.getPlayerYRadius(),
                        touchState.isCrouching() ? 1 : 0));
            }
            if (touchState != null && !touchState.getHits().isEmpty()) {
                solidEvent = combineDiagnostics(solidEvent,
                        TouchResponseDebugHitFormatter.summariseOverlaps(touchState.getHits()));
                solidEvent = combineDiagnostics(solidEvent,
                        TouchResponseDebugHitFormatter.summariseNearbyScans(
                                touchState.getHits(),
                                sprite.getCentreX(),
                                sprite.getCentreY()));
            }

            List<EngineNearbyObject> nearbyObjects = new ArrayList<>();
            for (ObjectInstance instance : om.getActiveObjects()) {
                if (!(instance instanceof AbstractObjectInstance aoi)) {
                    continue;
                }
                ObjectSpawn spawn = aoi.getSpawn();
                if (spawn == null) {
                    continue;
                }
                int currentX = aoi.getX();
                int currentY = aoi.getY();
                int dx = Math.abs(currentX - sprite.getCentreX());
                int dy = Math.abs(currentY - sprite.getCentreY());
                if (dx > 160 || dy > 160) {
                    continue;
                }
                TouchResponseProvider provider =
                        instance instanceof TouchResponseProvider trp ? trp : null;
                nearbyObjects.add(new EngineNearbyObject(
                        aoi.getSlotIndex(),
                        spawn.objectId(),
                        aoi.getName(),
                        currentX,
                        currentY,
                        spawn.x(),
                        spawn.y(),
                        provider != null,
                        provider != null ? provider.getCollisionFlags() : -1,
                        provider != null ? aoi.getPreUpdateCollisionFlags() : -1,
                        aoi.getPreUpdateX(),
                        aoi.getPreUpdateY(),
                        aoi.isSkipTouchThisFrame(),
                        aoi.isSkipSolidContactThisFrame(),
                        aoi.isOnScreenForTouch(),
                        aoi.traceDebugDetails()));
            }
            nearbyObjects.sort(Comparator.comparingInt(EngineNearbyObject::slot));
            solidEvent = combineDiagnostics(solidEvent,
                    EngineNearbyObjectFormatter.summarise(nearbyObjects));
            solidEvent = combineDiagnostics(solidEvent, summariseS2SkyChaseBadnikDiagnostics(om));
            solidEvent = combineDiagnostics(solidEvent, summariseSidekickStateDiagnostics(om));
            solidEvent = combineDiagnostics(solidEvent, summariseSidekickNearbyObjects(om));
            solidEvent = combineDiagnostics(solidEvent, summariseSidekickCpuDiagnostics());
            solidEvent = combineDiagnostics(solidEvent, summariseSidekickCylinderDiagnostics(om));
            solidEvent = combineDiagnostics(solidEvent, additionalEngineObjectDiagnostics(om));
        }

        return new EngineDiagnostics(routine, standOnSlot, standOnType, rings, statusByte,
                camX, camY, cursorIdx, leftCursorIdx, fwdCtr, bwdCtr, solidEvent, xSub, ySub,
                ridingObject, standingSnapshot);
    }

    private String summariseS2SkyChaseBadnikDiagnostics(ObjectManager om) {
        if (game() != SonicGame.SONIC_2 || zone() != 8 || om == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (ObjectInstance instance : om.getActiveObjects()) {
            if (!(instance instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            ObjectSpawn spawn = aoi.getSpawn();
            if (spawn == null) {
                continue;
            }
            int id = spawn.objectId() & 0xFF;
            if (id != 0x98 && id != 0x99 && id != 0x9A && id != 0x9B && id != 0x9C && id != 0xAC) {
                continue;
            }
            parts.add(String.format("sczobj s%d 0x%02X %s @%04X,%04X spawn=@%04X,%04X %s",
                    aoi.getSlotIndex(),
                    id,
                    aoi.getName(),
                    aoi.getX() & 0xFFFF,
                    aoi.getY() & 0xFFFF,
                    spawn.x() & 0xFFFF,
                    spawn.y() & 0xFFFF,
                    aoi.traceDebugDetails()));
        }
        parts.sort(String::compareTo);
        if (parts.size() > 16) {
            parts = new ArrayList<>(parts.subList(0, 16));
        }
        return parts.isEmpty() ? "sczobj none" : String.join(" | ", parts);
    }

    private String summariseSidekickStateDiagnostics(ObjectManager om) {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return "eng-tails-state none sidekick=inactive";
        }
        AbstractPlayableSprite sidekick = spriteManager.getSidekicks().getFirst();
        int standOnSlot = -1;
        int standOnType = -1;
        ObjectInstance ridingObj = om.getRidingObject(sidekick);
        if (ridingObj instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
            standOnSlot = aoi.getSlotIndex();
            standOnType = aoi.getSpawn() != null ? aoi.getSpawn().objectId() : -1;
        }
        var cam = sidekick.currentCamera();
        int camMinY = cam != null ? cam.getMinY() & 0xFFFF : -1;
        int camMaxY = cam != null ? cam.getMaxY() & 0xFFFF : -1;
        int camMaxYTarget = cam != null ? cam.getMaxYTarget() & 0xFFFF : -1;
        int cpuMaxY = sidekick.getCpuController() != null
                ? sidekick.getCpuController().getMaxYBound(camMaxY) & 0xFFFF
                : -1;
        int interactSlot = sidekick.getInteractSlotIndex();
        String interactOccupant = summariseSlotOccupant(om, interactSlot);
        return String.format(
                "eng-tails-state pos=(%04X,%04X) sub=(%04X,%04X) vel=(%04X,%04X) g=%04X dir=%s onObj=%s ride=s%d type=%02X interact=s%d[%s] st=%02X pin=%s lock=%s prs=%s boundsY=%04X/%04X/%04X cpuMax=%04X",
                sidekick.getCentreX() & 0xFFFF,
                sidekick.getCentreY() & 0xFFFF,
                sidekick.getXSubpixelRaw() & 0xFFFF,
                sidekick.getYSubpixelRaw() & 0xFFFF,
                sidekick.getXSpeed() & 0xFFFF,
                sidekick.getYSpeed() & 0xFFFF,
                sidekick.getGSpeed() & 0xFFFF,
                sidekick.getDirection(),
                sidekick.isOnObject(),
                standOnSlot,
                standOnType & 0xFF,
                interactSlot,
                interactOccupant,
                buildStatusByte(sidekick),
                sidekick.getPinballMode(),
                sidekick.getPinballSpeedLock(),
                sidekick.shouldPreserveRollingOnNextRollStop(),
                camMinY,
                camMaxY,
                camMaxYTarget,
                cpuMaxY);
    }

    private String summariseSlotOccupant(ObjectManager om, int slot) {
        if (om == null || slot < 0) {
            return "empty";
        }
        for (ObjectInstance instance : om.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance aoi
                    && aoi.getSlotIndex() == slot
                    && !instance.isDestroyed()
                    && instance.getSpawn() != null) {
                return String.format("%02X@%04X,%04X/%s",
                        instance.getSpawn().objectId() & 0xFF,
                        instance.getX() & 0xFFFF,
                        instance.getY() & 0xFFFF,
                        instance.getClass().getSimpleName());
            }
        }
        return "empty";
    }

    private String summariseSidekickNearbyObjects(ObjectManager om) {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return "eng-tails-near none sidekick=inactive";
        }
        AbstractPlayableSprite sidekick = spriteManager.getSidekicks().getFirst();
        List<EngineNearbyObject> nearbyObjects = new ArrayList<>();
        for (ObjectInstance instance : om.getActiveObjects()) {
            if (!(instance instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            ObjectSpawn spawn = aoi.getSpawn();
            if (spawn == null) {
                continue;
            }
            int currentX = aoi.getX();
            int currentY = aoi.getY();
            int dx = Math.abs(currentX - sidekick.getCentreX());
            int dy = Math.abs(currentY - sidekick.getCentreY());
            if (dx > 192 || dy > 192) {
                continue;
            }
            TouchResponseProvider provider =
                    instance instanceof TouchResponseProvider trp ? trp : null;
            nearbyObjects.add(new EngineNearbyObject(
                    aoi.getSlotIndex(),
                    spawn.objectId(),
                    aoi.getName(),
                    currentX,
                    currentY,
                    spawn.x(),
                    spawn.y(),
                    provider != null,
                    provider != null ? provider.getCollisionFlags() : -1,
                    provider != null ? aoi.getPreUpdateCollisionFlags() : -1,
                    aoi.getPreUpdateX(),
                    aoi.getPreUpdateY(),
                    aoi.isSkipTouchThisFrame(),
                    aoi.isSkipSolidContactThisFrame(),
                    aoi.isOnScreenForTouch(),
                    aoi.traceDebugDetails()));
        }
        nearbyObjects.sort(Comparator.comparingInt(EngineNearbyObject::slot));
        String summary = EngineNearbyObjectFormatter.summarise(nearbyObjects);
        return summary.isEmpty() ? "eng-tails-near none" : "eng-tails-near " + summary;
    }

    private String summariseSidekickCylinderDiagnostics(ObjectManager om) {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return "eng-tails-cyl none sidekick=inactive";
        }
        AbstractPlayableSprite sidekick = spriteManager.getSidekicks().getFirst();
        List<String> parts = new ArrayList<>();
        for (ObjectInstance instance : om.getActiveObjects()) {
            if (!(instance instanceof com.openggf.game.sonic3k.objects.CnzCylinderInstance)
                    || !(instance instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            int dx = Math.abs(aoi.getX() - sidekick.getCentreX());
            int dy = Math.abs(aoi.getY() - sidekick.getCentreY());
            if (dx > 2048 || dy > 2048) {
                continue;
            }
            parts.add(String.format("eng-tails-cyl d=%04X,%04X s%d @%04X,%04X %s",
                    dx & 0xFFFF,
                    dy & 0xFFFF,
                    aoi.getSlotIndex(),
                    aoi.getX() & 0xFFFF,
                    aoi.getY() & 0xFFFF,
                    aoi.traceDebugDetails()));
        }
        parts.sort(Comparator.naturalOrder());
        if (parts.size() > 4) {
            parts = new ArrayList<>(parts.subList(0, 4));
        }
        if (parts.isEmpty()) {
            return "eng-tails-cyl none";
        }
        return String.join(" | ", parts);
    }

    protected String additionalEngineObjectDiagnostics(ObjectManager om) {
        return "";
    }

    private String summariseSidekickCpuDiagnostics() {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return "eng-tails-cpu none sidekick=inactive";
        }
        AbstractPlayableSprite sidekick = spriteManager.getSidekicks().getFirst();
        if (sidekick.getCpuController() == null) {
            return "eng-tails-cpu none controller=missing";
        }
        return sidekick.getCpuController().formatLatestNormalStepDiagnostics();
    }

    private int buildStatusByte(AbstractPlayableSprite sprite) {
        int statusByte = 0;
        if (sprite.getDirection() == com.openggf.physics.Direction.LEFT) {
            statusByte |= 0x01;
        }
        if (sprite.getAir()) statusByte |= 0x02;
        if (sprite.getRolling()) statusByte |= 0x04;
        if (sprite.isOnObject()) statusByte |= 0x08;
        if (sprite.getPushing()) statusByte |= 0x20;
        if (sprite.isInWater()) statusByte |= 0x40;
        return statusByte;
    }

    private void writeReport(DivergenceReport report, TraceMetadata meta) {
        try {
            Path outDir = reportOutputDir();
            Files.createDirectories(outDir);

            String prefix = meta.game() + "_" + meta.zone() + meta.act();
            Path jsonPath = outDir.resolve(prefix + "_report.json");
            Files.writeString(jsonPath, report.toJson());

            if (report.hasErrors()) {
                Path contextPath = outDir.resolve(prefix + "_context.txt");
                int contextRadius = Integer.getInteger("trace.context.radius", 20);
                Files.writeString(contextPath,
                    report.getContextWindow(firstReportErrorFrame(report), contextRadius));
            }
        } catch (IOException e) {
            System.err.println("Warning: failed to write report: " + e.getMessage());
        }
    }

    private static int firstReportErrorFrame(DivergenceReport report) {
        if (!report.errors().isEmpty()) {
            return report.errors().get(0).startFrame();
        }
        return 0;
    }
}
