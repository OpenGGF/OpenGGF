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
import com.openggf.game.OscillationManager;
import com.openggf.game.resources.DynamicArtGapTransition;
import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.TraceData;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.replay.TraceReplayDriver;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.DestinationAdmissionReceipt;
import com.openggf.trace.replay.runs.RunBoundarySignal;
import com.openggf.trace.replay.runs.RunPlaybackObservation;
import com.openggf.trace.replay.runs.TraceRunBoundaryComparator;
import com.openggf.trace.replay.runs.TraceRunDynamicArtGapComparator;
import com.openggf.trace.replay.runs.RunLevelLoadTracker;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunFrameDriver;
import com.openggf.trace.replay.runs.TraceRunPresentationClosure;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRows;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRowDriver;
import com.openggf.trace.replay.runs.TraceStructuralRowComparator;
import com.openggf.trace.replay.TraceReplayRowPolicy;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryEntryMode;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryObservation;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.BoundaryProbe;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.HardwareTimingCoordinator;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.HardwareTimingSegment;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.ReturnAssertionMode;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.SegmentPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable base for multi-stage trace RUN chain tests (spec/API contract:
 * "Decisions locked with the owner" and Component 2 in
 * docs/architecture/designs/2026-07-18-multi-stage-trace-runs-design.md). Drives
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
    private LiveTraceComparator productionComparator;
    private HeadlessRunCoordinatorAdapter activeRunCoordinator;

    protected record DynamicArtGapJournalEvidence(
            int transitionCountAfterFirstArm,
            long lastEdgeOrdinalAfterFirstArm,
            List<DynamicArtStructuralGapEvidence> structuralGaps,
            List<TraceRunPlaybackCoordinator.Action> coordinatorActions) {
        protected DynamicArtGapJournalEvidence {
            structuralGaps = List.copyOf(structuralGaps);
            coordinatorActions = List.copyOf(coordinatorActions);
        }

        protected DynamicArtStructuralGapEvidence structuralGap(
                String representedSegmentDir,
                String nextSegmentDir) {
            return structuralGaps.stream()
                    .filter(gap -> gap.representedSegmentDir()
                            .equals(representedSegmentDir))
                    .filter(gap -> gap.nextSegmentDir()
                            .equals(nextSegmentDir))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "missing dynamic-art structural gap "
                                    + representedSegmentDir + " -> "
                                    + nextSegmentDir));
        }
    }

    protected record DynamicArtStructuralGapEvidence(
            String representedSegmentDir,
            String nextSegmentDir,
            int gapStartMovieLogicalFrame,
            int nextSegmentArmMovieLogicalFrame,
            int transitionCountAtGapStart,
            long lastEdgeOrdinalAtGapStart,
            int transitionCountAfterNextArm,
            long lastEdgeOrdinalAfterNextArm,
            TraceRunDynamicArtGapComparator.StructuralOrder structuralOrder,
            List<DynamicArtTransfer.Descriptor> openingLedger,
            List<DynamicArtGapTransition> transitionsAddedAcrossBoundary) {
        protected DynamicArtStructuralGapEvidence {
            openingLedger = List.copyOf(openingLedger);
            transitionsAddedAcrossBoundary =
                    List.copyOf(transitionsAddedAcrossBoundary);
        }
    }

    private static final class DynamicArtGapJournalProbe {
        private final DynamicArtLifecycleService lifecycle;
        private final int transitionCountAfterFirstArm;
        private final long lastEdgeOrdinalAfterFirstArm;
        private final List<DynamicArtStructuralGapEvidence> structuralGaps =
                new ArrayList<>();

        private String representedSegmentDir;
        private Integer gapStartMovieLogicalFrame;
        private int transitionCountAtGapStart;
        private long lastEdgeOrdinalAtGapStart;
        private long structuralOrdinal;
        private Long sourceClosedOrdinal;
        private Long gapOpenedOrdinal;
        private List<DynamicArtTransfer.Descriptor> openingLedger = List.of();

        private DynamicArtGapJournalProbe(DynamicArtLifecycleService lifecycle) {
            this.lifecycle = lifecycle;
            List<DynamicArtGapTransition> firstArmTransitions =
                    lifecycle.gapTransitions();
            transitionCountAfterFirstArm = firstArmTransitions.size();
            lastEdgeOrdinalAfterFirstArm =
                    lastEdgeOrdinal(firstArmTransitions);
        }

        private void sourceClosed(String segmentDir) {
            if (sourceClosedOrdinal != null) {
                throw new AssertionError(
                        "dynamic-art source closed twice for " + segmentDir);
            }
            representedSegmentDir = segmentDir;
            sourceClosedOrdinal = ++structuralOrdinal;
        }

        private void gapOpened(String segmentDir) {
            if (gapStartMovieLogicalFrame != null) {
                if (!representedSegmentDir.equals(segmentDir)) {
                    throw new AssertionError(
                            "dynamic-art gap changed represented segment from "
                                    + representedSegmentDir + " to " + segmentDir);
                }
                return;
            }
            if (sourceClosedOrdinal == null
                    || !Objects.equals(representedSegmentDir, segmentDir)) {
                throw new AssertionError(
                        "dynamic-art gap opened before source close for " + segmentDir);
            }
            List<DynamicArtGapTransition> atGapStart =
                    lifecycle.gapTransitions();
            gapOpenedOrdinal = ++structuralOrdinal;
            gapStartMovieLogicalFrame =
                    lifecycle.capture().movieLogicalFrame();
            openingLedger = lifecycle.capture().ledger().stream()
                    .map(DynamicArtGapJournalProbe::toTraceDescriptor)
                    .toList();
            transitionCountAtGapStart = atGapStart.size();
            lastEdgeOrdinalAtGapStart =
                    lastEdgeOrdinal(atGapStart);
        }

        private void nextSegmentArmed(String nextSegmentDir) {
            if (gapStartMovieLogicalFrame == null) {
                throw new AssertionError(
                        "dynamic-art next segment armed without a structural gap");
            }
            int nextSegmentArmMovieLogicalFrame =
                    lifecycle.capture().movieLogicalFrame();
            long destinationOpenedOrdinal = ++structuralOrdinal;
            List<DynamicArtGapTransition> afterNextArm =
                    lifecycle.gapTransitions();
            List<DynamicArtGapTransition> added =
                    afterNextArm.size() >= transitionCountAtGapStart
                            ? afterNextArm.subList(
                                    transitionCountAtGapStart,
                                    afterNextArm.size())
                            : List.of();
            structuralGaps.add(new DynamicArtStructuralGapEvidence(
                    representedSegmentDir,
                    nextSegmentDir,
                    gapStartMovieLogicalFrame,
                    nextSegmentArmMovieLogicalFrame,
                    transitionCountAtGapStart,
                    lastEdgeOrdinalAtGapStart,
                    afterNextArm.size(),
                    lastEdgeOrdinal(afterNextArm),
                    new TraceRunDynamicArtGapComparator.StructuralOrder(
                            sourceClosedOrdinal,
                            gapOpenedOrdinal,
                            destinationOpenedOrdinal),
                    openingLedger,
                    added));
            representedSegmentDir = null;
            gapStartMovieLogicalFrame = null;
            sourceClosedOrdinal = null;
            gapOpenedOrdinal = null;
            openingLedger = List.of();
        }

        private void verify(TraceRunManifest run) {
            for (int sourceIndex = 0;
                    sourceIndex < structuralGaps.size(); sourceIndex++) {
                DynamicArtStructuralGapEvidence evidence =
                        structuralGaps.get(sourceIndex);
                FrameComparison comparison =
                        TraceRunDynamicArtGapComparator.compare(
                                evidence.gapStartMovieLogicalFrame(),
                                run,
                                sourceIndex,
                                new TraceRunDynamicArtGapComparator.RuntimeGap(
                                        evidence.representedSegmentDir(),
                                        evidence.nextSegmentDir(),
                                        evidence.structuralOrder(),
                                        evidence.openingLedger(),
                                        evidence.transitionsAddedAcrossBoundary()));
                if (comparison.hasError()) {
                    throw new AssertionError(
                            "shared dynamic-art gap comparison failed for "
                                    + evidence.representedSegmentDir() + " -> "
                                    + evidence.nextSegmentDir() + ": "
                                    + comparison.divergentFields());
                }
            }
        }

        private void verifyTerminal(
                TraceRunManifest run, int movieFrameCount) {
            if (gapStartMovieLogicalFrame == null
                    || sourceClosedOrdinal == null
                    || gapOpenedOrdinal == null) {
                throw new AssertionError(
                        "terminal dynamic-art tail has no open source gap");
            }
            // Mirrors the launcher's terminal tail: the movie ends before this
            // level reaches its main loop, so a transfer still held for the
            // pre-main-loop tail settles at the tail's earliest row.
            lifecycle.releaseUnclaimedPreMainLoopPlayerTransfer();
            int transitionCount = lifecycle.gapTransitions().size();
            List<DynamicArtGapTransition> added = transitionCount
                    >= transitionCountAtGapStart
                    ? lifecycle.gapTransitions().subList(
                            transitionCountAtGapStart, transitionCount)
                    : List.of();
            int sourceIndex = run.segments().size() - 1;
            FrameComparison comparison =
                    TraceRunDynamicArtGapComparator.compareTerminalTail(
                            gapStartMovieLogicalFrame, run, sourceIndex,
                            movieFrameCount,
                            new TraceRunDynamicArtGapComparator.RuntimeTerminalTail(
                                    representedSegmentDir,
                                    sourceClosedOrdinal,
                                    gapOpenedOrdinal,
                                    ++structuralOrdinal,
                                    openingLedger,
                                    added));
            if (comparison.hasError()) {
                throw new AssertionError(
                        "shared terminal dynamic-art comparison failed for "
                                + representedSegmentDir + ": "
                                + comparison.divergentFields());
            }
            representedSegmentDir = null;
            gapStartMovieLogicalFrame = null;
            sourceClosedOrdinal = null;
            gapOpenedOrdinal = null;
            openingLedger = List.of();
        }

        private DynamicArtGapJournalEvidence evidence(
                List<TraceRunPlaybackCoordinator.Action> coordinatorActions) {
            if (structuralGaps.isEmpty()) {
                throw new AssertionError(
                        "run did not arm a next segment after its first structural gap");
            }
            return new DynamicArtGapJournalEvidence(
                    transitionCountAfterFirstArm,
                    lastEdgeOrdinalAfterFirstArm,
                    structuralGaps,
                    coordinatorActions);
        }

        private static long lastEdgeOrdinal(
                List<DynamicArtGapTransition> transitions) {
            return transitions.isEmpty()
                    ? -1
                    : transitions.getLast().edge().edgeOrdinal();
        }

        private static DynamicArtTransfer.Descriptor toTraceDescriptor(
                DynamicArtLifecycleService.Descriptor descriptor) {
            return new DynamicArtTransfer.Descriptor(
                    descriptor.transferId(),
                    descriptor.owner(),
                    descriptor.mappingFrame(),
                    "segment",
                    descriptor.requests().stream()
                            .map(request -> new DynamicArtTransfer.Request(
                                    request.romSourceAddress(),
                                    request.sourceTileIndex(),
                                    request.ramSourceAddress(),
                                    request.vramDestination(),
                                    request.byteLength()))
                            .toList(),
                    null);
        }
    }

    /**
     * Test-side executor for the shared run policy. It samples only production
     * identities and lifecycle generations, then requires every legacy
     * handoff to earn a coordinator admission receipt before a new comparator
     * is allowed to remain attached.
     */
    private static final class HeadlessRunCoordinatorAdapter {
        private final TraceRunManifest run;
        private final TraceRunPlaybackCoordinator coordinator;
        private final List<TraceRunPlaybackCoordinator.Action> actions =
                new ArrayList<>();
        private final RunLevelLoadTracker levelLoads;
        private long admittedStepOrdinal;
        private RunPlaybackObservation productionOwnerObservation;
        private int productionOwnerSegmentIndex = -1;

        private HeadlessRunCoordinatorAdapter(
                TraceRunManifest run, Bk2Movie movie,
                List<SegmentPlan> plans) {
            this.run = run;
            this.coordinator = new TraceRunPlaybackCoordinator(
                    run,
                    GameServices.module().getTracePlaybackProfile(),
                    movie.getFrameCount(), plans);
            this.levelLoads = SessionManager.getCurrentGameplayMode()
                    .runLevelLoads();
            this.levelLoads.prime(GameServices.level());
        }

        private void activateInitial(GameMode mode) {
            List<TraceRunPlaybackCoordinator.Action> emitted =
                    coordinator.activateInitialLevel(
                            observation(mode, false, 0, false));
            requireAdmission(emitted, 0, 0);
        }

        private void closeCurrent(GameMode mode, boolean publicationComplete) {
            int source = coordinator.currentSegmentIndex();
            if (!publicationComplete) {
                throw new AssertionError(
                        "cannot close represented segment " + source
                                + " before its comparator publication completed");
            }
            RunPlaybackObservation current = observation(mode, true, 0, false);
            RunPlaybackObservation production = productionOwnerSegmentIndex == source
                    && productionOwnerObservation != null
                    ? withProductionOwner(current, productionOwnerObservation)
                    : current;
            List<TraceRunPlaybackCoordinator.Action> emitted =
                    coordinator.afterProduction(production);
            actions.addAll(emitted);
            if (emitted.isEmpty()
                    || !(emitted.getFirst()
                            instanceof TraceRunPlaybackCoordinator.CloseSegment close)
                    || close.segmentIndex() != source) {
                throw new AssertionError(
                        "coordinator did not close represented segment " + source);
            }
        }

        private void beforeProduction(GameMode mode) {
            productionOwnerSegmentIndex = coordinator.currentSegmentIndex();
            productionOwnerObservation = observation(mode, false, 0, false);
        }

        private void afterStep(GameMode mode) {
            admittedStepOrdinal++;
            RunPlaybackObservation current = observation(mode, false, 0, false);
            RunPlaybackObservation step = productionOwnerSegmentIndex
                    == coordinator.currentSegmentIndex()
                    && productionOwnerObservation != null
                    ? withProductionOwner(current, productionOwnerObservation)
                    : current;
            List<TraceRunPlaybackCoordinator.Action> emitted =
                    coordinator.afterStep(step);
            actions.addAll(emitted);
            if (!emitted.isEmpty()) {
                TraceRunPlaybackCoordinator.Action action = emitted.getFirst();
                if (action instanceof TraceRunPlaybackCoordinator.FailRun failure) {
                    throw new AssertionError(failure.diagnostic());
                }
                throw new AssertionError(
                        "unexpected coordinator action after a headless step: " + action);
            }
        }

        private static RunPlaybackObservation withProductionOwner(
                RunPlaybackObservation current,
                RunPlaybackObservation owner) {
            return new RunPlaybackObservation(
                    owner.mode(), current.sharedBk2Cursor(),
                    current.admittedStepOrdinal(), owner.level(),
                    current.initialTitleCardPending(), owner.bonus(),
                    owner.specialStageIndex(), current.productionOpen(),
                    current.currentSegmentExhausted(),
                    current.destinationRowsConsumed(),
                    current.lagOnlySameLevelContinuation(),
                    current.timingScheduleGeneration(),
                    current.dynamicArtGeneration());
        }

        private DestinationAdmissionReceipt admitInterior(
                TraceRunManifest.Transition boundary,
                int observedBk2Frame,
                GameMode mode,
                int rowsConsumed) {
            int destinationIndex = coordinator.currentSegmentIndex() + 1;
            RunBoundarySignal signal = switch (boundary.entryKind()) {
                case "starpost_bonus" -> {
                    var bonus = GameServices.bonusStageOrNull();
                    if (bonus == null
                            || bonus.getActiveType() == BonusStageType.NONE) {
                        throw new AssertionError(
                                "production bonus identity is unavailable at admission");
                    }
                    yield new RunBoundarySignal.BonusRequest(
                            observedBk2Frame, bonus.getActiveType());
                }
                case "giant_ring", "starpost_special" ->
                        new RunBoundarySignal.SpecialStageRequest(
                                observedBk2Frame,
                                GameServices.module().getSpecialStageProvider()
                                        .getCurrentStage());
                default -> throw new AssertionError(
                        "not an interior entry boundary: " + boundary.entryKind());
            };
            coordinator.observeBoundary(signal);
            return requireAdmission(
                    coordinator.beforeAdmission(
                            observation(mode, false, rowsConsumed, false)),
                    destinationIndex,
                    rowsConsumed);
        }

        private DestinationAdmissionReceipt admitLevel(
                TraceRunManifest.Transition boundary,
                int observedBk2Frame,
                GameMode mode,
                int rowsConsumed,
                boolean lagOnlyContinuation,
                RunLevelLoadTracker.Receipt observedLoad) {
            int destinationIndex = coordinator.currentSegmentIndex() + 1;
            if (lagOnlyContinuation) {
                return requireAdmission(
                        coordinator.beforeAdmission(observation(
                                mode, false, rowsConsumed, true)),
                        destinationIndex, rowsConsumed);
            }
            RunLevelLoadTracker.Receipt load = Objects.requireNonNull(
                    observedLoad, "production load receipt");
            if (boundary != null && "stage_exit".equals(boundary.entryKind())) {
                coordinator.observeBoundary(
                        new RunBoundarySignal.StageExit(observedBk2Frame));
            }
            RunPlaybackObservation observed =
                    observation(mode, false, rowsConsumed, false);
            RunBoundarySignal.LevelLoaded loaded =
                    new RunBoundarySignal.LevelLoaded(
                            observedBk2Frame, load.cause(), load.identity());
            List<TraceRunPlaybackCoordinator.Action> emitted =
                    coordinator.beforeLoadedLevelActivation(loaded, observed);
            if (emitted.isEmpty()) {
                emitted = coordinator.beforeAdmission(observed);
            }
            return requireAdmission(
                    emitted, destinationIndex, rowsConsumed);
        }

        private DestinationAdmissionReceipt admitPresentationBridge(
                TraceRunManifest.Transition boundary,
                int observedBk2Frame,
                GameMode mode) {
            int destinationIndex = coordinator.currentSegmentIndex() + 1;
            return requireAdmission(
                    coordinator.beforeAdmission(
                            observation(mode, false, 0, false)),
                    destinationIndex, 0);
        }

        private TraceRunReplayWalker.TerminalMovieTailPlan terminalPlan() {
            if (actions.isEmpty()) {
                throw new AssertionError("coordinator emitted no terminal action");
            }
            TraceRunPlaybackCoordinator.Action last = actions.getLast();
            if (last instanceof TraceRunPlaybackCoordinator.BeginTerminalTail tail) {
                return tail.plan();
            }
            if (last instanceof TraceRunPlaybackCoordinator.CompleteRun) {
                return null;
            }
            throw new AssertionError(
                    "coordinator did not choose a terminal plan: " + last);
        }

        private void finishTerminal(GameMode actualMode) {
            List<TraceRunPlaybackCoordinator.Action> emitted =
                    coordinator.finishTerminalTail(actualMode);
            if (!emitted.isEmpty()) {
                actions.addAll(emitted);
                if (!(emitted.getFirst()
                        instanceof TraceRunPlaybackCoordinator.CompleteRun)) {
                    throw new AssertionError(
                            "coordinator rejected terminal mode " + actualMode);
                }
            }
            if (coordinator.phase()
                    != TraceRunPlaybackCoordinator.Phase.COMPLETE) {
                throw new AssertionError(
                        "coordinator did not complete the run: "
                                + coordinator.phase());
            }
        }

        private List<TraceRunPlaybackCoordinator.Action> actions() {
            return List.copyOf(actions);
        }

        private RunLevelLoadTracker.Receipt latestLoadReceipt() {
            return levelLoads.latest().orElseThrow(() -> new AssertionError(
                    "production did not publish a level-load receipt"));
        }

        private DestinationAdmissionReceipt requireAdmission(
                List<TraceRunPlaybackCoordinator.Action> emitted,
                int segmentIndex,
                int rowsConsumed) {
            actions.addAll(emitted);
            if (emitted.size() != 1
                    || !(emitted.getFirst()
                            instanceof TraceRunPlaybackCoordinator.AdmitDestination admit)) {
                throw new AssertionError(
                        "coordinator denied segment " + segmentIndex
                                + " admission in phase " + coordinator.phase());
            }
            DestinationAdmissionReceipt receipt = admit.receipt();
            TraceRunManifest.Segment segment = run.segments().get(segmentIndex);
            if (receipt.segmentIndex() != segmentIndex
                    || receipt.rowsConsumed() != rowsConsumed
                    || receipt.absoluteBk2Row()
                            != segment.bk2FrameOffset() + rowsConsumed) {
                throw new AssertionError(
                        "coordinator admitted the wrong destination row: " + receipt);
            }
            return receipt;
        }

        private RunPlaybackObservation observation(
                GameMode mode,
                boolean exhausted,
                int rowsConsumed,
                boolean lagOnlyContinuation) {
            var levelManager = GameServices.level();
            Object currentLevel = levelManager.getCurrentLevel();
            RunPlaybackObservation.LevelIdentity levelIdentity =
                    currentLevel == null ? null
                            : new RunPlaybackObservation.LevelIdentity(
                                    levelLoads.generation(),
                                    levelManager.getCurrentZone(),
                                    levelManager.getRomZoneId(),
                                    levelManager.getCurrentAct());
            RunPlaybackObservation.BonusIdentity bonusIdentity = null;
            var bonus = GameServices.bonusStageOrNull();
            if (mode == GameMode.BONUS_STAGE && bonus != null
                    && bonus.getActiveType() != BonusStageType.NONE) {
                bonusIdentity = new RunPlaybackObservation.BonusIdentity(
                        levelManager.getRomZoneId(),
                        levelManager.getCurrentAct(),
                        bonus.getActiveType());
            }
            Integer specialStageIndex = mode == GameMode.SPECIAL_STAGE
                    ? GameServices.module().getSpecialStageProvider()
                            .getCurrentStage()
                    : null;
            DynamicArtLifecycleService lifecycle =
                    SessionManager.getCurrentGameplayMode()
                            .dynamicArtLifecycle();
            var lifecycleState = lifecycle.capture();
            long stepOrdinal = admittedStepOrdinal;
            long dynamicGeneration =
                    GameServices.captureDynamicArtDiagnostics()
                            .segmentGeneration();
            return new RunPlaybackObservation(
                    mode,
                    Math.max(0,
                            GameServices.playbackDebug().getCursorFrame()),
                    stepOrdinal,
                    levelIdentity,
                    levelManager.isTitleCardRequested(),
                    bonusIdentity,
                    specialStageIndex,
                    lifecycleState.comparisonSegmentOpen(),
                    exhausted,
                    rowsConsumed,
                    lagOnlyContinuation,
                    Math.max(0, coordinator.currentSegmentIndex()),
                    dynamicGeneration);
        }
    }

    // -------------------------------------------------------------------------
    // Drive
    // -------------------------------------------------------------------------

    /**
     * The whole chain drive — the only method a lane subclass must call. Loads
     * and plans the run, boots segment 0, then walks every segment, awaiting
     * each boundary the engine raises and asserting return-boundary carry-over.
     */
    protected DynamicArtGapJournalEvidence assertChainReplay(Path runDir)
            throws Exception {
        return assertChainReplay(runDir, null);
    }

    protected DynamicArtGapJournalEvidence assertChainReplayThroughSegmentRow(
            Path runDir, int segmentIndex, int committedRows) throws Exception {
        if (segmentIndex < 0 || committedRows <= 0) {
            throw new IllegalArgumentException(
                    "prefix target requires a nonnegative segment and positive row count");
        }
        return assertChainReplay(
                runDir, new ReplayPrefixTarget(segmentIndex, committedRows));
    }

    private DynamicArtGapJournalEvidence assertChainReplay(
            Path runDir, ReplayPrefixTarget prefixTarget) throws Exception {
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
        // No game module exists until the ROM bootstrap below, so resolve the
        // first segment through the manifest game id. Subsequent seams use the
        // active module's profile.
        var bootProfile = "s1".equals(run.game())
                ? com.openggf.game.profiles.trace.TracePlaybackProfile.SONIC_1
                : com.openggf.game.profiles.trace.TracePlaybackProfile.DISABLED;
        var bootIdentity = bootProfile.resolveRecordedLevel(bootZone, manifestBootAct);
        Integer bootAct = bootIdentity.act();
        bootZone = bootIdentity.zone();
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
        boolean recordedHardwareTiming =
                TraceRunReplayWalker.hasHardwareTimingStream(plans);

        // Mirrors TestPachinkoTitleCardIntegration's engine setup: a
        // HeadlessTestFixture build initializes the headless engine before a real
        // GameLoop is constructed. Its sprite()/gameplayMode() are never read
        // afterward -- TraceReplayDriver.start() performs its own full reset +
        // team registration + level load, so a stale cached sprite reference
        // would desync from the engine's actual roster.
        HeadlessTestFixture.builder()
                .withZoneAndAct(bootZone, bootAct)
                .withHardwareReadinessAdmissionPolicy(
                        recordedHardwareTiming
                                ? HardwareReadinessAdmissionPolicy.RECORDED
                                : HardwareReadinessAdmissionPolicy.LIVE)
                .build();
        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);

        LiveEngineFixture fixture = new LiveEngineFixture(movie);
        TraceReplayDriver driver = new TraceReplayDriver(
                trace0, movie, fixture, loop, fixture::sprite, () -> { },
                recordedHardwareTiming);
        driver.start(bootZone, bootAct);
        int initialComparisonCursor = driver.initialCursor();

        HardwareTimingCoordinator hardwareTiming =
                new HardwareTimingCoordinator(
                        fixture, TraceRunReplayWalker.hardwareTimingSegments(plans));
        GameplayModeContext gameplayMode =
                SessionManager.getCurrentGameplayMode();
        gameplayMode.plcFrameLifecycle()
                .setComparisonSegmentsExternallyManaged(true);
        boolean[] firstDynamicArtWindow = {true};
        var dynamicArtSegments =
                new TraceRunReplayWalker.DynamicArtSegmentController(
                        new TraceRunReplayWalker.DynamicArtSegmentWindow() {
                            @Override
                            public void open() {
                                if (firstDynamicArtWindow[0]) {
                                    firstDynamicArtWindow[0] = false;
                                    gameplayMode.dynamicArtLifecycle()
                                            .serviceProductionVBlank();
                                }
                                gameplayMode.dynamicArtLifecycle()
                                        .openComparisonSegment();
                            }

                            @Override
                            public void close() {
                                gameplayMode.dynamicArtLifecycle()
                                        .closeComparisonSegment();
                            }
                        });
        dynamicArtSegments.beginSegment();
        // The initial standalone bootstrap may omit a recorded pre-level prefix.
        // Keep the read-only dynamic-art publication clock on the same first
        // compared row as the comparator/input cursor. This is initial adapter
        // alignment outside the coordinator transcript; later segments must earn
        // their publication generation through ordinary close/gap/open actions.
        gameplayMode.dynamicArtLifecycle()
                .advanceComparisonCursor(initialComparisonCursor);
        DynamicArtGapJournalProbe dynamicArtGapJournal =
                new DynamicArtGapJournalProbe(
                        gameplayMode.dynamicArtLifecycle());
        HeadlessRunCoordinatorAdapter runCoordinator =
                new HeadlessRunCoordinatorAdapter(run, movie, plans);
        activeRunCoordinator = runCoordinator;
        Throwable primaryFailure = null;
        boolean prefixReached = false;
        try {
            PlaybackDebugManager playback = GameServices.playbackDebug();
            RealEngineHooks hooks = new RealEngineHooks(loop);
            BoundaryProbe probe = new BoundaryProbe(hooks);
            probe.setBeforeFrameObserver(hardwareTiming::beginPlaybackFrame);
            // Replace the raw comparator TraceReplayDriver.start() installed with the
            // probe; the probe is the only observer for the rest of the chain,
            // delegating comparison to whichever segment comparator is attached.
            playback.setFrameObserver(probe);
            probe.setDelegate(driver.comparator());
            productionComparator = driver.comparator();
            runCoordinator.activateInitial(loop.getCurrentGameMode());

            // --- Step 3: walk every segment -------------------------------------
            LiveTraceComparator activeComparator = driver.comparator();
            SegmentPlan uncomparedInteriorSourceLevel = null;
            int uncomparedInteriorSourceVblank = 0;
            int i = 0;
            while (i < plans.size()) {
            SegmentPlan seg = plans.get(i);
            Object levelAtSegmentStart = GameServices.level().getCurrentLevel();
            TraceRunManifest.Transition exit = seg.exitBoundary();
            boolean last = (i == plans.size() - 1);

            if (exit == null) {
                // Last segment, OR a plain level->level boundary (no transition
                // record). Compare through this segment's recorded frames.
                int remainingFrames = TraceRunReplayWalker.remainingSegmentFrames(
                        seg.trace().frameCount(), activeComparator.cursor());
                stepFrames(loop, remainingFrames);
                activeComparator.finalizeTerminalDynamicArtComparison();
                requireComparatorComplete(seg, activeComparator);
                dynamicArtGapJournal.sourceClosed(seg.segment().dir());
                dynamicArtSegments.enterGap();
                runCoordinator.closeCurrent(
                        loop.getCurrentGameMode(), activeComparator.isComplete());
                maybeWriteReport(run.runId(), i, activeComparator);
                dynamicArtGapJournal.gapOpened(seg.segment().dir());
                if (last) {
                    productionComparator = null;
                    TraceRunFrameDriver terminalRows =
                            new TraceRunFrameDriver();
                    gameplayMode.installTraceRunFrameDriver(terminalRows);
                    try {
                        replayTerminalMovieTail(
                                runCoordinator.terminalPlan(), loop,
                                inputHandler, movie, playback, fixture,
                                terminalRows, runCoordinator);
                        dynamicArtGapJournal.verifyTerminal(
                                run, movie.getFrameCount());
                    } finally {
                        gameplayMode.clearTraceRunFrameDriver(terminalRows);
                    }
                    dynamicArtSegments.close();
                    hardwareTiming.close();
                    runCoordinator.finishTerminal(loop.getCurrentGameMode());
                    break;
                }
                SegmentPlan next = plans.get(i + 1);
                if (TraceRunReplayWalker.isLagOnlySameLevelContinuation(
                        seg.segment(), next.segment(), seg.trace().frameCount(),
                        activeComparator.laggedFrames())) {
                    int sourceTailVblank = TraceRunReplayWalker.sourceTailVblankAtBoundary(
                            seg.segment(), playback.getCursorFrame(),
                            GameServices.level().getObjectManager().getVblaCounter());
                    completeInterLevelVblankBudget(seg, next, 0, sourceTailVblank);
                    OscillationManager.suppressNextFrames(1);
                    runCoordinator.admitLevel(
                            null, playback.getCursorFrame(),
                            loop.getCurrentGameMode(), 0, true, null);
                    activeComparator = attachLevelSegment(
                            playback, probe, movie, next, fixture);
                    dynamicArtSegments.beginSegment();
                    dynamicArtGapJournal.nextSegmentArmed(
                            next.segment().dir());
                    i++;
                    continue;
                }
                // Plain level->level: cross the act/zone title-card cycle and
                // rebind onto the next level segment.
                int rowsConsumed = prepareAcrossLevelBoundary(
                        loop, playback, probe, movie, seg, next, stepCap,
                        levelAtSegmentStart);
                runCoordinator.admitLevel(
                        null, playback.getCursorFrame(),
                        loop.getCurrentGameMode(), rowsConsumed, false,
                        runCoordinator.latestLoadReceipt());
                activeComparator = attachPreparedLevelSegment(
                        playback, probe, movie, next, fixture, rowsConsumed);
                dynamicArtSegments.beginSegment();
                gameplayMode.dynamicArtLifecycle()
                        .advanceComparisonCursor(rowsConsumed);
                dynamicArtGapJournal.nextSegmentArmed(
                        next.segment().dir());
                i++;
                continue;
            }

            BoundaryEntryMode entryMode = TraceRunReplayWalker.boundaryEntryMode(exit.entryKind());
            if (entryMode == BoundaryEntryMode.LEVEL_MODE) {
                SegmentPlan returnSegment = plans.get(i + 1);
                if (TraceRunReplayWalker.isUncomparedInterior(seg.segment())
                        && returnSegment.executionPolicy()
                                == TraceRunReplayWalker.SegmentExecutionPolicy
                                        .LEVEL_PRESENTATION_BRIDGE) {
                    PresentationBridgeResult bridge =
                            replaySpecialStagePresentationBridge(
                                    runDir, run, plans, i, loop, inputHandler,
                                    movie, playback, probe, fixture,
                                    hardwareTiming, gameplayMode,
                                    dynamicArtSegments, dynamicArtGapJournal,
                                    runCoordinator, prefixTarget, stepCap,
                                    uncomparedInteriorSourceLevel,
                                    uncomparedInteriorSourceVblank);
                    uncomparedInteriorSourceLevel = null;
                    if (bridge.runComplete()) {
                        break;
                    }
                    activeComparator = bridge.gameplayComparator();
                    i = bridge.gameplaySegmentIndex();
                    continue;
                }
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
                // and no gameplay field comparison happens during this segment
                // (attachInteriorComparator keeps returning null for special_stage).
                // When the trace advertises the optional DPLC heartbeat, the
                // structural row driver compares only that diagnostic channel.
                boolean uncomparedInterior = TraceRunReplayWalker.isUncomparedInterior(seg.segment());
                Runnable stepOneFrame;
                TraceRunSpecialStageRowDriver interiorRows = null;
                boolean[] dynamicArtGapOpened = {false};
                boolean[] interiorCoordinatorSourceClosed = {false};
                if (uncomparedInterior) {
                    TraceRunSpecialStageRows specialRows;
                    try {
                        // The plan already parsed this interior with the
                        // segment's declared opening dynamic-art ledger; a
                        // bare re-load restarts that ledger empty and rejects
                        // a segment whose frame-0 state legitimately carries
                        // transfers over from the preceding segment.
                        specialRows = seg.specialStageRows() != null
                                ? seg.specialStageRows()
                                : TraceRunSpecialStageRows.load(
                                        seg.segment().traceProfile(),
                                        runDir.resolve(seg.segment().dir()),
                                        seg.segment()
                                                .dynamicArtInitialLedgerDescriptors());
                    } catch (IOException e) {
                        throw new AssertionError(
                                "Failed to load special-stage row policy for "
                                        + seg.segment().dir(), e);
                    }
                    assertEquals(seg.segment().traceFrameCount(),
                            specialRows.rowCount(),
                            "special-stage row policy must cover the represented segment");
                    IntConsumer driveInterior =
                            uncomparedInteriorStep(
                                    loop, inputHandler, movie, seg, specialRows);
                    interiorRows = new TraceRunSpecialStageRowDriver(
                            specialRows, seg.trace());
                    var rowDriver = interiorRows;
                    int segmentIndex = i;
                    stepOneFrame = () -> {
                        if (!rowDriver.isComplete()) {
                            if (loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE) {
                                throw new AssertionError(
                                        "special stage exited with "
                                                + (seg.segment().traceFrameCount()
                                                - rowDriver.cursor())
                                                + " represented rows remaining in "
                                                + seg.segment().dir());
                            }
                            DynamicArtDiagnosticsSnapshot before =
                                    GameServices.captureDynamicArtDiagnostics();
                            var admitted = rowDriver.admitCurrentRow(before);
                            int representedRow = admitted.row();
                            if (admitted.policy()
                                    .admitHardwareTiming()) {
                                hardwareTiming.beginSegmentRow(
                                        segmentIndex, representedRow);
                            }
                            driveInterior.accept(representedRow);
                            rowDriver.publishAdmittedRow(
                                    GameServices.captureDynamicArtDiagnostics());
                            if (prefixTarget != null
                                    && prefixTarget.segmentIndex() == segmentIndex
                                    && prefixTarget.committedRows()
                                            == rowDriver.cursor()) {
                                throw new ReplayPrefixReached();
                            }
                            if (rowDriver.isComplete()) {
                                rowDriver.verifyComplete();
                                dynamicArtGapJournal.sourceClosed(
                                        seg.segment().dir());
                                dynamicArtSegments.enterGap();
                                dynamicArtGapJournal.gapOpened(
                                        seg.segment().dir());
                                dynamicArtGapOpened[0] = true;
                                runCoordinator.closeCurrent(
                                        loop.getCurrentGameMode(), true);
                                interiorCoordinatorSourceClosed[0] = true;
                            }
                        } else {
                            fixture.enterHardwareTimingGap();
                            if (!dynamicArtGapOpened[0]) {
                                dynamicArtSegments.enterGap();
                                dynamicArtGapJournal.gapOpened(
                                        seg.segment().dir());
                                dynamicArtGapOpened[0] = true;
                            }
                            stepEngineFrame(loop);
                        }
                    };
                } else {
                    stepOneFrame = () -> stepEngineFrame(loop);
                }
                int returnOffset = plans.get(i + 1).segment().bk2FrameOffset();
                // The shared BK2 cursor is handled OPPOSITELY for the two interior
                // kinds, because they advance it oppositely:
                //
                //  * UNCOMPARED (special_stage): the shared cursor FREEZES at the
                //    interior-ENTRY offset for the whole interior -- SPECIAL_STAGE /
                //    RESULTS / fade / title-card never call onLevelFrameAdvanced -- and
                //    the SS itself is driven by uncomparedInteriorStep's own segment-local
                //    counter. Pre-seek the frozen cursor to the RETURN level segment's
                //    gameplay-unlock offset here so the ONE title-card-exit fall-through
                //    LEVEL frame (updateTitleCardMode releases control and FALLS THROUGH to
                //    LEVEL processing in the SAME loop.step(): GameLoop "Continue to LEVEL
                //    mode processing this frame") reads that segment's recorded frame-0
                //    input instead of the STALE entry-offset row (the star-post touch frame,
                //    which for this run holds a direction press) -- reading the stale row
                //    would accelerate the player one frame from rest and corrupt the return
                //    level's frame-0 physics. framesConsumed then == 1 (the fall-through
                //    frame advanced the seeked cursor by one).
                //
                //  * COMPARED (bonus_stage): the shared cursor is LIVE -- BONUS_STAGE runs
                //    on the level pipeline (GameLoop.updateBonusStageMode) and calls
                //    onLevelFrameAdvanced every frame, so the cursor drives the bonus stage
                //    forward from the interior offset and organically arrives at the return
                //    offset as the stage exits (each recorded segment gap == trace_frame_count
                //    + 1, the +1 being exactly that single fall-through boundary frame). It
                //    must NOT be pre-seeked: startSession(returnOffset) would jump the very
                //    cursor that drives the bonus stage straight to the stage_exit edge
                //    (returnOffset == modeChangeBk2Frame for every stage_exit in this run),
                //    so the first driven bonus frame pushes the cursor PAST the edge and
                //    awaitBoundary returns NOT_OBSERVED before the stage ever completes.
                //    Leaving it live lets the fade/title-card freeze (no onLevelFrameAdvanced)
                //    and the fall-through frame supply the +1, landing the cursor exactly on
                //    returnOffset (framesConsumed == 0).
                //
                // Comparison-only either way: this only positions the INPUT cursor, exactly
                // as handoffIntoInterior/attachLevelSegment already do -- no trace FIELD is
                // hydrated into engine state.
                if (uncomparedInterior) {
                    playback.startSession(movie, returnOffset);
                }
                BoundaryObservation obs =
                        TraceRunReplayWalker.awaitBoundary(probe, exit, stepCap, stepOneFrame);
                if (activeComparator != null) {
                    completePinnedSourceTailAfterBoundary(
                            loop, activeComparator, seg, stepCap,
                            activeComparator.cursor(), levelAtSegmentStart);
                }
                // Write the interior's comparator report BEFORE asserting the
                // boundary was observed -- a boundary miss is frequently caused
                // by an upstream interior divergence, and without this report a
                // failed assertTrue below would otherwise leave no artifact to
                // triage it from (maybeWriteReport is a no-op for uncompared
                // special-stage interiors).
                if (activeComparator != null) {
                    activeComparator.finalizeTerminalDynamicArtComparison();
                    requireComparatorComplete(seg, activeComparator);
                }
                maybeWriteReport(run.runId(), i, activeComparator);
                if (interiorRows != null) {
                    interiorRows.verifyComplete();
                    if (!interiorRows.comparisons().isEmpty()) {
                        writeDynamicArtInteriorReport(
                                run.runId(), i, interiorRows.comparisons());
                    }
                }
                if (!dynamicArtGapOpened[0]) {
                    dynamicArtGapJournal.sourceClosed(seg.segment().dir());
                    dynamicArtSegments.enterGap();
                }
                if (!interiorCoordinatorSourceClosed[0]) {
                    runCoordinator.closeCurrent(
                            loop.getCurrentGameMode(), uncomparedInterior
                                    ? dynamicArtGapOpened[0]
                                    : activeComparator != null
                                            && activeComparator.isComplete());
                }
                assertTrue(obs.observed(),
                        "Interior exit boundary (stage_exit) was never observed within the "
                                + "boundary window for " + runDir);
                assertReturnBoundary(plans, i, runDir);
                dynamicArtGapJournal.gapOpened(seg.segment().dir());
                // Attach the return comparator, keying on interior kind.
                int returnRowsConsumed;
                if (uncomparedInterior) {
                    if (GameServices.module().getTracePlaybackProfile()
                            .alignUncomparedInteriorReturnVblank()) {
                        if (uncomparedInteriorSourceLevel == null) {
                            throw new AssertionError(
                                    "Uncompared interior return has no source-level clock anchor");
                        }
                        alignUncomparedInteriorReturnVblank(
                                uncomparedInteriorSourceLevel, plans.get(i + 1),
                                uncomparedInteriorSourceVblank);
                        uncomparedInteriorSourceLevel = null;
                    }
                    // Pre-seeked SS interior: its single title-card-exit fall-through
                    // frame consumed the return segment's frame 0 (framesConsumed == 1)
                    // and the cursor is already in lockstep -- attach WITHOUT re-seeking.
                    int framesConsumed = playback.getCursorFrame() - returnOffset;
                    runCoordinator.admitLevel(
                            exit, obs.observedBk2Frame(),
                            loop.getCurrentGameMode(), framesConsumed, false,
                            runCoordinator.latestLoadReceipt());
                    activeComparator = attachReturnedLevelSegment(
                            probe, plans.get(i + 1), fixture, framesConsumed);
                    returnRowsConsumed = framesConsumed;
                } else {
                    // OPTION B (bonus interior): the engine's bonus-exit sequence is
                    // shorter than the recorded post-catch BONUS_STAGE tail, and ~80 of
                    // those recorded rows are the ROM's clearRAM/level-reload frames that
                    // the engine performs synchronously (loadZoneAndAct is one frame) --
                    // so the cursor cannot organically reach returnOffset (see
                    // docs/S3K_KNOWN_DISCREPANCIES.md, gumball exit choreography). The
                    // BONUS->LEVEL title-card-exit fall-through already ran the return
                    // segment's frame 0. Re-anchor the cursor to returnOffset+1 and
                    // compare the return level from frame 1. Input-cursor alignment only.
                    playback.startSession(movie, returnOffset + 1);
                    runCoordinator.admitLevel(
                            exit, obs.observedBk2Frame(),
                            loop.getCurrentGameMode(), 1, false,
                            runCoordinator.latestLoadReceipt());
                    activeComparator = attachReturnedLevelSegment(
                            probe, plans.get(i + 1), fixture, 1);
                    returnRowsConsumed = 1;
                }
                dynamicArtSegments.beginSegment();
                gameplayMode.dynamicArtLifecycle()
                        .advanceComparisonCursor(returnRowsConsumed);
                dynamicArtGapJournal.nextSegmentArmed(
                        plans.get(i + 1).segment().dir());
                i++;
            } else if (entryMode == BoundaryEntryMode.LEVEL_LOAD) {
                SegmentPlan next = plans.get(i + 1);
                RunLevelLoadTracker.Receipt[] observedLoad = {null};
                BoundaryObservation obs = TraceRunReplayWalker.awaitBoundary(
                        probe, exit, stepCap, () -> {
                            stepEngineFrame(loop);
                            if (isNewActiveLevelSegment(
                                    next, levelAtSegmentStart)) {
                                RunLevelLoadTracker.Receipt receipt =
                                        runCoordinator.latestLoadReceipt();
                                observedLoad[0] = receipt;
                                probe.observeSignal(
                                        new RunBoundarySignal.LevelLoaded(
                                                playback.getCursorFrame(),
                                                receipt.cause(),
                                                receipt.identity()));
                            }
                        });
                completePinnedSourceTailAfterBoundary(
                        loop, activeComparator, seg, stepCap,
                        activeComparator.cursor(), levelAtSegmentStart);
                activeComparator.finalizeTerminalDynamicArtComparison();
                requireComparatorComplete(seg, activeComparator);
                dynamicArtGapJournal.sourceClosed(seg.segment().dir());
                dynamicArtSegments.enterGap();
                runCoordinator.closeCurrent(
                        loop.getCurrentGameMode(), activeComparator.isComplete());
                maybeWriteReport(run.runId(), i, activeComparator);
                assertTrue(obs.observed(),
                        "Segment " + i + " (" + seg.segment().dir()
                                + ") semantic level-load boundary ("
                                + exit.entryKind()
                                + ") was never observed within the boundary window for "
                                + runDir);
                dynamicArtGapJournal.gapOpened(seg.segment().dir());
                int rowsConsumed = prepareAcrossLevelBoundary(
                        loop, playback, probe, movie, seg, next, stepCap,
                        levelAtSegmentStart);
                runCoordinator.admitLevel(
                        exit, obs.observedBk2Frame(),
                        loop.getCurrentGameMode(),
                        rowsConsumed, false,
                        Objects.requireNonNull(observedLoad[0],
                                "production level-load receipt was not observed"));
                activeComparator = attachPreparedLevelSegment(
                        playback, probe, movie, next, fixture, rowsConsumed);
                dynamicArtSegments.beginSegment();
                gameplayMode.dynamicArtLifecycle()
                        .advanceComparisonCursor(rowsConsumed);
                dynamicArtGapJournal.nextSegmentArmed(next.segment().dir());
                i++;
            } else {
                // This segment is a LEVEL; its exit is an ENTRY boundary into the
                // interior at i+1. Await the transient entry request, then hand
                // off into the interior mode.
                SegmentPlan interior = plans.get(i + 1);
                boolean anchorUncomparedInterior =
                        TraceRunReplayWalker.isUncomparedInterior(interior.segment())
                                && GameServices.module().getTracePlaybackProfile()
                                        .alignUncomparedInteriorReturnVblank();
                BoundaryObservation obs =
                        TraceRunReplayWalker.awaitBoundary(
                                probe, exit, stepCap, () -> stepEngineFrame(loop));
                completePinnedSourceTailAfterBoundary(
                        loop, activeComparator, seg, stepCap,
                        initialComparisonCursor, levelAtSegmentStart);
                // Report BEFORE asserting -- see the stage_exit branch above for
                // why: a level segment's own interior divergence is the usual
                // cause of a missed entry boundary, and this is the only report
                // this segment's comparator will ever get if the assert throws.
                activeComparator.finalizeTerminalDynamicArtComparison();
                requireComparatorComplete(seg, activeComparator);
                dynamicArtGapJournal.sourceClosed(seg.segment().dir());
                dynamicArtSegments.enterGap();
                runCoordinator.closeCurrent(
                        loop.getCurrentGameMode(), activeComparator.isComplete());
                maybeWriteReport(run.runId(), i, activeComparator);
                assertTrue(obs.observed(), "Segment " + i + " (" + seg.segment().dir()
                        + ") exit boundary (" + exit.entryKind()
                        + ") was never observed within the boundary window for " + runDir);
                if (anchorUncomparedInterior) {
                    uncomparedInteriorSourceLevel = seg;
                    uncomparedInteriorSourceVblank =
                            TraceRunReplayWalker.sourceTailVblankAtBoundary(
                                    seg.segment(), obs.observedBk2Frame(),
                                    GameServices.level().getObjectManager().getVblaCounter());
                }
                dynamicArtGapJournal.gapOpened(seg.segment().dir());
                int rowsConsumed = prepareIntoInterior(
                        loop, playback, probe, movie, interior, stepCap);
                runCoordinator.admitInterior(
                        exit, obs.observedBk2Frame(),
                        loop.getCurrentGameMode(), rowsConsumed);
                activeComparator = attachPreparedInterior(
                        probe, interior, fixture, rowsConsumed);
                dynamicArtSegments.beginSegment();
                gameplayMode.dynamicArtLifecycle()
                        .advanceComparisonCursor(rowsConsumed);
                dynamicArtGapJournal.nextSegmentArmed(
                        interior.segment().dir());
                i++;
            }
            }
            dynamicArtSegments.close();
            hardwareTiming.close();
        } catch (ReplayPrefixReached reached) {
            prefixReached = true;
            hardwareTiming.abort();
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            activeRunCoordinator = null;
            productionComparator = null;
            try {
                dynamicArtSegments.close();
            } catch (RuntimeException | Error closeFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(closeFailure);
                } else {
                    throw closeFailure;
                }
            }
            try {
                hardwareTiming.close();
            } catch (RuntimeException | Error closeFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(closeFailure);
                } else {
                    throw closeFailure;
                }
            }
        }
        if (!prefixReached) {
            dynamicArtGapJournal.verify(run);
        }
        return dynamicArtGapJournal.evidence(runCoordinator.actions());
    }

    private record ReplayPrefixTarget(int segmentIndex, int committedRows) {
    }

    private record PresentationBridgeResult(
            boolean runComplete,
            int gameplaySegmentIndex,
            LiveTraceComparator gameplayComparator) {
    }

    private static final class ReplayPrefixReached extends RuntimeException {
        private ReplayPrefixReached() {
            super(null, null, false, false);
        }
    }

    /**
     * Drives a special-stage return and its recorded native presentation on one
     * continuous physical BK2 clock. Both the visual launcher and this adapter
     * use {@link TraceRunFrameDriver}; only the production hook differs.
     */
    private PresentationBridgeResult replaySpecialStagePresentationBridge(
            Path runDir,
            TraceRunManifest run,
            List<SegmentPlan> plans,
            int specialIndex,
            GameLoop loop,
            InputHandler inputHandler,
            Bk2Movie movie,
            PlaybackDebugManager playback,
            BoundaryProbe probe,
            LiveEngineFixture fixture,
            HardwareTimingCoordinator hardwareTiming,
            GameplayModeContext gameplayMode,
            TraceRunReplayWalker.DynamicArtSegmentController dynamicArtSegments,
            DynamicArtGapJournalProbe dynamicArtGapJournal,
            HeadlessRunCoordinatorAdapter runCoordinator,
            ReplayPrefixTarget prefixTarget,
            int stepCap,
            SegmentPlan sourceLevel,
            int sourceVblank) throws Exception {
        SegmentPlan special = plans.get(specialIndex);
        int bridgeIndex = specialIndex + 1;
        SegmentPlan bridge = plans.get(bridgeIndex);
        TraceRunFrameDriver physicalRows = new TraceRunFrameDriver();
        gameplayMode.installTraceRunFrameDriver(physicalRows);
        probe.setDelegate(null);
        productionComparator = null;
        try {
            // Reuse the plan's parse (loaded with the segment's declared
            // opening dynamic-art ledger); re-loading here would restart the
            // ledger empty and reject a carried-over frame-0 state.
            TraceRunSpecialStageRows specialRows =
                    special.specialStageRows() != null
                            ? special.specialStageRows()
                            : TraceRunSpecialStageRows.load(
                                    special.segment().traceProfile(),
                                    runDir.resolve(special.segment().dir()),
                                    special.segment()
                                            .dynamicArtInitialLedgerDescriptors());
            TraceRunSpecialStageRowDriver specialDriver =
                    new TraceRunSpecialStageRowDriver(
                            specialRows, special.trace());
            IntConsumer driveSpecial = uncomparedInteriorStep(
                    loop, inputHandler, movie, special, specialRows);
            assertEquals(special.segment().bk2FrameOffset(),
                    playback.getCursorFrame(),
                    "special-stage shared clock must enter at its manifest offset");

            while (!specialDriver.isComplete()) {
                int localRow = specialDriver.cursor();
                int movieRow = playback.getCursorFrame();
                boolean[] hostStepRan = {false};
                physicalRows.execute(
                        new TraceRunFrameDriver.Step(
                                TraceRunFrameDriver.Disposition.SPECIAL_LOCAL,
                                movieRow,
                                localRow == specialDriver.rowCount() - 1),
                        new TraceRunFrameDriver.Hooks<DynamicArtDiagnosticsSnapshot>() {
                            @Override
                            public void preparePhysicalRow(
                                    TraceRunFrameDriver.Step step) {
                                assertEquals(
                                        special.segment().bk2FrameOffset()
                                                + localRow,
                                        step.movieRow());
                                stateMovieLogicalRow(step);
                            }

                            @Override
                            public void prepareHardwareTiming(
                                    TraceRunFrameDriver.Step step) {
                                var admission = specialRows.admission(localRow);
                                if (admission.admitHardwareTiming()) {
                                    hardwareTiming.beginSegmentRow(
                                            specialIndex, localRow);
                                } else {
                                    fixture.enterHardwareTimingGap();
                                }
                            }

                            @Override
                            public DynamicArtDiagnosticsSnapshot captureBefore(
                                    TraceRunFrameDriver.Step step) {
                                DynamicArtDiagnosticsSnapshot before =
                                        GameServices.captureDynamicArtDiagnostics();
                                specialDriver.admitCurrentRow(before);
                                return before;
                            }

                            @Override
                            public void runProductionLifecycle(
                                    TraceRunFrameDriver.Step step) {
                                hostStepRan[0] = specialRows.admission(localRow)
                                        .executeGameplay();
                                driveSpecial.accept(localRow);
                            }

                            @Override
                            public void advancePhysicalRow(
                                    TraceRunFrameDriver.Step step) {
                                playback.onLevelFrameAdvanced();
                            }

                            @Override
                            public DynamicArtDiagnosticsSnapshot captureAfter(
                                    TraceRunFrameDriver.Step step) {
                                return GameServices.captureDynamicArtDiagnostics();
                            }

                            @Override
                            public void compare(
                                    TraceRunFrameDriver.Step step,
                                    DynamicArtDiagnosticsSnapshot before,
                                    DynamicArtDiagnosticsSnapshot after) {
                                specialDriver.publishAdmittedRow(after);
                            }

                            @Override
                            public void afterStep(
                                    TraceRunFrameDriver.Step step) {
                                if (!hostStepRan[0]) {
                                    runCoordinator.afterStep(
                                            loop.getCurrentGameMode());
                                }
                            }
                        });
                if (prefixTarget != null
                        && prefixTarget.segmentIndex() == specialIndex
                        && prefixTarget.committedRows()
                                == specialDriver.cursor()) {
                    throw new ReplayPrefixReached();
                }
            }
            specialDriver.verifyComplete();
            if (!specialDriver.comparisons().isEmpty()) {
                writeDynamicArtInteriorReport(
                        run.runId(), specialIndex,
                        specialDriver.comparisons());
            }

            dynamicArtGapJournal.sourceClosed(special.segment().dir());
            dynamicArtSegments.enterGap();
            runCoordinator.closeCurrent(
                    loop.getCurrentGameMode(), true);
            dynamicArtGapJournal.gapOpened(special.segment().dir());

            int bridgeOffset = bridge.segment().bk2FrameOffset();
            while (playback.getCursorFrame() < bridgeOffset) {
                if (playback.getCursorFrame() + 1 > bridgeOffset) {
                    throw new AssertionError(
                            "physical clock crossed presentation offset ") ;
                }
                driveHeadlessTransitionRow(
                        physicalRows,
                        TraceRunFrameDriver.Disposition.SHARED_GAP,
                        loop, inputHandler, movie, playback, fixture,
                        runCoordinator);
            }
            int stageExitFrame = playback.getCursorFrame();
            runCoordinator.admitPresentationBridge(
                    special.exitBoundary(), stageExitFrame,
                    loop.getCurrentGameMode());
            if (sourceLevel != null
                    && GameServices.module().getTracePlaybackProfile()
                            .alignUncomparedInteriorReturnVblank()) {
                alignUncomparedInteriorReturnVblank(
                        sourceLevel, bridge, sourceVblank);
            }

            dynamicArtSegments.beginSegment();
            dynamicArtGapJournal.nextSegmentArmed(bridge.segment().dir());

            TraceStructuralRowComparator structural =
                    new TraceStructuralRowComparator(
                            bridge.trace(), ToleranceConfig.DEFAULT, 0);
            List<FrameComparison> comparisons = new ArrayList<>();
            while (!structural.allRowsConsumed()) {
                int localRow = structural.cursor();
                int movieRow = playback.getCursorFrame();
                var rowPolicy = TraceReplayRowPolicy.resolve(
                        bridge.trace(), localRow, movieRow);
                boolean previousObservedVblank = localRow == 0
                        || TraceReplayRowPolicy.resolve(
                                bridge.trace(), localRow - 1, movieRow - 1)
                                .observedVblankCounterAdvance();
                TraceRunFrameDriver.Disposition disposition =
                        TraceRunFrameDriver.selectDisposition(
                                TraceRunPlaybackCoordinator.Phase.CURRENT_SEGMENT,
                                bridge.executionPolicy(), rowPolicy.phase(),
                                rowPolicy.observedVblankCounterAdvance(),
                                previousObservedVblank,
                                loop.getCurrentGameMode() == GameMode.LEVEL);
                boolean deferBoundaryCommit = false;
                if (localRow + 1 < bridge.trace().frameCount()) {
                    TraceReplayRowPolicy nextRowPolicy =
                            TraceReplayRowPolicy.resolve(
                                    bridge.trace(), localRow + 1, movieRow + 1);
                    deferBoundaryCommit = TraceRunFrameDriver
                            .shouldDeferBoundaryCommit(
                                    rowPolicy.observedVblankCounterAdvance(),
                                    nextRowPolicy
                                            .observedVblankCounterAdvance());
                }
                boolean[] hostStepRan = {false};
                boolean commitDeferredBoundaryAfterClosure = TraceRunFrameDriver
                        .shouldCommitDeferredBoundaryAfterClosure(
                                previousObservedVblank,
                                rowPolicy.observedVblankCounterAdvance());
                physicalRows.execute(
                        new TraceRunFrameDriver.Step(
                                disposition, movieRow,
                                localRow == bridge.trace().frameCount() - 1,
                                deferBoundaryCommit,
                                commitDeferredBoundaryAfterClosure,
                                rowPolicy.observedVblankCounterAdvance()),
                        new TraceRunFrameDriver.Hooks<DynamicArtDiagnosticsSnapshot>() {
                            @Override
                            public void preparePhysicalRow(
                                    TraceRunFrameDriver.Step step) {
                                stateMovieLogicalRow(step);
                                Bk2FrameInput current =
                                        playback.currentFrameOrThrow();
                                Bk2FrameInput previous = movieRow > 0
                                        ? movie.getFrame(movieRow - 1) : null;
                                inputHandler.setLogicalOverride(
                                        RecordedInputSnapshots.fromBk2(
                                                current, previous));
                                structural.prepareRow(current);
                            }

                            @Override
                            public void prepareHardwareTiming(
                                    TraceRunFrameDriver.Step step) {
                                hardwareTiming.beginPlaybackFrame(
                                        playback.currentFrameOrThrow());
                            }

                            @Override
                            public DynamicArtDiagnosticsSnapshot captureBefore(
                                    TraceRunFrameDriver.Step step) {
                                return GameServices.captureDynamicArtDiagnostics();
                            }

                            @Override
                            public void runProductionLifecycle(
                                    TraceRunFrameDriver.Step step) {
                                hostStepRan[0] = true;
                                assertTrue(inputHandler.hasLogicalOverride(),
                                        "presentation production must receive "
                                                + "the physical BK2 input");
                                if (step.disposition()
                                        == TraceRunFrameDriver.Disposition
                                                .PRESENTATION_SUPPRESSED_CLOSURE) {
                                    runCoordinator.beforeProduction(
                                            loop.getCurrentGameMode());
                                    TraceRunPresentationClosure.execute(loop, step);
                                    runCoordinator.afterStep(
                                            loop.getCurrentGameMode());
                                } else {
                                    stepEngineFrame(loop);
                                }
                            }

                            @Override
                            public void advancePhysicalRow(
                                    TraceRunFrameDriver.Step step) {
                                playback.onLevelFrameAdvanced();
                            }

                            @Override
                            public DynamicArtDiagnosticsSnapshot captureAfter(
                                    TraceRunFrameDriver.Step step) {
                                return GameServices.captureDynamicArtDiagnostics();
                            }

                            @Override
                            public void compare(
                                    TraceRunFrameDriver.Step step,
                                    DynamicArtDiagnosticsSnapshot before,
                                    DynamicArtDiagnosticsSnapshot after) {
                                FrameComparison comparison =
                                        structural.completePostProduction(
                                                before, after,
                                                step.disposition()
                                                        .runsProductionLifecycle());
                                if (comparison != null) {
                                    comparisons.add(comparison);
                                }
                                if (prefixTarget != null
                                        && prefixTarget.segmentIndex()
                                                == bridgeIndex
                                        && prefixTarget.committedRows()
                                                == structural.cursor()) {
                                    // A special-stage results bridge does NOT
                                    // end in gameplay. Its recorded rows are the
                                    // ROM's results card, which ends at
                                    // SSR_Exit followed by PaletteWhiteOut, so
                                    // the returning level loads on the FIRST row
                                    // after the bridge and its title card is
                                    // what shows there
                                    // ("_incObj/7E, 7F Special Stage Results and
                                    // Chaos Emeralds.asm":157-159,
                                    // sonic.asm:3419-3421). Requiring LEVEL here
                                    // required the engine to finish the whole
                                    // card early, which is the divergence this
                                    // lane exists to catch.
                                    assertNotEquals(GameMode.SPECIAL_STAGE,
                                            loop.getCurrentGameMode(),
                                            "presentation bridge must leave the special stage");
                                    throw new ReplayPrefixReached();
                                }
                            }

                            @Override
                            public void afterStep(
                                    TraceRunFrameDriver.Step step) {
                                try {
                                    if (!hostStepRan[0]) {
                                        runCoordinator.afterStep(
                                                loop.getCurrentGameMode());
                                    }
                                } finally {
                                    inputHandler.clearLogicalOverride();
                                }
                            }
                        });
            }

            dynamicArtGapJournal.sourceClosed(bridge.segment().dir());
            dynamicArtSegments.enterGap();
            FrameComparison terminal = structural.finalizeSegment(
                    GameServices.captureDynamicArtDiagnostics());
            if (terminal != null) {
                comparisons.add(terminal);
            }
            assertStructuralComparisonsGreen(
                    bridge.segment().dir(), comparisons);
            runCoordinator.closeCurrent(
                    loop.getCurrentGameMode(), structural.isComplete());
            dynamicArtGapJournal.gapOpened(bridge.segment().dir());

            if (bridgeIndex == plans.size() - 1) {
                TraceRunReplayWalker.TerminalMovieTailPlan tail =
                        runCoordinator.terminalPlan();
                if (tail != null) {
                    // rowsToReplay counts rows, so tailStart + rowsToReplay is
                    // the movie's frame COUNT -- one past the last row a cursor
                    // can ever hold. Playback pins on its last valid row and
                    // stops, so waiting for the count itself never returns:
                    // this loop hung the whole class rather than failing it.
                    // Drive until the cursor stops advancing, then prove it
                    // stopped because the movie ended.
                    int previousCursor = -1;
                    while (playback.getCursorFrame()
                                    < tail.tailStart() + tail.rowsToReplay()
                            && playback.getCursorFrame() != previousCursor) {
                        previousCursor = playback.getCursorFrame();
                        driveHeadlessTransitionRow(
                                physicalRows,
                                TraceRunFrameDriver.Disposition.TERMINAL_TAIL,
                                loop, inputHandler, movie, playback, fixture,
                                runCoordinator);
                    }
                    assertEquals(movie.getFrameCount() - 1,
                            playback.getCursorFrame(),
                            "terminal tail must consume the movie to its last row");
                    assertEquals(tail.expectedMode(),
                            loop.getCurrentGameMode(),
                            "Complete movie must finish in the manifest-declared mode");
                }
                assertReturnBoundary(plans, specialIndex, runDir);
                dynamicArtGapJournal.verifyTerminal(
                        run, movie.getFrameCount());
                dynamicArtSegments.close();
                hardwareTiming.close();
                runCoordinator.finishTerminal(
                        loop.getCurrentGameMode());
                return new PresentationBridgeResult(true, -1, null);
            }

            int gameplayIndex = bridgeIndex + 1;
            SegmentPlan gameplay = plans.get(gameplayIndex);
            int gameplayOffset = gameplay.segment().bk2FrameOffset();
            while (playback.getCursorFrame() < gameplayOffset) {
                driveHeadlessTransitionRow(
                        physicalRows,
                        TraceRunFrameDriver.Disposition.SHARED_GAP,
                        loop, inputHandler, movie, playback, fixture,
                        runCoordinator);
            }
            assertEquals(gameplayOffset, playback.getCursorFrame(),
                    "gameplay destination must open at its exact physical row");
            if (loop.getCurrentGameMode() != GameMode.LEVEL
                    || GameServices.level().isTitleCardRequested()) {
                throw new AssertionError(
                        "gameplay destination was not ready at its recorded offset");
            }
            assertReturnBoundary(plans, specialIndex, runDir);
            runCoordinator.admitLevel(
                    null, gameplayOffset, loop.getCurrentGameMode(),
                    0, true, null);
            dynamicArtSegments.beginSegment();
            dynamicArtGapJournal.nextSegmentArmed(
                    gameplay.segment().dir());
            LiveTraceComparator comparator =
                    attachReturnedLevelSegment(
                            probe, gameplay, fixture, 0);
            return new PresentationBridgeResult(
                    false, gameplayIndex, comparator);
        } finally {
            gameplayMode.clearTraceRunFrameDriver(physicalRows);
        }
    }

    /**
     * States the physical BK2 row a dynamic-art gap edge is stamped with, the
     * way {@code TraceSessionLauncher.driveRunPhysicalRow} does for a live run.
     *
     * <p>{@code DynamicArtLifecycleService.movieLogicalFrame} otherwise counts
     * production iterations, and a chain drives whole spans of rows —
     * transition gaps, shared gaps, the terminal tail — that run no production
     * iteration at all. Every such row is silently lost from the stamp, so a
     * gap edge raised late in a chain reports a row hundreds short of the one
     * the recorder wrote. The recorder's contract is the movie row it has
     * consumed ("tools/bizhawk-headless/src/Recording/S1RunCaptureRunner.cs":
     * 199-215), so the driver states it rather than inferring it.
     */
    private static void stateMovieLogicalRow(TraceRunFrameDriver.Step step) {
        SessionManager.getCurrentGameplayMode()
                .dynamicArtLifecycle()
                .setMovieLogicalFrame(step.movieRow());
    }

    private void driveHeadlessTransitionRow(
            TraceRunFrameDriver driver,
            TraceRunFrameDriver.Disposition disposition,
            GameLoop loop,
            InputHandler inputHandler,
            Bk2Movie movie,
            PlaybackDebugManager playback,
            LiveEngineFixture fixture,
            HeadlessRunCoordinatorAdapter coordinator) {
        int movieRow = playback.getCursorFrame();
        driver.execute(
                new TraceRunFrameDriver.Step(disposition, movieRow, false),
                new TraceRunFrameDriver.Hooks<DynamicArtDiagnosticsSnapshot>() {
                    @Override
                    public void preparePhysicalRow(
                            TraceRunFrameDriver.Step step) {
                        stateMovieLogicalRow(step);
                        Bk2FrameInput current = playback.currentFrameOrThrow();
                        Bk2FrameInput previous = movieRow > 0
                                ? movie.getFrame(movieRow - 1) : null;
                        inputHandler.setLogicalOverride(
                                RecordedInputSnapshots.fromBk2(
                                        current, previous));
                    }

                    @Override
                    public void prepareHardwareTiming(
                            TraceRunFrameDriver.Step step) {
                        fixture.enterHardwareTimingGap();
                    }

                    @Override
                    public DynamicArtDiagnosticsSnapshot captureBefore(
                            TraceRunFrameDriver.Step step) {
                        return GameServices.captureDynamicArtDiagnostics();
                    }

                    @Override
                    public void runProductionLifecycle(
                            TraceRunFrameDriver.Step step) {
                        stepEngineFrame(loop);
                    }

                    @Override
                    public void advancePhysicalRow(
                            TraceRunFrameDriver.Step step) {
                        playback.onLevelFrameAdvanced();
                    }

                    @Override
                    public DynamicArtDiagnosticsSnapshot captureAfter(
                            TraceRunFrameDriver.Step step) {
                        return GameServices.captureDynamicArtDiagnostics();
                    }

                    @Override
                    public void compare(
                            TraceRunFrameDriver.Step step,
                            DynamicArtDiagnosticsSnapshot before,
                            DynamicArtDiagnosticsSnapshot after) {
                    }

                    @Override
                    public void afterStep(
                            TraceRunFrameDriver.Step step) {
                        inputHandler.clearLogicalOverride();
                        if (!step.disposition().runsProductionLifecycle()) {
                            coordinator.afterStep(loop.getCurrentGameMode());
                        }
                    }
                });
    }

    private static void assertStructuralComparisonsGreen(
            String segmentDir,
            List<FrameComparison> comparisons) {
        FrameComparison firstFailure = comparisons.stream()
                .filter(comparison -> comparison.divergentFields().stream()
                        .anyMatch(field -> field.severity()
                                == com.openggf.trace.Severity.ERROR))
                .findFirst()
                .orElse(null);
        if (firstFailure != null) {
            throw new AssertionError(
                    "structural presentation comparison failed for "
                            + segmentDir + " at row " + firstFailure.frame()
                            + ": " + firstFailure.divergentFields());
        }
    }

    // -------------------------------------------------------------------------
    // Handoffs
    // -------------------------------------------------------------------------

    private void completePinnedSourceTailAfterBoundary(
            GameLoop loop, LiveTraceComparator comparator,
            SegmentPlan segment, int stepCap, int initialCursor,
            Object sourceLevel) {
        int steps = 0;
        int startCursor = comparator.cursor();
        List<GameMode> modePath = new ArrayList<>();
        while (!comparator.isComplete() && steps < stepCap) {
            GameMode mode = loop.getCurrentGameMode();
            if (modePath.isEmpty() || modePath.getLast() != mode) {
                modePath.add(mode);
            }
            GameMode sourceMode = TraceRunReplayWalker.expectedMode(
                    segment.segment());
            boolean levelOwnershipChanged = "level".equals(segment.segment().kind())
                    && GameServices.level().getCurrentLevel() != sourceLevel;
            if (mode != sourceMode || levelOwnershipChanged) {
                throw new AssertionError(
                        "source comparator cannot exhaust after boundary for "
                                + segment.segment().dir()
                                + ": production ownership already left " + sourceMode
                                + " at tail step " + steps + ", comparator cursor "
                                + comparator.cursor() + " of "
                                + segment.trace().frameCount()
                                + " (bootstrap initial cursor " + initialCursor
                                + "), mode path=" + modePath
                                + ", level ownership changed="
                                + levelOwnershipChanged);
            }
            try {
                stepEngineFrame(loop);
            } catch (RuntimeException | Error failure) {
                throw new AssertionError(
                        "pinned source-tail production failed for "
                                + segment.segment().dir() + " at tail step "
                                + steps + ", comparator cursor "
                                + comparator.cursor() + " of "
                                + segment.trace().frameCount()
                                + " (bootstrap initial cursor " + initialCursor
                                + "), mode path="
                                + modePath + ", current mode=" + mode,
                        failure);
            }
            steps++;
        }
        if (!comparator.isComplete()) {
            throw new AssertionError(
                    "source comparator did not exhaust after boundary for "
                            + segment.segment().dir()
                            + ": cursor " + startCursor + " -> "
                            + comparator.cursor() + " of "
                            + segment.trace().frameCount()
                            + " (bootstrap initial cursor " + initialCursor + ")"
                            + " after " + steps + " pinned tail steps; mode path="
                            + modePath + ", final mode="
                            + loop.getCurrentGameMode());
        }
    }

    private static void requireComparatorComplete(
            SegmentPlan segment, LiveTraceComparator comparator) {
        if (!comparator.isComplete()) {
            throw new AssertionError(
                    "source comparator is not complete for "
                            + segment.segment().dir() + ": cursor "
                            + comparator.cursor() + " of "
                            + segment.trace().frameCount());
        }
    }

    /**
     * Hands off from a level segment INTO an interior (bonus/special). Detaches
     * the comparator across the uncompared fade/title-card transition, waits for
     * the interior's expected mode, re-seeks the BK2 cursor to the interior's
     * offset, then attaches the interior comparator (or leaves it detached for a
     * special stage -- see {@link #attachInteriorComparator}).
     *
     * <p>The comparator's initial cursor for each interior kind (compared-bonus
     * ENTRY = 1, special = uncompared) follows the COMPARATOR FRAME BASE contract
     * on {@link #attachReturnedLevelSegment}.
     */
    private int prepareIntoInterior(
            GameLoop loop, PlaybackDebugManager playback, BoundaryProbe probe,
            Bk2Movie movie, SegmentPlan interior, int stepCap) {
        probe.setDelegate(null);
        productionComparator = null;
        int offset = interior.segment().bk2FrameOffset();
        GameMode target = TraceRunReplayWalker.expectedMode(interior.segment());
        if (!TraceRunReplayWalker.isUncomparedInterior(interior.segment())) {
            // COMPARED interior (bonus stage). The interior's FIRST gameplay tick is
            // the single title-card-exit fall-through frame: GameLoop.exitTitleCard
            // releases control and flips into BONUS_STAGE in the SAME loop.step(),
            // then that step's LevelFrameStep ticks the player. That fall-through
            // frame IS the recorded interior's frame 0, and for the S3K bonus
            // machines it is load-bearing: the player enters air-forced-false for
            // exactly one frame and, if the recorded frame-0 direction is pressed,
            // does a single grounded ground-move (e.g. the gumball's g_speed -0x0C
            // left nudge) before the ground probe finds no floor and flips it
            // airborne. If that tick reads a neutral/stale input the grounded nudge
            // is lost, the player free-falls with air-accel from frame 0, and the
            // interior trajectory diverges enough to push the stage exit past the
            // boundary window.
            //
            // Seek to the interior's recorded offset BEFORE waiting out the fade +
            // title card. The shared cursor is frozen across the fade/title-card
            // (those non-LEVEL/BONUS frames never call onLevelFrameAdvanced), so it
            // stays parked at this offset until the fall-through frame -- unlike
            // seeking AFTER waitForMode, which left the fall-through reading the
            // stale pre-entry row. GameLoop.exitTitleCard's bonus branch re-arms the
            // playback forced-input bridge right after flipping to BONUS_STAGE (the
            // step-top syncPlaybackInputBridge ran while still TITLE_CARD, which
            // PlaybackDebugManager.isDriving does not drive), so the fall-through
            // player tick then samples this parked offset = recorded frame 0. Input
            // alignment via the same forced-input bridge the interior already uses,
            // never trace-field hydration.
            playback.startSession(movie, offset);
            waitForMode(loop, target, stepCap);
            primeInteriorEntryRngFromMetadata(interior);
            // The fall-through frame already reproduced recorded frame 0 (the grounded
            // entry tick) and advanced the cursor to offset+1, so compare from frame 1
            // (the compared-bonus-ENTRY case of the COMPARATOR FRAME BASE contract on
            // attachReturnedLevelSegment: initialCursor == cursorFrame - offset == 1).
            return 1;
        }
        // UNCOMPARED interior (special stage): the SS is driven separately by
        // uncomparedInteriorStep; the shared cursor is not read for its physics, so
        // keep the simple seek-after-mode handoff.
        waitForMode(loop, target, stepCap);
        playback.startSession(movie, offset);
        primeInteriorEntryRngFromMetadata(interior);
        return 0;
    }

    private LiveTraceComparator attachPreparedInterior(
            BoundaryProbe probe, SegmentPlan interior,
            LiveEngineFixture fixture, int rowsConsumed) {
        LiveTraceComparator comparator;
        if (TraceRunReplayWalker.isUncomparedInterior(interior.segment())) {
            if (rowsConsumed != 0) {
                throw new AssertionError(
                        "uncompared interior admission consumed "
                                + rowsConsumed + " rows");
            }
            comparator = attachInteriorComparator(interior, fixture);
        } else {
            if (rowsConsumed != 1) {
                throw new AssertionError(
                        "compared interior admission expected one published row but saw "
                                + rowsConsumed);
            }
            comparator = new LiveTraceComparator(
                    interior.trace(), ToleranceConfig.DEFAULT,
                    rowsConsumed, fixture::sprite);
        }
        probe.setDelegate(comparator); // null => special-stage advance-uncompared
        productionComparator = comparator;
        return comparator;
    }

    /**
     * Primes the interior segment's entry-time RNG state from its OWN recorded
     * {@code metadata.rng_seed}, at the interior boundary -- the SAME
     * comparison-bootstrap seam the standalone bonus/special fixtures already run
     * once per replay (TraceReplaySessionBootstrap.performTraceReplayBootstrap ->
     * applyInitialRngSeedForReplay, TraceReplaySessionBootstrap.java:375).
     *
     * <p>Why the chain needs this and the standalone gets it for free: the ROM's
     * S3K bonus machines seed their RNG from the free-running hardware
     * {@code V_int_run_count} at machine init -- the gumball does
     * {@code move.l (V_int_run_count).w,(RNG_seed).w} (sonic3k.asm:127412), folding
     * power-on run history (menu time, prior acts) into the ball-subtype roll
     * (sub_612A8, sonic3k.asm:127988-128008). A standalone bonus trace boots
     * directly into the interior and its bootstrap applies the recorded frame-0
     * {@code rng_seed} (== that run's {@code V_int_run_count} at entry, e.g.
     * 0x1598 for gumball #1) before the first interior frame. The chain instead
     * reaches the interior ORGANICALLY from the preceding level replay, so the
     * shared engine RNG carries whatever state the preceding level left it in --
     * which is NOT the recorded run's entry seed, because the
     * engine has no faithful persistent global {@code V_int_run_count} (a
     * documented deferred gap; see docs/S3K_KNOWN_DISCREPANCIES.md). Without this
     * prime the gumball's ball series diverges mid-interior (f442) and dispenses a
     * different reward ball, so the on-return ring carry-over is off by one ball's
     * award (59 vs the recorded 69). Re-establishing the recorded entry seed at
     * the boundary reproduces the recorded ball series.
     *
     * <p>Comparison-only and NOT a carve-out: it reads one frame-0 bootstrap
     * datum ({@code metadata.rng_seed}) and applies it exactly once at the segment
     * boundary via the established {@code applyInitialRngSeedForReplay} helper --
     * identical to loading a save-state at the interior's BK2 start. No per-frame
     * trace field is ever hydrated into engine state, and it keys purely on
     * manifest metadata, never on zone/route/segment identity (the helper is a
     * no-op for any interior whose metadata lacks {@code rng_seed}). This is the
     * gumball-family (RNG_seed reseed) counterpart of the slots-family
     * {@code primeVIntRunCountForReplay} seam in
     * {@code TraceReplaySessionBootstrap.applyBonusStageEntry}: both re-establish
     * the recorded entry-time {@code V_int_run_count}-derived state the organic
     * chain entry cannot reproduce, each modelling the exact ROM read its machine
     * performs (gumball reseeds RNG_seed once; slots reads V_int_run_count live per
     * reel cycle).
     */
    private void primeInteriorEntryRngFromMetadata(SegmentPlan interior) {
        TraceReplaySessionBootstrap.applyInitialRngSeedForReplay(interior.trace().metadata());
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
        productionComparator = comparator;
        return comparator;
    }

    /**
     * Attaches the comparator for a level segment reached on RETURN from an
     * interior, when the engine's title-card-exit fall-through has already run
     * (and faithfully reproduced) {@code framesConsumed} leading LEVEL frames of
     * that segment from its own recorded input.
     *
     * <p>Unlike {@link #attachLevelSegment}, this method does not itself re-seek
     * the BK2 cursor. Its caller first establishes the required alignment: an
     * uncompared special-stage return keeps the pre-seeked cursor advanced by its
     * fall-through frame, while the bonus/Option-B return explicitly re-anchors
     * to {@code returnOffset + 1}. In both cases the cursor reaches
     * {@code returnOffset + framesConsumed} before this method attaches.
     * Starting the comparator at {@code framesConsumed} keeps the recording-frame
     * index and the BK2 cursor in lockstep, so the already-run leading frame(s)
     * are neither replayed a second time (which would double-apply a moving
     * gameplay-unlock frame like {@code seg3_ehz1} frame 0) nor skipped.
     *
     * <p><b>COMPARATOR FRAME BASE — authoritative contract for the whole chain.</b>
     * Every {@link LiveTraceComparator} in this class is constructed with an
     * <em>initial cursor</em> (its third argument) equal to the trace-frame index
     * of the FIRST frame the segment will compare. That index equals
     * {@code framesConsumed}: the number of leading segment frames the transition
     * machinery already ran — and faithfully reproduced from the segment's OWN
     * recorded input — before the comparator attached. Equivalently, the BK2 input
     * cursor at attach time sits at {@code segmentOffset + framesConsumed}, so the
     * comparator's initial cursor and {@code (cursorFrame - segmentOffset)} are
     * ALWAYS equal at attach. Keeping them in lockstep is what prevents replaying
     * an already-run frame (double-applying a moving gameplay-unlock frame) or
     * skipping one — the recurring off-by-one that corrupts a return segment's
     * frame-0 physics. Values by transition kind (each cited at its site; do not
     * re-derive them — reference this block):
     * <ul>
     *   <li><b>0</b> — plain level entry or a level rebind via
     *       {@link #attachLevelSegment}: the comparator attaches before the
     *       segment's frame 0 and the cursor is (re-)seeked to
     *       {@code segmentOffset}.</li>
     *   <li><b>1</b> — a compared bonus interior ENTRY
     *       ({@link #handoffIntoInterior}'s bonus/Option-B branch): the single
     *       title-card-exit fall-through frame already reproduced the interior's
     *       recorded frame 0, so comparison starts at frame 1.</li>
     *   <li><b>1</b> — a level RETURN after a special-stage (uncompared) interior
     *       (this method): the pre-seeked frozen cursor's one fall-through frame
     *       consumed the return segment's frame 0.</li>
     *   <li><b>1</b> — a level RETURN after a compared bonus interior (this
     *       method): Option B has already run the return segment's frame 0, then
     *       explicitly re-seeks the input cursor to {@code returnOffset + 1}
     *       before attaching the comparator at frame 1.</li>
     * </ul>
     */
    private LiveTraceComparator attachReturnedLevelSegment(
            BoundaryProbe probe, SegmentPlan level, LiveEngineFixture fixture, int framesConsumed) {
        LiveTraceComparator comparator = new LiveTraceComparator(
                level.trace(), ToleranceConfig.DEFAULT, framesConsumed, fixture::sprite);
        probe.setDelegate(comparator);
        productionComparator = comparator;
        return comparator;
    }

    /**
     * Crosses a plain level->level boundary that carries NO transition record
     * (e.g. S3K AIZ->HCZ seg 8->9, HCZ->MGZ seg 18->19). The engine runs an
     * act/zone title-card cycle. If that cycle completed inside the preceding
     * segment's recorded tail, the target level is already active and we rebind
     * immediately. Otherwise wait out of LEVEL and back, then rebind.
     */
    private int prepareAcrossLevelBoundary(
            GameLoop loop, PlaybackDebugManager playback, BoundaryProbe probe,
            Bk2Movie movie, SegmentPlan currentLevel, SegmentPlan nextLevel,
            int stepCap,
        Object levelAtSegmentStart) {
        int sourceTailVblank = TraceRunReplayWalker.sourceTailVblankAtBoundary(
                currentLevel.segment(), playback.getCursorFrame(),
                GameServices.level().getObjectManager().getVblaCounter());
        probe.setDelegate(null);
        if (!isNewActiveLevelSegment(nextLevel, levelAtSegmentStart)) {
            int offset = nextLevel.segment().bk2FrameOffset();
            playback.scheduleSessionAtNextLevelLoad(movie, offset);
            waitForModeToLeaveOrLevelActivate(
                    loop, GameMode.LEVEL, nextLevel, levelAtSegmentStart, stepCap);
            int firstGameplayFrame = playback.getCursorFrame() - offset;
            if (firstGameplayFrame < 0 || firstGameplayFrame > 1) {
                throw new AssertionError("Destination playback cursor advanced "
                        + firstGameplayFrame + " frames during level-load handoff");
            }
            completeInterLevelVblankBudget(
                    currentLevel, nextLevel, firstGameplayFrame, sourceTailVblank);
            if (loop.getCurrentGameMode() == GameMode.TITLE_CARD
                    || GameServices.level().isTitleCardRequested()) {
                if (loop.getCurrentGameMode() == GameMode.LEVEL) {
                    waitForModeToLeave(loop, GameMode.LEVEL, stepCap);
                }
                waitForMode(loop, GameMode.LEVEL, stepCap);
            }
            int settledFramesConsumed = playback.getCursorFrame() - offset;
            if (settledFramesConsumed < firstGameplayFrame || settledFramesConsumed > 1) {
                throw new AssertionError("Destination playback cursor advanced "
                        + settledFramesConsumed + " frames after title-card handoff");
            }
            // The level can become active while its title card still owns the
            // loop. Reconcile once more after that choreography settles so any
            // same-step LEVEL fall-through is included in the manifest-derived
            // budget and cannot leave the destination clock one tick adrift.
            completeInterLevelVblankBudget(
                    currentLevel, nextLevel, settledFramesConsumed, sourceTailVblank);
            return settledFramesConsumed;
        }
		var profile = GameServices.module().getTracePlaybackProfile();
		if (profile.reinitializeOscillationAtLoadedLevelAttach()) {
			// The destination Level has already loaded inside the source segment's
			// death/exit tail. Re-establish the ROM's OscillateNumInit boundary at
			// the point where the destination movie segment attaches; otherwise
			// engine-only choreography frames advance v_oscillate before recorded
			// gameplay frame 0. This is movie-clock lifecycle pacing only: no trace
			// field is read into engine state.
			OscillationManager.resetForSonic1();
        }
        completeInterLevelVblankBudget(currentLevel, nextLevel, 0, sourceTailVblank);
        return 0;
    }

    private LiveTraceComparator attachPreparedLevelSegment(
            PlaybackDebugManager playback, BoundaryProbe probe, Bk2Movie movie,
            SegmentPlan nextLevel, LiveEngineFixture fixture, int rowsConsumed) {
        int expectedCursor = nextLevel.segment().bk2FrameOffset() + rowsConsumed;
        if (playback.getCursorFrame() != expectedCursor) {
            if (rowsConsumed != 0) {
                throw new AssertionError(
                        "prepared destination cursor expected " + expectedCursor
                                + " but was " + playback.getCursorFrame());
            }
            playback.startSession(movie, expectedCursor);
        }
        LiveTraceComparator comparator = new LiveTraceComparator(
                nextLevel.trace(), ToleranceConfig.DEFAULT,
                rowsConsumed, fixture::sprite);
        probe.setDelegate(comparator);
        productionComparator = comparator;
        return comparator;
    }

    private void completeInterLevelVblankBudget(
            SegmentPlan currentLevel,
            SegmentPlan nextLevel,
            int nextFramesConsumed,
            int sourceTailVblank) {
        var profile = GameServices.module().getTracePlaybackProfile();
        if (!profile.alignsInterLevelVblank()) {
            return;
        }
        int requiredTicks = TraceRunReplayWalker.interLevelVblankBudget(
                currentLevel.segment(), nextLevel.segment(), nextFramesConsumed,
                profile.interLevelNonAdvancingMovieRows());
        var objectManager = GameServices.level().getObjectManager();
        int actualTicks = objectManager.getVblaCounter() - sourceTailVblank;
        if (actualTicks != requiredTicks) {
            // This is movie-clock pacing, not trace-field hydration: the target
            // comes only from BK2/manifest row counts plus the game profile's
            // measured non-advancing rows. It both fills shortened engine
            // transitions and removes synthetic host ticks that the ROM did not
            // count (notably S1's six-row death/act seam).
            objectManager.initVblaCounter(sourceTailVblank + requiredTicks);
        }
    }

    private void alignUncomparedInteriorReturnVblank(
            SegmentPlan sourceLevel,
            SegmentPlan returnLevel,
            int sourceVblank) {
        var profile = GameServices.module().getTracePlaybackProfile();
        if (!profile.alignUncomparedInteriorReturnVblank()) {
            return;
        }
        int requiredTicks = TraceRunReplayWalker.uncomparedInteriorReturnVblankBudget(
                sourceLevel.segment(), returnLevel.segment());
        // Movie-clock pacing only: the target derives from the source engine
        // counter and manifest/BK2 row distance. No trace field is read back
        // into engine state.
        GameServices.level().getObjectManager().initVblaCounter(sourceVblank + requiredTicks);
    }


    /**
     * SS-INTERIOR SEAM (policy v1 = ADVANCE-UNCOMPARED). Per-frame special-stage
     * field comparison is an explicitly LATER workflow; when it lands, build the
     * special-stage comparator HERE instead of returning {@code null} for a
     * {@code special_stage} segment. See "Decisions locked with the owner" item 1
     * in docs/architecture/designs/2026-07-18-multi-stage-trace-runs-design.md.
     *
     * <p>v1: a {@code bonus_stage} interior returns a per-frame
     * {@link LiveTraceComparator}; a {@code special_stage} interior returns
     * {@code null} so the boundary probe forwards to no gameplay comparator
     * across the whole special-stage phase. Its independent structural row
     * driver may still compare an advertised DPLC heartbeat.
     *
     * <p>The bonus comparator's initial cursor is {@code 0} (compares from the
     * interior's body frame 0); see the COMPARATOR FRAME BASE contract on
     * {@link #attachReturnedLevelSegment}.
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
        assertNotNull(exit, "Interior segment must have an exit boundary: " + runDir);
        SegmentPlan returnLevel = plans.get(interiorIndex + 1);
        SegmentPlan preEntry = plans.get(entry.fromSegment());
        assertTrue(returnLevel.trace().frameCount() > 0,
                "Return level segment has no recorded frames: " + runDir);
        Integer resolvedReturnZone = returnLevel.segment().zoneId() == null
                ? null
                : GameServices.module().getTracePlaybackProfile()
                        .resolveRecordedLevel(
                                returnLevel.segment().zoneId(),
                                returnLevel.segment().act())
                        .zone();
        AbstractPlayableSprite sprite =
                GameServices.camera().getFocusedSprite();
        assertNotNull(sprite,
                "Focused sprite missing on special-stage return for " + runDir);
        TraceRunBoundaryComparator.ExpectedBoundary expected =
                new TraceRunBoundaryComparator.ExpectedBoundary(
                        entry,
                        exit,
                        preEntry.segment(),
                        returnLevel.segment(),
                        returnLevel.trace().getFrame(0),
                        resolvedReturnZone);
        TraceRunBoundaryComparator.ActualBoundary actual =
                new TraceRunBoundaryComparator.ActualBoundary(
                        (int) sprite.getCentreX(),
                        (int) sprite.getCentreY(),
                        GameServices.level().getCheckpointState()
                                .getLastCheckpointIndex(),
                        GameServices.level().getCurrentZone(),
                        GameServices.level().getCurrentAct(),
                        GameServices.level().getLevelGamestate().getRings(),
                        GameServices.gameState().getEmeraldCount(),
                        emeraldCarryOverIsVerifiable(interior));
        FrameComparison comparison = TraceRunBoundaryComparator.compare(
                exit.modeChangeBk2Frame(), expected, actual);
        List<com.openggf.trace.FieldComparison> errors =
                comparison.divergentFields().stream()
                        .filter(field -> field.severity()
                                == com.openggf.trace.Severity.ERROR)
                        .toList();
        assertTrue(errors.isEmpty(),
                "Shared return-boundary comparison failed for " + runDir
                        + ": " + errors);
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
            int expectedZone = GameServices.module().getTracePlaybackProfile()
                    .resolveRecordedLevel(returnZone, returnLevel.segment().act()).zone();
            assertEquals(expectedZone, GameServices.level().getCurrentZone(),
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
    protected IntConsumer uncomparedInteriorStep(
            GameLoop loop,
            InputHandler inputHandler,
            Bk2Movie movie,
            SegmentPlan interior,
            TraceRunSpecialStageRows rows) {
        int bk2FrameOffset = interior.segment().bk2FrameOffset();
        return localRow -> {
            TraceRunSpecialStageRows.SpecialStageRowAdmission admission =
                    rows.admission(localRow);
            var beforeManager = GameServices.level().getObjectManager();
            int beforeVblank = beforeManager.getVblaCounter();
            admission.syntheticPlcPhase().ifPresent(
                    AbstractRunChainTest::stepUncomparedInteriorLifecycleRow);
            if (admission.executeGameplay()
                    && loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE) {
                int absoluteRow = bk2FrameOffset + localRow;
                Bk2FrameInput current = movie.getFrame(absoluteRow);
                Bk2FrameInput previous = absoluteRow > 0
                        ? movie.getFrame(absoluteRow - 1) : null;
                inputHandler.setLogicalOverride(
                        RecordedInputSnapshots.fromBk2(current, previous));
                try {
                    stepEngineFrame(loop);
                } finally {
                    inputHandler.clearLogicalOverride();
                }
            }
            var afterManager = GameServices.level().getObjectManager();
            if (admission.advancePreservedVblankIfUnchanged()
                    && afterManager.getVblaCounter() == beforeVblank) {
                afterManager.advanceVblaCounter();
            }
        };
    }

    /** Replays and asserts only the terminal behavior declared by the run manifest. */
    private void replayTerminalMovieTail(
            TraceRunReplayWalker.TerminalMovieTailPlan tailPlan,
            GameLoop loop, InputHandler inputHandler, Bk2Movie movie,
            PlaybackDebugManager playback, LiveEngineFixture fixture,
            TraceRunFrameDriver driver,
            HeadlessRunCoordinatorAdapter coordinator) {
        if (tailPlan == null) {
            return;
        }
        for (int row = 0; row < tailPlan.rowsToReplay(); row++) {
            assertEquals(tailPlan.tailStart() + row,
                    playback.getCursorFrame(),
                    "terminal tail must retain the continuous movie cursor");
            driveHeadlessTransitionRow(
                    driver, TraceRunFrameDriver.Disposition.TERMINAL_TAIL,
                    loop, inputHandler, movie, playback, fixture, coordinator);
        }

        System.out.printf("[TRACE-RUN-TAIL] rows=%d finalMode=%s%n",
                tailPlan.rowsToReplay(), loop.getCurrentGameMode());
        assertEquals(tailPlan.expectedMode(), loop.getCurrentGameMode(),
                "Complete movie must finish in the manifest-declared mode");
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
    IntConsumer specialStageDrivenStep(
            GameLoop loop, InputHandler inputHandler, Bk2Movie movie,
            int bk2FrameOffset, int recordedFrameCount) {
        return localRow -> {
            var beforeManager = GameServices.level().getObjectManager();
            int beforeVblank = beforeManager.getVblaCounter();
            int absoluteRow = bk2FrameOffset + localRow;
            Bk2FrameInput current = movie.getFrame(absoluteRow);
            Bk2FrameInput previous = absoluteRow > 0 ? movie.getFrame(absoluteRow - 1) : null;
            inputHandler.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
            try {
                stepEngineFrame(loop);
            } finally {
                inputHandler.clearLogicalOverride();
            }
            // Special-stage policy v1 deliberately advances without a field
            // comparator. Account for at most the recorded number of interior
            // VBlank rows. If the simplified engine choreography takes extra
            // host steps to reach LEVEL, those steps are not extra BK2 VBlanks;
            // if a step already advanced the preserved ObjectManager clock (for
            // example the title-card-exit LEVEL fall-through), do not double-tick.
            var afterManager = GameServices.level().getObjectManager();
            if (localRow < recordedFrameCount
                    && afterManager.getVblaCounter() == beforeVblank) {
                afterManager.advanceVblaCounter();
            }
        };
    }

    private static void stepUncomparedInteriorLifecycleRow(
            PlcLifecyclePhase phase) {
        SessionManager.getCurrentGameplayMode().plcFrameLifecycle()
                .runLogicalIteration(() -> {
                }, row -> {
                    if (row.claim(phase)) {
                        row.prepareAfterLoop(phase);
                    }
                    return null;
                });
    }

    private void stepFrames(GameLoop loop, int frameCount) {
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

    private void waitForModeToLeaveOrLevelActivate(
            GameLoop loop, GameMode from, SegmentPlan targetLevel,
            Object levelAtSegmentStart, int maxSteps) {
        int steps = 0;
        while (loop.getCurrentGameMode() == from
                && !isNewActiveLevelSegment(targetLevel, levelAtSegmentStart)) {
            stepEngineFrame(loop);
            steps++;
            if (steps >= maxSteps) {
				var segment = targetLevel.segment();
				var identity = GameServices.module().getTracePlaybackProfile()
						.resolveRecordedLevel(segment.zoneId(), segment.act());
                throw new AssertionError("Mode never left " + from + " within "
                        + maxSteps + " frames and target level never activated"
						+ " (target=" + segment.dir() + " " + identity.zone() + ":" + identity.act()
						+ ", actual=" + GameServices.level().getCurrentZone() + ":"
						+ GameServices.level().getCurrentAct() + ")");
            }
        }
    }

    private static boolean isNewActiveLevelSegment(
            SegmentPlan targetLevel, Object levelAtSegmentStart) {
        var segment = targetLevel.segment();
        if (!"level".equals(segment.kind()) || segment.zoneId() == null || segment.act() == null) {
            return false;
        }
        var identity = GameServices.module().getTracePlaybackProfile()
                .resolveRecordedLevel(segment.zoneId(), segment.act());
        return GameServices.level().getCurrentLevel() != levelAtSegmentStart
                && GameServices.level().getCurrentZone() == identity.zone()
                && GameServices.level().getCurrentAct() == identity.act();
    }

    private void waitForModeToLeave(GameLoop loop, GameMode from, int maxSteps) {
        int steps = 0;
        while (loop.getCurrentGameMode() == from) {
            stepEngineFrame(loop);
            if (++steps >= maxSteps) {
                throw new AssertionError("Mode never left " + from + " within "
                        + maxSteps + " frames (expected title-card entry)");
            }
        }
    }

    /** Advances one engine frame through the same outer PLC/fade lifecycle as live play. */
    void stepEngineFrame(GameLoop loop) {
        DynamicArtDiagnosticsSnapshot before =
                GameServices.captureDynamicArtDiagnostics();
        HeadlessRunCoordinatorAdapter coordinator = activeRunCoordinator;
        if (coordinator != null) {
            coordinator.beforeProduction(loop.getCurrentGameMode());
        }
        loop.step();
        LiveTraceComparator comparator = productionComparator;
        if (comparator != null) {
            comparator.consumePostProductionPlayableAnimationAction();
            DynamicArtDiagnosticsSnapshot after =
                    GameServices.captureDynamicArtDiagnostics();
            try {
                comparator.publishPendingDynamicArtComparison(before, after);
            } catch (IllegalStateException failure) {
                throw new IllegalStateException(
                        failure.getMessage() + "; before=" + before
                                + "; after=" + after,
                        failure);
            }
        }
        if (coordinator != null) {
            coordinator.afterStep(loop.getCurrentGameMode());
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
        assertCompletedSegmentComparison(segmentIndex, comparator);
        writeChainSegmentReport(runId, segmentIndex, comparator);
    }

    /** Adapter-neutral assertions a committed route can add at a completed source segment. */
    protected void assertCompletedSegmentComparison(
            int segmentIndex, LiveTraceComparator comparator) {
    }

    private void writeChainSegmentReport(String runId, int segmentIndex, LiveTraceComparator comparator)
            throws IOException {
        Files.createDirectories(REPORT_OUTPUT_DIR);
        Path jsonPath = REPORT_OUTPUT_DIR.resolve(runId + "_seg" + segmentIndex + "_report.json");
        Files.writeString(jsonPath, buildComparatorSummaryJson(comparator));
        assertTrue(Files.exists(jsonPath), "Chain segment report must be written: " + jsonPath);
    }

    private void writeDynamicArtInteriorReport(
            String runId,
            int segmentIndex,
            List<FrameComparison> comparisons) throws IOException {
        Files.createDirectories(REPORT_OUTPUT_DIR);
        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (FrameComparison comparison : comparisons) {
            comparison.divergentFields().forEach(field -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("frame", comparison.frame());
                row.put("field", field.fieldName());
                row.put("expected", field.expected());
                row.put("actual", field.actual());
                row.put("severity", field.severity().name());
                mismatches.add(row);
            });
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("comparisonCount", comparisons.size());
        summary.put("errorCount", comparisons.stream()
                .flatMap(comparison -> comparison.fields().values().stream())
                .filter(field -> field.severity()
                        == com.openggf.trace.Severity.ERROR)
                .count());
        summary.put("mismatches", mismatches);
        Path jsonPath = REPORT_OUTPUT_DIR.resolve(
                runId + "_seg" + segmentIndex
                        + "_dynamic_art_report.json");
        ObjectMapper mapper =
                new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(jsonPath, mapper.writeValueAsString(summary));
        assertTrue(mismatches.isEmpty(),
                "DPLC divergence in named-run special-stage segment "
                        + segmentIndex + "; report=" + jsonPath);
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
        MismatchEntry firstPhysics = comparator.firstNonCameraPhysicsMismatch();
        if (firstPhysics != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("frame", firstPhysics.frame());
            row.put("field", firstPhysics.field());
            row.put("romValue", firstPhysics.romValue());
            row.put("engineValue", firstPhysics.engineValue());
            row.put("delta", firstPhysics.delta());
            row.put("severity", firstPhysics.severity().name());
            summary.put("firstNonCameraPhysicsMismatch", row);
        }
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
        private HardwareTimingReplayPort hardwareTimingReplayPort;
        private TraceHardwareTimingBoundaryObserver hardwareTimingObserver;
        private boolean hardwareTimingReplayClosed;

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
        public void installHardwareTimingReplay(HardwareTimingReplayPort replayPort) {
            hardwareTimingReplayPort = replayPort;
            hardwareTimingObserver = new TraceHardwareTimingBoundaryObserver(replayPort);
            gameplayMode().getRewindRegistry().register(replayPort);
            gameplayMode().setHardwareTimingBoundaryObserver(hardwareTimingObserver);
            gameplayMode().setHardwareTimingReplayCloseHook(
                    this::closeHardwareTimingReplayRun);
        }

        @Override
        public void beginTraceRow(int traceIndex, int rawFrame) {
            if (hardwareTimingObserver != null) {
                hardwareTimingObserver.beginRawFrame(rawFrame);
            }
        }

        @Override
        public void enterHardwareTimingGap() {
            if (hardwareTimingObserver != null) {
                hardwareTimingObserver.enterUnrepresentedGap();
            }
        }

        @Override
        public void verifyHardwareTimingSegmentEdges() {
            if (hardwareTimingReplayPort != null) {
                hardwareTimingReplayPort.verifySegmentEdges();
            }
        }

        @Override
        public void handoffHardwareTimingReplay(HardwareTimingSchedule nextSchedule) {
            if (hardwareTimingReplayPort != null) {
                hardwareTimingReplayPort.handoffTo(nextSchedule);
            }
        }

        @Override
        public void closeHardwareTimingReplayRun() {
            if (hardwareTimingReplayClosed || hardwareTimingReplayPort == null) {
                return;
            }
            hardwareTimingReplayClosed = true;
            try {
                hardwareTimingReplayPort.verifyRunComplete();
            } finally {
                gameplayMode().setHardwareTimingBoundaryObserver(null);
                gameplayMode().getRewindRegistry()
                        .deregister(HardwareTimingReplayPort.REWIND_KEY);
                gameplayMode().clearHardwareTimingReplayCloseHook();
                hardwareTimingObserver = null;
            }
        }

        @Override
        public void abortHardwareTimingReplayRun() {
            if (hardwareTimingReplayClosed) {
                return;
            }
            hardwareTimingReplayClosed = true;
            gameplayMode().setHardwareTimingBoundaryObserver(null);
            gameplayMode().getRewindRegistry()
                    .deregister(HardwareTimingReplayPort.REWIND_KEY);
            gameplayMode().clearHardwareTimingReplayCloseHook();
            hardwareTimingObserver = null;
        }

        @Override
        public void suppressFirstSidekickAnimationOnce() {
            // Live action, honored identically to the canonical fixtures: hold the
            // first CPU sidekick's next Animate dispatch. No-op for solo runs
            // (e.g. the Knuckles mega-run) whose sidekick list is empty.
            var sprites = GameServices.sprites();
            if (!sprites.getSidekicks().isEmpty()) {
                sprites.getSidekicks().getFirst().getAnimationManager().suppressNextUpdate();
            }
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
