package com.openggf.tests.trace.runs;

import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.profiles.trace.TracePlaybackProfile;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.replay.runs.DestinationAdmissionReceipt;
import com.openggf.trace.replay.runs.RunBoundarySignal;
import com.openggf.trace.replay.runs.RunLevelLoadCause;
import com.openggf.trace.replay.runs.RunPlaybackObservation;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.Action;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.AdmitDestination;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.BeginTerminalTail;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.CloseSegment;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.EnterTransitionGap;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.FailRun;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRunPlaybackCoordinator {

    @Test
    void observationCarriesInitialTitleCardProductionBarrier() {
        assertTrue(Arrays.stream(
                        RunPlaybackObservation.class.getRecordComponents())
                .anyMatch(component -> component.getName()
                        .equals("initialTitleCardPending")));
    }

    @Test
    void initialLevelAdmissionCarriesExplicitRowZeroAndLoadGeneration() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("aiz", 0, 1, 100, 20)), List.of(), 200);

        List<Action> actions = coordinator.activateInitialLevel(
                levelObservation(100, 0, 0, 0, false, 0));

        AdmitDestination admission = assertInstanceOf(
                AdmitDestination.class, actions.getFirst());
        DestinationAdmissionReceipt receipt = admission.receipt();
        assertEquals(0, receipt.segmentIndex());
        assertEquals(DestinationAdmissionReceipt.InputClock.SHARED,
                receipt.inputClock());
        assertEquals(100, receipt.absoluteBk2Row());
        assertEquals(0, receipt.rowsConsumed());
        assertEquals(100, receipt.loadGeneration());
        assertEquals(TraceRunPlaybackCoordinator.Phase.CURRENT_SEGMENT,
                coordinator.phase());
    }

    @Test
    void initialLevelAdmissionRejectsTheRightIdentityInTheWrongMode() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("aiz", 0, 1, 100, 20)), List.of(), 200);
        RunPlaybackObservation wrongMode = new RunPlaybackObservation(
                GameMode.TITLE_CARD, 0, 0,
                new RunPlaybackObservation.LevelIdentity(100, 0, 0, 0),
                false, null, null, false, false, 0, false, 10, 20);

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.activateInitialLevel(wrongMode));
    }

    @Test
    void bonusAdmissionRequiresInWindowRequestAndMatchingIdentity() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(
                        level("aiz", 0, 1, 100, 20),
                        bonus("gumball", 19, 1, 121, 10, "gumball")),
                List.of(transition(0, "starpost_bonus", 121)), 200);
        coordinator.activateInitialLevel(levelObservation(7, 0, 0, 0, false, 0));

        coordinator.observeBoundary(new RunBoundarySignal.BonusRequest(
                121, BonusStageType.GUMBALL));
        List<Action> close = coordinator.afterProduction(
                levelObservation(7, 0, 0, 1, true, 0));
        assertEquals(List.of(new CloseSegment(0), new EnterTransitionGap(0, 1)), close);

        assertTrue(coordinator.beforeAdmission(bonusObservation(
                121, 2, BonusStageType.SLOT_MACHINE, 0)).isEmpty());
        AdmitDestination admission = assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(bonusObservation(
                        121, 2, BonusStageType.GUMBALL, 0)).getFirst());
        assertEquals(new DestinationAdmissionReceipt.BonusIdentity(
                        19, 0, BonusStageType.GUMBALL),
                admission.receipt().identity());

        TraceRunPlaybackCoordinator late = coordinator(
                List.of(level("aiz", 0, 1, 100, 20),
                        bonus("gumball", 19, 1, 121, 10, "gumball")),
                List.of(transition(0, "starpost_bonus", 121)), 200);
        late.activateInitialLevel(levelObservation(7, 0, 0, 0, false, 0));
        late.observeBoundary(new RunBoundarySignal.BonusRequest(
                121 + 121, BonusStageType.GUMBALL));
        late.afterProduction(levelObservation(7, 0, 0, 1, true, 0));
        assertTrue(late.beforeAdmission(bonusObservation(
                121, 2, BonusStageType.GUMBALL, 0)).isEmpty(),
                "an out-of-window request must not authorize the destination");
    }

    @Test
    void specialAdmissionRequiresRecordedStageIndexAndUsesLocalClock() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("ehz", 0, 1, 10, 5),
                        special("ss", 16, 9, 3)),
                List.of(transition(0, "starpost_special", 16)), 40);
        coordinator.activateInitialLevel(levelObservation(3, 0, 0, 0, false, 0));
        coordinator.observeBoundary(new RunBoundarySignal.SpecialStageRequest(16, 3));
        coordinator.afterProduction(levelObservation(3, 0, 0, 1, true, 0));

        assertTrue(coordinator.beforeAdmission(specialObservation(16, 1, 2, 0)).isEmpty());
        AdmitDestination admission = assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(specialObservation(16, 1, 3, 0)).getFirst());
        assertEquals(DestinationAdmissionReceipt.InputClock.SPECIAL_LOCAL,
                admission.receipt().inputClock());
        assertEquals(new DestinationAdmissionReceipt.SpecialStageIdentity(3),
                admission.receipt().identity());
    }

    @Test
    void matchingLevelLoadedInsideSourceTailIsRememberedWithoutAdmission() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("aiz", 0, 1, 100, 20),
                        level("hcz", 1, 1, 130, 20)),
                List.of(transition(0, "level_advance", 125)), 180);
        coordinator.activateInitialLevel(levelObservation(10, 0, 0, 0, false, 0));

        RunBoundarySignal.LevelLoaded loaded = new RunBoundarySignal.LevelLoaded(
                125, RunLevelLoadCause.LEVEL_ADVANCE,
                new RunPlaybackObservation.LevelIdentity(11, 1, 1, 0));
        assertTrue(coordinator.beforeLoadedLevelActivation(
                loaded, levelObservation(11, 1, 0, 1, false, 0)).isEmpty(),
                "an early load must not seek away from the source segment");
        assertEquals(0, coordinator.currentSegmentIndex());

        coordinator.afterProduction(levelObservation(10, 0, 0, 2, true, 0));
        AdmitDestination admission = assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(
                        levelObservation(11, 1, 0, 2, false, 0)).getFirst());
        assertEquals(11, admission.receipt().loadGeneration());
        assertEquals(130, admission.receipt().absoluteBk2Row());
    }

    @Test
    void matchingLoadDuringSourceTailWaitsForInitialTitleCardRelease() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("aiz", 0, 1, 100, 20),
                        level("hcz", 1, 1, 130, 20)),
                List.of(transition(0, "level_advance", 125)), 180);
        coordinator.activateInitialLevel(levelObservation(
                10, 0, 0, 0, false, 0, false, false));

        RunBoundarySignal.LevelLoaded loaded = new RunBoundarySignal.LevelLoaded(
                125, RunLevelLoadCause.LEVEL_ADVANCE,
                new RunPlaybackObservation.LevelIdentity(11, 1, 1, 0));
        assertTrue(coordinator.beforeLoadedLevelActivation(
                loaded, levelObservation(
                        11, 1, 0, 1, false, 0, false, true)).isEmpty());
        assertEquals(
                List.of(new CloseSegment(0), new EnterTransitionGap(0, 1)),
                coordinator.afterProduction(levelObservation(
                        10, 0, 0, 2, true, 0, false, true)));

        assertTrue(coordinator.beforeAdmission(levelObservation(
                11, 1, 0, 3, false, 0, false, true)).isEmpty());
        assertEquals(TraceRunPlaybackCoordinator.Phase.TRANSITION_GAP,
                coordinator.phase());
        assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(levelObservation(
                        11, 1, 0, 4, false, 0, false, false)).getFirst());
    }

    @Test
    void loadedDestinationIsNotAdmittedUntilLevelModeIsActive() {
        TraceRunPlaybackCoordinator coordinator = levelPairCoordinator();
        coordinator.activateInitialLevel(levelObservation(1, 0, 0, 0, false, 0));
        RunPlaybackObservation.LevelIdentity destination =
                new RunPlaybackObservation.LevelIdentity(2, 1, 1, 0);
        coordinator.beforeLoadedLevelActivation(
                new RunBoundarySignal.LevelLoaded(
                        15, RunLevelLoadCause.ORDINARY, destination),
                levelObservation(2, 1, 0, 1, false, 0));
        coordinator.afterProduction(levelObservation(1, 0, 0, 2, true, 0));
        RunPlaybackObservation titleCard = new RunPlaybackObservation(
                GameMode.TITLE_CARD, 0, 2, destination,
                false, null, null, false, false, 0, false, 10, 20);

        assertTrue(coordinator.beforeAdmission(titleCard).isEmpty());
        assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(
                        levelObservation(2, 1, 0, 2, false, 0)).getFirst());
    }

    @Test
    void everyLevelBoundaryKindRequiresItsSemanticLoadCause() {
        assertLevelBoundaryCause("level_advance", RunLevelLoadCause.LEVEL_ADVANCE);
        assertLevelBoundaryCause("death_restart", RunLevelLoadCause.DEATH_RESTART);
        assertLevelBoundaryCause(null, RunLevelLoadCause.ORDINARY);
    }

    @Test
    void transitionlessLevelAdjacencyAcceptsProductionLevelAdvanceOnly() {
        assertLevelBoundaryCause(null, RunLevelLoadCause.LEVEL_ADVANCE);

        for (RunLevelLoadCause rejected : List.of(
                RunLevelLoadCause.DEATH_RESTART,
                RunLevelLoadCause.INTERIOR_RETURN)) {
            TraceRunPlaybackCoordinator coordinator = coordinator(
                    List.of(level("a", 0, 1, 100, 10),
                            level("b", 1, 1, 120, 10)), List.of(), 160);
            coordinator.activateInitialLevel(
                    levelObservation(1, 0, 0, 0, false, 0));
            coordinator.beforeLoadedLevelActivation(
                    new RunBoundarySignal.LevelLoaded(115, rejected,
                            new RunPlaybackObservation.LevelIdentity(
                                    2, 1, 1, 0)),
                    levelObservation(2, 1, 0, 1, false, 0));
            coordinator.afterProduction(
                    levelObservation(1, 0, 0, 2, true, 0));

            assertTrue(coordinator.beforeAdmission(
                    levelObservation(2, 1, 0, 2, false, 0)).isEmpty(),
                    rejected + " must not satisfy transitionless adjacency");
        }
    }

    @Test
    void stageExitRequiresBothExitSignalAndMatchingReturnLoad() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("ehz", 0, 1, 0, 10),
                        special("ss", 10, 5, 0),
                        level("ehz_return", 0, 1, 20, 10)),
                List.of(transition(0, "starpost_special", 10),
                        transition(1, "stage_exit", 20)), 50);
        coordinator.activateInitialLevel(levelObservation(1, 0, 0, 0, false, 0));
        coordinator.observeBoundary(new RunBoundarySignal.SpecialStageRequest(10, 0));
        coordinator.afterProduction(levelObservation(1, 0, 0, 1, true, 0));
        coordinator.beforeAdmission(specialObservation(10, 1, 0, 0));
        coordinator.observeBoundary(new RunBoundarySignal.StageExit(20));
        RunBoundarySignal.LevelLoaded loaded =
                new RunBoundarySignal.LevelLoaded(20,
                        RunLevelLoadCause.INTERIOR_RETURN,
                        new RunPlaybackObservation.LevelIdentity(2, 0, 0, 0));
        coordinator.beforeLoadedLevelActivation(
                loaded,
                levelObservation(2, 0, 0, 1, false, 0));
        assertTrue(coordinator.remembersLevelLoad(loaded));
        assertFalse(coordinator.remembersLevelLoad(
                new RunBoundarySignal.LevelLoaded(20,
                        RunLevelLoadCause.INTERIOR_RETURN,
                        new RunPlaybackObservation.LevelIdentity(3, 0, 0, 0))));
        RunPlaybackObservation exhaustedSpecial = new RunPlaybackObservation(
                GameMode.SPECIAL_STAGE, 10, 2, null, false, null, 0,
                false, true, 0, false, 12, 22);
        coordinator.afterProduction(exhaustedSpecial);

        assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(
                        levelObservation(2, 0, 0, 2, false, 0)).getFirst());
    }

    @Test
    void presentationBridgeAdmitsAtItsPhysicalOffsetWithoutGameplayModeOrLoadHydration() {
        List<TraceRunManifest.Segment> segments = List.of(
                level("ghz1", 0, 1, 0, 5),
                special("ss", 5, 5, 0),
                level("ghz2_bridge", 0, 2, 11, 2));
        List<TraceRunManifest.Transition> transitions = List.of(
                transition(0, "giant_ring", 5),
                transition(1, "stage_exit", 10));
        TraceRunManifest run = run(segments, transitions,
                TraceRunManifest.ExpectedMovieEndMode.UNSPECIFIED);
        TraceData gameplay = executionTrace(1, 2);
        TraceData specialTrace = TraceFixtures.trace(
                TraceFixtures.metadata("s1", 0, 1), List.of());
        TraceData bridge = executionTrace(10, 10);
        List<TraceRunReplayWalker.SegmentPlan> plans = List.of(
                new TraceRunReplayWalker.SegmentPlan(
                        segments.get(0), gameplay, null, transitions.get(0)),
                new TraceRunReplayWalker.SegmentPlan(
                        segments.get(1), specialTrace, transitions.get(0),
                        transitions.get(1)),
                new TraceRunReplayWalker.SegmentPlan(
                        segments.get(2), bridge, transitions.get(1), null));
        TraceRunPlaybackCoordinator coordinator =
                new TraceRunPlaybackCoordinator(
                        run, TracePlaybackProfile.DISABLED, 30, plans);
        coordinator.activateInitialLevel(
                levelObservation(1, 0, 0, 0, false, 0));
        coordinator.observeBoundary(
                new RunBoundarySignal.SpecialStageRequest(5, 0));
        coordinator.afterProduction(
                levelObservation(1, 0, 0, 1, true, 0));
        coordinator.beforeAdmission(specialObservation(5, 2, 0, 0));
        coordinator.observeBoundary(new RunBoundarySignal.StageExit(10));
        coordinator.afterProduction(new RunPlaybackObservation(
                GameMode.SPECIAL_STAGE, 11, 3, null, false, null, 0,
                false, true, 0, false, 1, 1));
        RunPlaybackObservation titleCard = new RunPlaybackObservation(
                GameMode.TITLE_CARD, 11, 4,
                new RunPlaybackObservation.LevelIdentity(2, 0, 0, 1),
                true, null, null, false, false, 0, false, 2, 2);

        AdmitDestination admission = assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(titleCard).getFirst());

        assertEquals(-1, admission.receipt().loadGeneration());
        assertEquals(
                TraceRunReplayWalker.SegmentExecutionPolicy
                        .LEVEL_PRESENTATION_BRIDGE,
                admission.receipt().executionPolicy());
        assertInstanceOf(
                DestinationAdmissionReceipt.LevelPresentationIdentity.class,
                admission.receipt().identity());
    }

    @Test
    void deathRestartRejectsReusedGenerationAndWrongCause() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("mz2", 2, 2, 100, 10),
                        level("mz2_restart", 2, 2, 120, 10)),
                List.of(transition(0, "death_restart", 115)), 160);
        coordinator.activateInitialLevel(levelObservation(44, 2, 1, 0, false, 0));

        RunBoundarySignal.LevelLoaded reused = new RunBoundarySignal.LevelLoaded(
                115, RunLevelLoadCause.DEATH_RESTART,
                new RunPlaybackObservation.LevelIdentity(44, 2, 2, 1));
        coordinator.beforeLoadedLevelActivation(
                reused, levelObservation(44, 2, 1, 1, false, 0));
        coordinator.afterProduction(levelObservation(44, 2, 1, 2, true, 0));
        assertTrue(coordinator.beforeAdmission(
                levelObservation(44, 2, 1, 2, false, 0)).isEmpty());

        RunBoundarySignal.LevelLoaded wrongCause = new RunBoundarySignal.LevelLoaded(
                115, RunLevelLoadCause.LEVEL_ADVANCE,
                new RunPlaybackObservation.LevelIdentity(45, 2, 2, 1));
        coordinator.beforeLoadedLevelActivation(
                wrongCause, levelObservation(45, 2, 1, 3, false, 0));
        assertTrue(coordinator.beforeAdmission(
                levelObservation(45, 2, 1, 3, false, 0)).isEmpty());
    }

    @Test
    void lagOnlySameLevelContinuationNeedsNoLoadGenerationChange() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("ghz2_lag", 0, 2, 100, 10),
                        level("ghz2", 0, 2, 120, 10)),
                List.of(), 160);
        coordinator.activateInitialLevel(levelObservation(9, 0, 1, 0, false, 0));

        coordinator.afterProduction(levelObservation(
                9, 0, 1, 1, true, 0, true));
        AdmitDestination admission = assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(levelObservation(
                        9, 0, 1, 1, false, 0, true)).getFirst());
        assertEquals(9, admission.receipt().loadGeneration());
    }

    @Test
    void lagOnlyContinuationCannotMaskANewLevelLoadGeneration() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("ghz2_lag", 0, 2, 100, 10),
                        level("ghz2", 0, 2, 120, 10)),
                List.of(), 160);
        coordinator.activateInitialLevel(levelObservation(9, 0, 1, 0, false, 0));
        FailRun failure = assertInstanceOf(FailRun.class,
                coordinator.afterProduction(levelObservation(
                        10, 0, 1, 1, true, 0, true)).getFirst());

        assertTrue(failure.diagnostic().contains("lost production ownership"));
        assertEquals(TraceRunPlaybackCoordinator.Phase.FAILED,
                coordinator.phase());
    }

    @Test
    void destinationReceiptAcceptsOnlyZeroOrOneConsumedRows() {
        TraceRunPlaybackCoordinator zero = levelPairCoordinator();
        prepareOrdinaryLevelDestination(zero, 0);
        assertEquals(0, assertInstanceOf(AdmitDestination.class,
                zero.beforeAdmission(levelObservation(2, 1, 0, 2, false, 0)).getFirst())
                .receipt().rowsConsumed());

        TraceRunPlaybackCoordinator one = levelPairCoordinator();
        prepareOrdinaryLevelDestination(one, 1);
        assertEquals(1, assertInstanceOf(AdmitDestination.class,
                one.beforeAdmission(levelObservation(2, 1, 0, 2, false, 1)).getFirst())
                .receipt().rowsConsumed());

        TraceRunPlaybackCoordinator drift = levelPairCoordinator();
        prepareOrdinaryLevelDestination(drift, 2);
        assertThrows(IllegalArgumentException.class,
                () -> drift.beforeAdmission(levelObservation(
                        2, 1, 0, 2, false, 2)));
    }

    @Test
    void frozenCursorStillFailsAtIndependentAdmittedStepCap() {
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("a", 0, 1, 0, 10),
                        bonus("b", 19, 1, 20, 10, "gumball")),
                List.of(transition(0, "starpost_bonus", 20)), 40);
        coordinator.activateInitialLevel(levelObservation(1, 0, 0, 100, false, 0));
        coordinator.afterProduction(levelObservation(1, 0, 0, 101, true, 0));
        int cap = coordinator.transitionStepCap();

        List<Action> actions = coordinator.afterStep(
                levelObservation(1, 0, 0, 101 + cap, false, 0));

        FailRun failure = assertInstanceOf(FailRun.class, actions.getFirst());
        assertTrue(failure.diagnostic().contains("starpost_bonus"));
        assertEquals(TraceRunPlaybackCoordinator.Phase.FAILED, coordinator.phase());
    }

    @Test
    void finalSegmentProducesWalkerTerminalPlan() {
        TraceRunManifest run = run(List.of(level("a", 0, 1, 10, 5)),
                List.of(), TraceRunManifest.ExpectedMovieEndMode.TITLE_SCREEN);
        TraceRunPlaybackCoordinator coordinator = new TraceRunPlaybackCoordinator(
                run, TracePlaybackProfile.DISABLED, 22);
        coordinator.activateInitialLevel(levelObservation(1, 0, 0, 0, false, 0));

        List<Action> actions = coordinator.afterProduction(
                levelObservation(1, 0, 0, 1, true, 0));

        assertInstanceOf(CloseSegment.class, actions.get(0));
        BeginTerminalTail tail = assertInstanceOf(BeginTerminalTail.class, actions.get(1));
        assertEquals(15, tail.plan().tailStart());
        assertEquals(7, tail.plan().rowsToReplay());
        assertEquals(GameMode.TITLE_SCREEN, tail.plan().expectedMode());
    }

    @Test
    void coordinatorActionsCannotCarryGameplayMutationCallbacksOrStateValues() {
        Set<Class<?>> allowedLeafTypes = Set.of(
                int.class, long.class, String.class,
                DestinationAdmissionReceipt.class,
                com.openggf.trace.replay.runs.TraceRunReplayWalker.TerminalMovieTailPlan.class);
        for (Class<?> actionType : Action.class.getPermittedSubclasses()) {
            assertTrue(actionType.isRecord(), actionType.getName());
            for (RecordComponent component : actionType.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertFalse(name.contains("rng") || name.contains("vblank")
                                || name.contains("position") || name.contains("ring")
                                || name.contains("emerald") || name.contains("gameplay"),
                        actionType.getSimpleName() + "." + component.getName());
                assertTrue(allowedLeafTypes.contains(component.getType()),
                        "unexpected action payload " + actionType.getSimpleName()
                                + "." + component.getName() + ": " + component.getType());
            }
        }
    }

    private static void assertLevelBoundaryCause(
            String entryKind, RunLevelLoadCause cause) {
        List<TraceRunManifest.Transition> transitions = entryKind == null
                ? List.of() : List.of(transition(0, entryKind, 115));
        TraceRunPlaybackCoordinator coordinator = coordinator(
                List.of(level("a", 0, 1, 100, 10),
                        level("b", 1, 1, 120, 10)), transitions, 160);
        coordinator.activateInitialLevel(levelObservation(1, 0, 0, 0, false, 0));
        coordinator.beforeLoadedLevelActivation(
                new RunBoundarySignal.LevelLoaded(115, cause,
                        new RunPlaybackObservation.LevelIdentity(2, 1, 1, 0)),
                levelObservation(2, 1, 0, 1, false, 0));
        coordinator.afterProduction(levelObservation(1, 0, 0, 2, true, 0));

        assertInstanceOf(AdmitDestination.class,
                coordinator.beforeAdmission(
                        levelObservation(2, 1, 0, 2, false, 0)).getFirst());
    }

    private static TraceRunPlaybackCoordinator levelPairCoordinator() {
        return coordinator(List.of(level("a", 0, 1, 0, 10),
                level("b", 1, 1, 20, 10)), List.of(), 50);
    }

    private static void prepareOrdinaryLevelDestination(
            TraceRunPlaybackCoordinator coordinator, int rowsConsumed) {
        coordinator.activateInitialLevel(levelObservation(1, 0, 0, 0, false, 0));
        coordinator.beforeLoadedLevelActivation(
                new RunBoundarySignal.LevelLoaded(15, RunLevelLoadCause.ORDINARY,
                        new RunPlaybackObservation.LevelIdentity(2, 1, 1, 0)),
                levelObservation(2, 1, 0, 1, false, rowsConsumed));
        coordinator.afterProduction(levelObservation(1, 0, 0, 2, true, rowsConsumed));
    }

    private static TraceRunPlaybackCoordinator coordinator(
            List<TraceRunManifest.Segment> segments,
            List<TraceRunManifest.Transition> transitions,
            int movieFrameCount) {
        return new TraceRunPlaybackCoordinator(
                run(segments, transitions,
                        TraceRunManifest.ExpectedMovieEndMode.UNSPECIFIED),
                TracePlaybackProfile.DISABLED, movieFrameCount);
    }

    private static TraceRunManifest run(
            List<TraceRunManifest.Segment> segments,
            List<TraceRunManifest.Transition> transitions,
            TraceRunManifest.ExpectedMovieEndMode endMode) {
        return new TraceRunManifest("test", "run", "movie.bk2",
                "checksum", segments, transitions, endMode);
    }

    private static TraceRunManifest.Segment level(
            String dir, int zone, int act, int offset, int frames) {
        return new TraceRunManifest.Segment(dir, "level", "complete_run",
                offset, frames, zone, act, null, null);
    }

    private static TraceRunManifest.Segment bonus(
            String dir, int zone, int act, int offset, int frames, String type) {
        return new TraceRunManifest.Segment(dir, "bonus_stage", "s3k_bonus_stage",
                offset, frames, zone, act, null, type);
    }

    private static TraceRunManifest.Segment special(
            String dir, int offset, int frames, int stage) {
        return new TraceRunManifest.Segment(dir, "special_stage", "s2_special_stage",
                offset, frames, 0, 0, stage, null);
    }

    private static TraceRunManifest.Transition transition(
            int from, String kind, int frame) {
        return new TraceRunManifest.Transition(from, from + 1, kind, frame,
                null, null, null, null, null, null, null, null);
    }

    private static TraceData executionTrace(int... gameplayCounters) {
        List<TraceFrame> frames = new ArrayList<>();
        for (int index = 0; index < gameplayCounters.length; index++) {
            frames.add(TraceFrame.executionTestFrame(
                    index, 0x300 + index, gameplayCounters[index], 0));
        }
        return TraceFixtures.trace(
                TraceFixtures.metadata("s1", 0, 1), frames);
    }

    private static RunPlaybackObservation levelObservation(
            long generation, int romZone, int act, long step,
            boolean exhausted, int rowsConsumed) {
        return levelObservation(generation, romZone, act, step,
                exhausted, rowsConsumed, false);
    }

    private static RunPlaybackObservation levelObservation(
            long generation, int romZone, int act, long step,
            boolean exhausted, int rowsConsumed, boolean lagOnly) {
        return levelObservation(generation, romZone, act, step,
                exhausted, rowsConsumed, lagOnly, false);
    }

    private static RunPlaybackObservation levelObservation(
            long generation, int romZone, int act, long step,
            boolean exhausted, int rowsConsumed, boolean lagOnly,
            boolean initialTitleCardPending) {
        return new RunPlaybackObservation(GameMode.LEVEL, 0, step,
                new RunPlaybackObservation.LevelIdentity(
                        generation, romZone, romZone, act),
                initialTitleCardPending, null, null, false,
                exhausted, rowsConsumed,
                lagOnly, 10, 20);
    }

    private static RunPlaybackObservation bonusObservation(
            int cursor, long step, BonusStageType type, int rowsConsumed) {
        return new RunPlaybackObservation(GameMode.BONUS_STAGE, cursor, step,
                null, false,
                new RunPlaybackObservation.BonusIdentity(19, 0, type),
                null, false, false, rowsConsumed, false, 11, 21);
    }

    private static RunPlaybackObservation specialObservation(
            int cursor, long step, int stage, int rowsConsumed) {
        return new RunPlaybackObservation(GameMode.SPECIAL_STAGE, cursor, step,
                null, false, null, stage, false, false, rowsConsumed,
                false, 12, 22);
    }
}
