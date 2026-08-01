package com.openggf;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.profiles.trace.TracePlaybackProfile;
import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.runs.RunBoundarySignal;
import com.openggf.trace.replay.runs.RunPlaybackObservation;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level coverage for the visual multi-stage trace-run adapter. It proves
 * the production launcher translates shared coordinator actions identically
 * to the headless policy harness, and retains focused compatibility coverage
 * for {@link RunSegmentAdvancer}. Synthetic fixtures exercise level, bonus,
 * and special-stage segment plans without requiring a ROM.
 */
class TestTraceSessionLauncherRunBranch {

    private static final Path RUN_DIR =
            Path.of("src", "test", "resources", "traces", "synthetic", "run_aiz_gumball_3seg");
    private static final Path SS_RUN_DIR =
            Path.of("src", "test", "resources", "traces", "synthetic", "run_ehz_ss_3seg");

    private List<TraceRunReplayWalker.SegmentPlan> segments;

    @BeforeEach
    void loadFixture() throws Exception {
        TraceRunManifest run = TraceRunManifest.load(RUN_DIR.resolve("run_manifest.json"));
        segments = TraceRunReplayWalker.plan(run, RUN_DIR);
    }

    @AfterEach
    void clearSession() {
        Engine.clearGlobalInstance();
        GameServices.playbackDebug().endSession();
        SessionManager.clear();
    }

    @Test
    void failedRunLaunchDoesNotLeakRecordedPolicyIntoNextGameplayContext() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.armNextGameplayAdmissionPolicy(
                HardwareReadinessAdmissionPolicy.RECORDED);

        TraceSessionLauncher.restoreFailedLaunch(null, false);
        var next = SessionManager.openGameplaySession(new Sonic2GameModule());

        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                next.hardwareTiming().admissionPolicy());
    }

    @Test
    void realVisualLauncherAndHeadlessPolicyEmitSameCoordinatorTranscript() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        new Engine(EngineServices.current());

        TraceRunManifest.Segment level = segments.get(0).segment();
        TraceRunManifest.Segment bonus = new TraceRunManifest.Segment(
                "seg01_gumball", "bonus_stage", "s3k_bonus_stage",
                1900, 2, 19, 1, null, "gumball");
        TraceRunManifest.Transition transition =
                new TraceRunManifest.Transition(
                        0, 1, "starpost_bonus", 1750,
                        2, null, null, null, null, null, null, null);
        TraceRunManifest run = new TraceRunManifest(
                1, "s3k", "visual-headless-parity", "synthetic.bk2",
                "checksum", "recorder", List.of(level, bonus),
                List.of(transition));
        List<TraceRunReplayWalker.SegmentPlan> twoSegments = List.of(
                new TraceRunReplayWalker.SegmentPlan(
                        level, segments.get(0).trace(), null, transition),
                new TraceRunReplayWalker.SegmentPlan(
                        bonus, segments.get(1).trace(), transition, null));
        Bk2Movie movie = new Bk2Movie(
                Path.of("synthetic-run.bk2"), "logkey", Map.of(),
                List.of(frame(500), frame(1900)), 3);

        TraceSessionLauncher visual =
                new TraceSessionLauncher(null, movie, twoSegments, null);
        TraceRunPlaybackCoordinator visualCoordinator =
                new TraceRunPlaybackCoordinator(
                        run, TracePlaybackProfile.DISABLED, movie.getFrameCount());
        setField(visual, "runCoordinator", visualCoordinator);
        setField(visual, "runBoundaryProbe", new TraceRunReplayWalker.BoundaryProbe(
                new TraceRunReplayWalker.EngineHooks() {
                    @Override
                    public int currentBk2Frame() {
                        return 1750;
                    }

                    @Override
                    public com.openggf.game.BonusStageType peekBonusRequest() {
                        return com.openggf.game.BonusStageType.GUMBALL;
                    }

                    @Override
                    public boolean isSpecialStageRequested() {
                        return false;
                    }

                    @Override
                    public GameMode currentMode() {
                        return GameMode.BONUS_STAGE;
                    }
                }));

        List<TraceRunPlaybackCoordinator.Action> headless =
                driveCanonicalPolicy(new TraceRunPlaybackCoordinator(
                        run, TracePlaybackProfile.DISABLED, movie.getFrameCount()));
        driveCanonicalVisual(visual, visualCoordinator);

        assertEquals(headless, visual.runCoordinatorTranscript());
        assertEquals(List.of(
                        "AdmitDestination", "CloseSegment", "EnterTransitionGap",
                        "AdmitDestination", "CloseSegment", "CompleteRun"),
                headless.stream()
                        .map(action -> action.getClass().getSimpleName())
                        .toList());
    }

    @Test
    void loadInsideSourceIterationKeepsSourceOwnerAndDestinationRowZero()
            throws Exception {
        RunPlaybackObservation sourceOwner = new RunPlaybackObservation(
                GameMode.LEVEL, 129, 8,
                new RunPlaybackObservation.LevelIdentity(10, 0, 0, 0),
                null, null, true, false, 0, false, 4, 5);
        RunPlaybackObservation postStepDestination = new RunPlaybackObservation(
                GameMode.LEVEL, 130, 9,
                new RunPlaybackObservation.LevelIdentity(11, 1, 1, 0),
                null, null, false, true, 1, false, 6, 7);
        Method method = TraceSessionLauncher.class.getDeclaredMethod(
                "withProductionOwner",
                RunPlaybackObservation.class, RunPlaybackObservation.class);
        method.setAccessible(true);

        RunPlaybackObservation published = (RunPlaybackObservation) method.invoke(
                null, postStepDestination, sourceOwner);

        assertEquals(sourceOwner.level(), published.level());
        assertEquals(GameMode.LEVEL, published.mode());
        assertTrue(published.currentSegmentExhausted());
        assertEquals(130, published.sharedBk2Cursor());
        assertEquals(9, published.admittedStepOrdinal());
        assertEquals(0, published.destinationRowsConsumed(),
                "a remembered load inside source production has not consumed "
                        + "destination row zero");
    }

    @Test
    void admissionLatchesComparedLevelBonusLevelRowsAcrossStructuralHandoffs() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Bk2Movie movie = new Bk2Movie(
                Path.of("synthetic-run.bk2"),
                "logkey",
                Map.of(),
                List.of(frame(500), frame(1900), frame(2900)),
                3);
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, movie, segments, null);
        RecordingTimingFixture fixture = new RecordingTimingFixture();
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        var first = HardwareTimingSchedule.empty();
        var bonus = HardwareTimingSchedule.empty();
        var returnedLevel = HardwareTimingSchedule.empty();
        var coordinator = new TraceRunReplayWalker.HardwareTimingCoordinator(
                fixture,
                List.of(
                        new TraceRunReplayWalker.HardwareTimingSegment(
                                500, List.of(100), first),
                        new TraceRunReplayWalker.HardwareTimingSegment(
                                1900, List.of(200), bonus),
                        new TraceRunReplayWalker.HardwareTimingSegment(
                                2900, List.of(300), returnedLevel)));
        setField(session, "fixture", fixture);
        setField(session, "runAdvancer", advancer);
        setField(session, "runHardwareTiming", coordinator);

        GameServices.playbackDebug().startSession(movie, 0);
        try {
            session.prepareHardwareTimingForAdmission(GameMode.LEVEL);

            session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 501);
            session.runAdvanceTickIfActive(GameMode.BONUS_STAGE, 1900);
            GameServices.playbackDebug().seekSessionFrame(1, true);
            session.prepareHardwareTimingForAdmission(GameMode.BONUS_STAGE);

            session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 1901);
            session.runAdvanceTickIfActive(GameMode.LEVEL, 2900);
            GameServices.playbackDebug().seekSessionFrame(2, true);
            session.prepareHardwareTimingForAdmission(GameMode.LEVEL);
        } finally {
            GameServices.playbackDebug().endSession();
        }

        assertEquals(List.of(100, 200, 300), fixture.rawFrames);
        assertEquals(List.of(bonus, returnedLevel), fixture.handoffs);
        assertEquals(0, fixture.gaps);
    }

    @Test
    void productionDynamicArtWindowSpansRepresentedSegmentsAndNativeGaps() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, null, segments, null);
        setField(session, "runAdvancer", new RunSegmentAdvancer(segments));
        session.installRunDynamicArtSegments(context);

        assertTrue(context.dynamicArtLifecycle().isComparisonSegmentOpen());

        session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 501);
        assertFalse(context.dynamicArtLifecycle().isComparisonSegmentOpen());
        context.dynamicArtLifecycle().observePlayerDplc(
                GameId.S2, "tails-tails", 13,
                new com.openggf.level.render.SpriteDplcFrame(List.of(
                        new TileLoadRequest(110, 12))));

        session.runAdvanceTickIfActive(GameMode.BONUS_STAGE, 1900);
        assertTrue(context.dynamicArtLifecycle().isComparisonSegmentOpen());
        assertEquals(List.of(0L), context.dynamicArtDiagnostics()
                .latestSnapshot().outstandingTransferIds());
        for (int rowIndex = 0; rowIndex < 126; rowIndex++) {
            context.plcFrameLifecycle().runLogicalIteration(() -> { }, row -> {
                row.claim(PlcLifecyclePhase.PALETTE_FADE);
                row.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
                return null;
            });
            assertEquals(List.of(0L), context.dynamicArtDiagnostics()
                    .latestSnapshot().outstandingTransferIds());
        }
        context.plcFrameLifecycle().runLogicalIteration(() -> { }, row -> {
            row.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            return null;
        });
        assertTrue(context.dynamicArtDiagnostics().latestSnapshot()
                .outstandingTransferIds().isEmpty());
        session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 1901);
        assertFalse(context.dynamicArtLifecycle().isComparisonSegmentOpen());
        session.runAdvanceTickIfActive(GameMode.LEVEL, 2900);
        assertTrue(context.dynamicArtLifecycle().isComparisonSegmentOpen());

        session.runAdvanceTickIfActive(GameMode.LEVEL, 2902);

        assertFalse(context.dynamicArtLifecycle().isComparisonSegmentOpen());
        assertEquals(List.of("submitted"),
                context.dynamicArtLifecycle().gapEdges().stream()
                        .map(edge -> edge.phase()).toList());
    }

    @Test
    void teardownRequestedInsideIterationWaitsForPostFinishDrain()
            throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, null, segments, null);
        session.installRunDynamicArtSegments(context);
        session.beforeProductionIteration();

        var teardown = TraceSessionLauncher.class
                .getDeclaredMethod("teardown");
        teardown.setAccessible(true);
        teardown.invoke(session);

        assertTrue((boolean) getField(session, "teardownPending"));
        assertNotNull(getField(session, "runDynamicArtSegments"),
                "teardown must not close production comparison in the body");

        session.afterProductionIteration();

        assertFalse((boolean) getField(session, "teardownPending"));
        assertNull(getField(session, "runDynamicArtSegments"));
    }

    @Test
    void visualRunSpecialStageComparesAdvertisedProductionRowsAfterPublication() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        List<TraceRunReplayWalker.SegmentPlan> advertised =
                withAdvertisedSpecialStageTrace();
        assertTrue(advertised.get(1).trace().metadata()
                .hasPerFrameDynamicArtTransferState());
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, null, advertised, null);
        List<FrameComparison> observed = new ArrayList<>();
        LiveTraceComparator reportSink = new LiveTraceComparator(
                advertised.get(1).trace(), ToleranceConfig.DEFAULT, 0,
                () -> null, null, observed::add);
        RecordingTimingFixture fixture = new RecordingTimingFixture(context);
        setField(session, "fixture", fixture);
        setField(session, "comparator", reportSink);
        setField(session, "runAdvancer", new RunSegmentAdvancer(advertised));
        setField(session, "runHardwareTiming",
                new TraceRunReplayWalker.HardwareTimingCoordinator(
                        fixture,
                        TraceRunReplayWalker.hardwareTimingSegments(advertised)));
        session.installRunDynamicArtSegments(context);
        session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 501);

        session.prepareHardwareTimingForAdmission(GameMode.SPECIAL_STAGE);
        session.beforeProductionIteration();
        context.plcFrameLifecycle().runLogicalIteration(() -> {
        }, row -> {
            row.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            row.prepareAfterLoop(PlcLifecyclePhase.SPECIAL_STAGE);
            session.runAdvanceTickIfActive(GameMode.SPECIAL_STAGE, 800);
            return null;
        });
        session.afterProductionIteration();

        session.prepareHardwareTimingForAdmission(GameMode.SPECIAL_STAGE);
        session.beforeProductionIteration();
        context.plcFrameLifecycle().runLogicalIteration(() -> {
        }, row -> {
            row.claim(PlcLifecyclePhase.LAG);
            context.dynamicArtLifecycle().observeRamDplc(
                    "ss-sonic", 3, List.of(new TileLoadRequest(1, 1)),
                    0xFF0000, 0x5CA0);
            row.prepareAfterLoop(PlcLifecyclePhase.LAG);
            session.runAdvanceTickIfActive(GameMode.SPECIAL_STAGE, 801);
            assertEquals(1, observed.size(),
                    "terminal comparison must wait for coordinator finish");
            return null;
        });
        session.afterProductionIteration();

        assertEquals(2, observed.size());
        assertTrue(observed.stream().noneMatch(FrameComparison::hasDivergence));
        assertEquals("true", observed.getLast().fields()
                .get("dynamic_art.edge[0].terminal_forwarded").actual());
    }

    @Test
    void visualRunStartingInSpecialStageBindsAlreadyOpenGeneration() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        TraceRunReplayWalker.SegmentPlan special =
                withAdvertisedSpecialStageTrace().get(1);
        List<TraceRunReplayWalker.SegmentPlan> ssFirst = List.of(special);
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, null, ssFirst, null);
        List<FrameComparison> observed = new ArrayList<>();
        LiveTraceComparator reportSink = new LiveTraceComparator(
                special.trace(), ToleranceConfig.DEFAULT, 0,
                () -> null, null, observed::add);
        RecordingTimingFixture fixture = new RecordingTimingFixture(context);
        setField(session, "fixture", fixture);
        setField(session, "comparator", reportSink);
        setField(session, "runAdvancer", new RunSegmentAdvancer(ssFirst));
        setField(session, "runHardwareTiming",
                new TraceRunReplayWalker.HardwareTimingCoordinator(
                        fixture,
                        TraceRunReplayWalker.hardwareTimingSegments(ssFirst)));
        session.installRunDynamicArtSegments(context);

        long alreadyOpenGeneration = context.dynamicArtDiagnostics()
                .latestSnapshot().segmentGeneration();
        session.prepareHardwareTimingForAdmission(GameMode.SPECIAL_STAGE);
        assertEquals(alreadyOpenGeneration,
                getField(session,
                        "runSpecialDynamicArtTargetGeneration"));
        session.beforeProductionIteration();
        context.plcFrameLifecycle().runLogicalIteration(() -> {
        }, row -> {
            row.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            row.prepareAfterLoop(PlcLifecyclePhase.SPECIAL_STAGE);
            return null;
        });
        session.afterProductionIteration();

        assertEquals(1, observed.size());
        assertFalse(observed.getFirst().hasDivergence());
        assertEquals(alreadyOpenGeneration,
                context.dynamicArtDiagnostics().latestSnapshot()
                        .segmentGeneration());
    }

    @Test
    void visualRunSpecialStageRowZeroRebindsBeforeMismatchPublication() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        List<TraceRunReplayWalker.SegmentPlan> advertised =
                withAdvertisedSpecialStageTrace();
        Engine engine = new Engine(EngineServices.current());
        Bk2Movie movie = new Bk2Movie(
                Path.of("synthetic-run.bk2"),
                "logkey",
                Map.of(),
                List.of(frame(500), frame(800)),
                3);
        List<FrameComparison> oldObserved = new ArrayList<>();
        AtomicBoolean oldFirstError = new AtomicBoolean();
        LiveTraceComparator levelComparator = new LiveTraceComparator(
                advertised.getFirst().trace(), ToleranceConfig.DEFAULT, 0,
                () -> null, () -> oldFirstError.set(true), oldObserved::add);
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, movie, advertised, null);
        RecordingTimingFixture fixture = new RecordingTimingFixture(context);
        setField(session, "fixture", fixture);
        setField(session, "comparator", levelComparator);
        setField(session, "runAdvancer", new RunSegmentAdvancer(advertised));
        setField(session, "runHardwareTiming",
                new TraceRunReplayWalker.HardwareTimingCoordinator(
                        fixture,
                        TraceRunReplayWalker.hardwareTimingSegments(advertised)));
        session.installRunDynamicArtSegments(context);
        GameServices.playbackDebug().setFrameObserver(levelComparator);
        GameServices.playbackDebug().startSession(movie, 500);
        context.plcFrameLifecycle().runLogicalIteration(() -> {
        }, row -> {
            row.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
            row.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            return null;
        });
        assertEquals(0,
                context.dynamicArtDiagnostics().latestSnapshot().frame(),
                "regression must cross a prior level publication epoch");
        session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 501);

        session.prepareHardwareTimingForAdmission(GameMode.SPECIAL_STAGE);
        session.beforeProductionIteration();
        context.plcFrameLifecycle().runLogicalIteration(() -> {
        }, row -> {
            row.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            context.dynamicArtLifecycle().observeRamDplc(
                    "ss-sonic", 2, List.of(new TileLoadRequest(0, 1)),
                    0xFF0000, 0x5CA0);
            row.prepareAfterLoop(PlcLifecyclePhase.SPECIAL_STAGE);
            session.runAdvanceTickIfActive(GameMode.SPECIAL_STAGE, 800);
            LiveTraceComparator beforeFinish =
                    (LiveTraceComparator) getField(session, "comparator");
            assertSame(levelComparator, beforeFinish,
                    "segment rebind must wait for the old production row to publish");
            assertEquals(0, beforeFinish.errorCount());
            assertTrue(beforeFinish.recentMismatches().isEmpty());
            return null;
        });
        session.afterProductionIteration();

        LiveTraceComparator specialStageComparator =
                (LiveTraceComparator) getField(session, "comparator");
        assertNotSame(levelComparator, specialStageComparator);
        assertEquals(3, specialStageComparator.errorCount());
        assertEquals(
                List.of(
                        "dynamic_art.edge[0].present",
                        "dynamic_art.outstanding_transfer_ids",
                        "dynamic_art.edges"),
                specialStageComparator.recentMismatches().stream()
                        .map(mismatch -> mismatch.field()).toList());
        assertTrue(specialStageComparator.recentMismatches().stream()
                .allMatch(mismatch -> mismatch.frame() == 0));
        assertSame(specialStageComparator,
                getField(GameServices.playbackDebug(), "frameObserver"));
        assertTrue(engine.getGameLoop().isPaused());
        assertEquals(0, levelComparator.errorCount());
        assertTrue(levelComparator.recentMismatches().isEmpty());
        assertFalse(oldFirstError.get());
        assertTrue(oldObserved.isEmpty());
    }

    @Test
    void visualRunSpecialStageRejectsOmittedAdvertisedRow() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        List<TraceRunReplayWalker.SegmentPlan> advertised =
                withAdvertisedSpecialStageTrace();
        TraceSessionLauncher session =
                new TraceSessionLauncher(null, null, advertised, null);
        List<FrameComparison> observed = new ArrayList<>();
        LiveTraceComparator reportSink = new LiveTraceComparator(
                advertised.getFirst().trace(), ToleranceConfig.DEFAULT, 0,
                () -> null, null, observed::add);
        RecordingTimingFixture fixture = new RecordingTimingFixture(context);
        setField(session, "fixture", fixture);
        setField(session, "comparator", reportSink);
        setField(session, "runAdvancer", new RunSegmentAdvancer(advertised));
        setField(session, "runHardwareTiming",
                new TraceRunReplayWalker.HardwareTimingCoordinator(
                        fixture,
                        TraceRunReplayWalker.hardwareTimingSegments(advertised)));
        session.installRunDynamicArtSegments(context);
        context.dynamicArtLifecycle().finishProductionIteration(false);
        var oldWork = context.dynamicArtLifecycle().observeRamDplc(
                "ss-sonic", 7, List.of(new TileLoadRequest(0, 1)),
                0xFF0000, 0x5CA0);
        context.dynamicArtLifecycle().completeApplied(oldWork);
        session.runAdvanceTickIfActive(GameMode.TITLE_CARD, 501);
        DynamicArtDiagnosticsSnapshot oldTerminal =
                context.dynamicArtDiagnostics().latestSnapshot();
        assertTrue(oldTerminal.published());
        assertEquals(0, oldTerminal.frame());
        session.prepareHardwareTimingForAdmission(GameMode.SPECIAL_STAGE);
        DynamicArtDiagnosticsSnapshot newlyOpened =
                context.dynamicArtDiagnostics().latestSnapshot();
        assertFalse(newlyOpened.published());
        assertEquals(-1, newlyOpened.frame());
        assertEquals(oldTerminal.deliverySerial(),
                newlyOpened.deliverySerial());
        assertTrue(newlyOpened.segmentGeneration()
                > oldTerminal.segmentGeneration());
        session.beforeProductionIteration();
        context.plcFrameLifecycle().runLogicalIteration(() -> {
        }, row -> {
            session.runAdvanceTickIfActive(GameMode.SPECIAL_STAGE, 800);
            return null;
        });

        assertTrue(observed.isEmpty());
        assertThrows(IllegalStateException.class,
                session::afterProductionIteration);
    }

    private List<TraceRunReplayWalker.SegmentPlan>
            withAdvertisedSpecialStageTrace() {
        List<TraceRunReplayWalker.SegmentPlan> specialSegments;
        try {
            TraceRunManifest run = TraceRunManifest.load(
                    SS_RUN_DIR.resolve("run_manifest.json"));
            specialSegments = TraceRunReplayWalker.plan(run, SS_RUN_DIR);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        TraceData trace = TraceFixtures.trace(
                TraceFixtures.metadataWithDynamicArt("s2", 0, 0, 2),
                List.of(),
                Map.of(
                        0, List.of(new TraceEvent.DynamicArtTransferState(
                                0, List.of(), List.of())),
                        1, List.of(new TraceEvent.DynamicArtTransferState(
                                1,
                                List.of(new DynamicArtTransfer.SegmentEdge(
                                        0, 0, "submitted", "ss-sonic",
                                        "segment", 3, 1, 0, 1, true,
                                        0x33ADA,
                                        List.of(new DynamicArtTransfer.Request(
                                                -1, -1, 0xFF0020,
                                                0x5CA0, 0x20)))),
                                List.of(0L)))));
        var middle = specialSegments.get(1);
        return List.of(
                specialSegments.get(0),
                new TraceRunReplayWalker.SegmentPlan(
                        middle.segment(), trace,
                        middle.entryBoundary(), middle.exitBoundary()),
                specialSegments.get(2));
    }

    @Test
    void staysComparingWhileModeMatchesSegmentZero() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        assertNull(advancer.onFrame(GameMode.LEVEL, 500));
        assertNull(advancer.onFrame(GameMode.LEVEL, 600));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void entersTransitionWhenModeLeavesSegmentZero() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        assertNull(advancer.onFrame(GameMode.TITLE_CARD, 1751));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void emitsAdvanceActionWhenBonusStageReached() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        RunSegmentAdvancer.Event event = advancer.onFrame(GameMode.BONUS_STAGE, 1900);
        assertTrue(event instanceof RunSegmentAdvancer.AdvanceAction);
        RunSegmentAdvancer.AdvanceAction action = (RunSegmentAdvancer.AdvanceAction) event;
        assertEquals(1900, action.reseekOffset());
        assertEquals(1, action.nextSegmentIndex());
        assertEquals(1, advancer.currentSegmentIndex());
    }

    @Test
    void modeFlickerDuringTransitionEmitsNothing() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        // TITLE_CARD -> TITLE_CARD flicker mid-transition: not the next
        // segment's expected mode (BONUS_STAGE), so nothing is emitted and
        // the advancer stays mid-transition on segment 0.
        assertNull(advancer.onFrame(GameMode.TITLE_CARD, 1752));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void wrongModeDuringTransitionKeepsWaiting() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        // SPECIAL_STAGE is not segment 1's expected mode (BONUS_STAGE):
        // never throws, just keeps waiting.
        assertNull(advancer.onFrame(GameMode.SPECIAL_STAGE, 1755));
        assertEquals(0, advancer.currentSegmentIndex());
    }

    @Test
    void fullChainReachesEndOfRun() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        assertNull(advancer.onFrame(GameMode.LEVEL, 1000));

        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        RunSegmentAdvancer.Event toBonus = advancer.onFrame(GameMode.BONUS_STAGE, 1900);
        assertEquals(new RunSegmentAdvancer.AdvanceAction(1900, 1), toBonus);
        assertEquals(1, advancer.currentSegmentIndex());

        assertNull(advancer.onFrame(GameMode.BONUS_STAGE, 2000));
        advancer.onFrame(GameMode.TITLE_CARD, 2800);
        RunSegmentAdvancer.Event toLevel = advancer.onFrame(GameMode.LEVEL, 2900);
        assertEquals(new RunSegmentAdvancer.AdvanceAction(2900, 2), toLevel);
        assertEquals(2, advancer.currentSegmentIndex());

        // Segment 2 (offset 2900, 2 trace frames): still comparing before
        // the last frame is exhausted.
        assertNull(advancer.onFrame(GameMode.LEVEL, 2901));
        RunSegmentAdvancer.Event end = advancer.onFrame(GameMode.LEVEL, 2902);
        assertSame(RunSegmentAdvancer.EndOfRun.INSTANCE, end);
    }

    @Test
    void staysDoneAfterEndOfRun() {
        RunSegmentAdvancer advancer = new RunSegmentAdvancer(segments);
        advancer.onFrame(GameMode.LEVEL, 1750);
        advancer.onFrame(GameMode.TITLE_CARD, 1751);
        advancer.onFrame(GameMode.BONUS_STAGE, 1900);
        advancer.onFrame(GameMode.TITLE_CARD, 2800);
        advancer.onFrame(GameMode.LEVEL, 2900);
        advancer.onFrame(GameMode.LEVEL, 2902);
        assertNull(advancer.onFrame(GameMode.LEVEL, 3000));
    }

    private static Bk2FrameInput frame(int index) {
        return new Bk2FrameInput(index, 0, 0, false, "");
    }

    private static List<TraceRunPlaybackCoordinator.Action> driveCanonicalPolicy(
            TraceRunPlaybackCoordinator coordinator) {
        List<TraceRunPlaybackCoordinator.Action> transcript = new ArrayList<>();
        transcript.addAll(coordinator.activateInitialLevel(
                levelObservation(false)));
        coordinator.observeBoundary(new RunBoundarySignal.BonusRequest(
                1750, com.openggf.game.BonusStageType.GUMBALL));
        transcript.addAll(coordinator.afterProduction(levelObservation(true)));
        transcript.addAll(coordinator.beforeAdmission(bonusObservation(false)));
        transcript.addAll(coordinator.afterProduction(bonusObservation(true)));
        return List.copyOf(transcript);
    }

    private static void driveCanonicalVisual(
            TraceSessionLauncher launcher,
            TraceRunPlaybackCoordinator coordinator) {
        applyCoordinatorActions(launcher,
                coordinator.activateInitialLevel(levelObservation(false)));
        coordinator.observeBoundary(new RunBoundarySignal.BonusRequest(
                1750, com.openggf.game.BonusStageType.GUMBALL));
        assertNotNull(getField(coordinator, "observedBoundary"));
        applyCoordinatorActions(launcher,
                coordinator.afterProduction(levelObservation(true)));
        assertEquals(TraceRunPlaybackCoordinator.Phase.TRANSITION_GAP,
                coordinator.phase());
        assertEquals(0, coordinator.currentSegmentIndex());
        assertNotNull(getField(coordinator, "observedBoundary"));
        List<TraceRunPlaybackCoordinator.Action> admission =
                coordinator.beforeAdmission(bonusObservation(false));
        assertFalse(admission.isEmpty(),
                "visual coordinator must admit the observed bonus destination");
        applyCoordinatorActions(launcher, admission);
        applyCoordinatorActions(launcher,
                coordinator.afterProduction(bonusObservation(true)));
    }

    private static RunPlaybackObservation levelObservation(boolean exhausted) {
        return new RunPlaybackObservation(
                GameMode.LEVEL, 500, exhausted ? 2 : 0,
                new RunPlaybackObservation.LevelIdentity(1, 0, 0, 0),
                null, null, false, exhausted, 0, false, 10, 20);
    }

    private static RunPlaybackObservation bonusObservation(boolean exhausted) {
        return new RunPlaybackObservation(
                GameMode.BONUS_STAGE, 1900, exhausted ? 4 : 3,
                null,
                new RunPlaybackObservation.BonusIdentity(
                        19, 0, com.openggf.game.BonusStageType.GUMBALL),
                null, false, exhausted, 0, false, 30, 40);
    }

    @SuppressWarnings("unchecked")
    private static void applyCoordinatorActions(
            TraceSessionLauncher launcher,
            List<TraceRunPlaybackCoordinator.Action> actions) {
        try {
            Method method = TraceSessionLauncher.class.getDeclaredMethod(
                    "applyRunCoordinatorActions", List.class);
            method.setAccessible(true);
            method.invoke(launcher, actions);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(
            TraceSessionLauncher session, String fieldName, Object value) {
        try {
            Field field = TraceSessionLauncher.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(session, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object getField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class RecordingTimingFixture
            implements TraceReplayFixture {
        private final List<Integer> rawFrames = new ArrayList<>();
        private final List<HardwareTimingSchedule> handoffs = new ArrayList<>();
        private int gaps;
        private final GameplayModeContext gameplayMode;

        private RecordingTimingFixture() {
            this(null);
        }

        private RecordingTimingFixture(GameplayModeContext gameplayMode) {
            this.gameplayMode = gameplayMode;
        }

        @Override
        public void beginTraceRow(int traceIndex, int rawFrame) {
            rawFrames.add(rawFrame);
        }

        @Override
        public void enterHardwareTimingGap() {
            gaps++;
        }

        @Override
        public void handoffHardwareTimingReplay(
                HardwareTimingSchedule nextSchedule) {
            handoffs.add(nextSchedule);
        }

        @Override
        public AbstractPlayableSprite sprite() {
            return null;
        }

        @Override
        public GameplayModeContext gameplayMode() {
            return gameplayMode;
        }

        @Override
        public void installHardwareTimingReplay(
                HardwareTimingReplayPort replayPort) {
        }

        @Override
        public void verifyHardwareTimingSegmentEdges() {
        }

        @Override
        public void closeHardwareTimingReplayRun() {
        }

        @Override
        public void abortHardwareTimingReplayRun() {
        }

        @Override
        public int stepFrameFromRecording() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int skipFrameFromRecording() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advancePlayableAnimationsOnly() {
        }

        @Override
        public void advancePlayableFixedSlotsOnly() {
        }

        @Override
        public void suppressFirstSidekickAnimationOnce() {
        }

        @Override
        public int consumeRecordingFrameInputOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            throw new UnsupportedOperationException();
        }
    }
}
