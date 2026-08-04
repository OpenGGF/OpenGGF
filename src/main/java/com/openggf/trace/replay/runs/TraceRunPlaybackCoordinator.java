package com.openggf.trace.replay.runs;

import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.profiles.trace.TracePlaybackProfile;
import com.openggf.trace.TraceRunManifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Engine-agnostic structural policy for visual and headless whole-run replay.
 * It decides only when comparison/input owners may move between manifest
 * segments. Adapters remain responsible for performing the represented work.
 */
public final class TraceRunPlaybackCoordinator {

    public enum Phase {
        DESTINATION_READY,
        CURRENT_SEGMENT,
        TRANSITION_GAP,
        TERMINAL_TAIL,
        COMPLETE,
        FAILED
    }

    public sealed interface Action permits
            AdmitDestination,
            CloseSegment,
            EnterTransitionGap,
            BeginTerminalTail,
            CompleteRun,
            FailRun {
    }

    public sealed interface ImmediateAdmissionAction
            permits AdmitDestination {
    }

    public sealed interface PublishedIterationAction permits
            CloseSegment,
            EnterTransitionGap,
            BeginTerminalTail,
            CompleteRun {
    }

    public record AdmitDestination(DestinationAdmissionReceipt receipt)
            implements Action, ImmediateAdmissionAction {
        public AdmitDestination {
            receipt = Objects.requireNonNull(receipt, "receipt");
        }
    }

    public record CloseSegment(int segmentIndex)
            implements Action, PublishedIterationAction {
    }

    public record EnterTransitionGap(int sourceSegmentIndex, int destinationSegmentIndex)
            implements Action, PublishedIterationAction {
    }

    public record BeginTerminalTail(
            TraceRunReplayWalker.TerminalMovieTailPlan plan)
            implements Action, PublishedIterationAction {
        public BeginTerminalTail {
            plan = Objects.requireNonNull(plan, "plan");
        }
    }

    public record CompleteRun(int segmentIndex)
            implements Action, PublishedIterationAction {
    }

    public record FailRun(String diagnostic) implements Action {
        public FailRun {
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    private final TraceRunManifest run;
    private final TracePlaybackProfile profile;
    private final int movieFrameCount;
    private final TraceRunReplayWalker.BoundaryPairing boundaries;
    private final List<TraceRunReplayWalker.SegmentExecutionPolicy>
            executionPolicies;
    private final int transitionStepCap;

    private Phase phase = Phase.DESTINATION_READY;
    private int currentSegmentIndex = -1;
    private long currentLevelGeneration = -1;
    private long transitionStartStepOrdinal = -1;
    private RunBoundarySignal observedBoundary;
    private RunBoundarySignal.LevelLoaded rememberedLevelLoad;

    public TraceRunPlaybackCoordinator(
            TraceRunManifest run,
            TracePlaybackProfile profile,
            int movieFrameCount) {
        this(run, profile, movieFrameCount, null);
    }

    public TraceRunPlaybackCoordinator(
            TraceRunManifest run,
            TracePlaybackProfile profile,
            int movieFrameCount,
            List<TraceRunReplayWalker.SegmentPlan> plans) {
        this.run = Objects.requireNonNull(run, "run");
        this.profile = Objects.requireNonNull(profile, "profile");
        if (run.segments() == null || run.segments().isEmpty()) {
            throw new IllegalArgumentException("run must contain a segment");
        }
        if (!"level".equals(run.segments().getFirst().kind())) {
            throw new IllegalArgumentException(
                    "visual whole-run playback requires an initial level segment");
        }
        if (movieFrameCount < 0) {
            throw new IllegalArgumentException("movieFrameCount must be non-negative");
        }
        this.movieFrameCount = movieFrameCount;
        this.boundaries = TraceRunReplayWalker.pairBoundaries(run);
        if (plans != null) {
            if (plans.size() != run.segments().size()) {
                throw new IllegalArgumentException(
                        "segment plans must match manifest segment count");
            }
            this.executionPolicies = plans.stream()
                    .map(TraceRunReplayWalker.SegmentPlan::executionPolicy)
                    .toList();
        } else {
            this.executionPolicies = run.segments().stream()
                    .map(segment -> "special_stage".equals(segment.kind())
                            ? TraceRunReplayWalker.SegmentExecutionPolicy.SPECIAL_LOCAL
                            : TraceRunReplayWalker.SegmentExecutionPolicy.GAMEPLAY)
                    .toList();
        }
        this.transitionStepCap = TraceRunReplayWalker.interSegmentStepCap(run);
    }

    public Phase phase() {
        return phase;
    }

    public int currentSegmentIndex() {
        return currentSegmentIndex;
    }

    public int transitionStepCap() {
        return transitionStepCap;
    }

    /** True only for the exact production load retained for destination admission. */
    public boolean remembersLevelLoad(RunBoundarySignal.LevelLoaded signal) {
        return rememberedLevelLoad != null
                && rememberedLevelLoad.equals(Objects.requireNonNull(signal, "signal"));
    }

    public List<Action> activateInitialLevel(RunPlaybackObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (currentSegmentIndex >= 0 || phase != Phase.DESTINATION_READY) {
            throw new IllegalStateException("initial segment is already active");
        }
        TraceRunManifest.Segment initial = run.segments().getFirst();
        if (observation.mode() != GameMode.LEVEL
                || !matchesLevel(initial, observation.level())) {
            throw new IllegalArgumentException(
                    "initial level identity does not match segment 0");
        }
        currentSegmentIndex = 0;
        currentLevelGeneration = observation.level().loadGeneration();
        phase = Phase.CURRENT_SEGMENT;
        return List.of(admissionFor(0, observation));
    }

    /** Records a semantic engine-created boundary without causing a transition. */
    public void observeBoundary(RunBoundarySignal signal) {
        Objects.requireNonNull(signal, "signal");
        if (currentSegmentIndex < 0
                || currentSegmentIndex >= run.segments().size() - 1
                || phase == Phase.COMPLETE || phase == Phase.FAILED) {
            return;
        }
        TraceRunManifest.Transition expected =
                boundaries.exitBoundaries()[currentSegmentIndex];
        if (matchesBoundary(expected, signal, nextSegment())) {
            observedBoundary = signal;
        }
    }

    /**
     * Observes a production level load. A target reached inside the source tail
     * is remembered but cannot seek or attach destination owners yet.
     */
    public List<Action> beforeLoadedLevelActivation(
            RunBoundarySignal.LevelLoaded signal,
            RunPlaybackObservation observation) {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(observation, "observation");
        if (currentSegmentIndex < 0
                || currentSegmentIndex >= run.segments().size() - 1
                || !"level".equals(nextSegment().kind())) {
            return List.of();
        }
        TraceRunManifest.Transition expected =
                boundaries.exitBoundaries()[currentSegmentIndex];
        if (!matchesLevel(nextSegment(), signal.identity())
                || signal.identity().loadGeneration() == currentLevelGeneration
                || !matchesLoadCause(expected, signal.cause())) {
            return List.of();
        }
        observeBoundary(signal);
        if (expected != null && "stage_exit".equals(expected.entryKind())
                && !(observedBoundary instanceof RunBoundarySignal.StageExit)) {
            return List.of();
        }
        rememberedLevelLoad = signal;
        if (phase == Phase.TRANSITION_GAP) {
            return beforeAdmission(observation);
        }
        return List.of();
    }

    /** Returns a destination action only after signal and identity both match. */
    public List<Action> beforeAdmission(RunPlaybackObservation observation) {
        Objects.requireNonNull(observation, "observation");
        requireRowsConsumed(observation.destinationRowsConsumed());
        if (phase != Phase.TRANSITION_GAP
                || currentSegmentIndex >= run.segments().size() - 1) {
            return List.of();
        }
        int destinationIndex = currentSegmentIndex + 1;
        TraceRunManifest.Segment destination = run.segments().get(destinationIndex);
        if (!destinationReady(destination, observation)) {
            return List.of();
        }
        AdmitDestination action = admissionFor(destinationIndex, observation);
        currentSegmentIndex = destinationIndex;
        if (observation.level() != null) {
            currentLevelGeneration = observation.level().loadGeneration();
        }
        observedBoundary = null;
        rememberedLevelLoad = null;
        transitionStartStepOrdinal = -1;
        phase = Phase.CURRENT_SEGMENT;
        return List.of(action);
    }

    /** Closes a represented segment only after its final publication. */
    public List<Action> afterProduction(RunPlaybackObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (phase != Phase.CURRENT_SEGMENT
                || !observation.currentSegmentExhausted()) {
            return List.of();
        }
        if (!ownsCurrentSegment(observation)) {
            return failSourceOwnership(observation);
        }
        List<Action> actions = new ArrayList<>();
        actions.add(new CloseSegment(currentSegmentIndex));
        if (currentSegmentIndex == run.segments().size() - 1) {
            TraceRunManifest.Segment finalSegment = run.segments().getLast();
            int tailStart = Math.addExact(finalSegment.bk2FrameOffset(),
                    finalSegment.traceFrameCount());
            TraceRunReplayWalker.TerminalMovieTailPlan tail =
                    TraceRunReplayWalker.planTerminalMovieTail(
                            run.expectedMovieEndMode(), tailStart, movieFrameCount);
            if (tail.shouldAssertExpectedMode()) {
                phase = Phase.TERMINAL_TAIL;
                actions.add(new BeginTerminalTail(tail));
            } else {
                phase = Phase.COMPLETE;
                actions.add(new CompleteRun(currentSegmentIndex));
            }
            return List.copyOf(actions);
        }
        phase = Phase.TRANSITION_GAP;
        transitionStartStepOrdinal = observation.admittedStepOrdinal();
        actions.add(new EnterTransitionGap(
                currentSegmentIndex, currentSegmentIndex + 1));
        return List.copyOf(actions);
    }

    /** Applies the independent admitted-step timeout while playback is frozen. */
    public List<Action> afterStep(RunPlaybackObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (phase == Phase.CURRENT_SEGMENT
                && !ownsCurrentSegment(observation)) {
            return failSourceOwnership(observation);
        }
        if (phase != Phase.TRANSITION_GAP || transitionStartStepOrdinal < 0) {
            return List.of();
        }
        long elapsed = observation.admittedStepOrdinal() - transitionStartStepOrdinal;
        if (elapsed < transitionStepCap) {
            return List.of();
        }
        phase = Phase.FAILED;
        TraceRunManifest.Transition expected =
                boundaries.exitBoundaries()[currentSegmentIndex];
        String boundaryName = expected == null
                ? "adjacent_level" : expected.entryKind();
        return List.of(new FailRun(
                "transition step cap " + transitionStepCap
                        + " exceeded for " + boundaryName
                        + " from segment " + currentSegmentIndex
                        + " at BK2 cursor " + observation.sharedBk2Cursor()));
    }

    public List<Action> finishTerminalTail(GameMode actualMode) {
        Objects.requireNonNull(actualMode, "actualMode");
        if (phase != Phase.TERMINAL_TAIL) {
            return List.of();
        }
        TraceRunManifest.Segment finalSegment = run.segments().getLast();
        int tailStart = Math.addExact(finalSegment.bk2FrameOffset(),
                finalSegment.traceFrameCount());
        TraceRunReplayWalker.TerminalMovieTailPlan tail =
                TraceRunReplayWalker.planTerminalMovieTail(
                        run.expectedMovieEndMode(), tailStart, movieFrameCount);
        if (tail.expectedMode() != actualMode) {
            phase = Phase.FAILED;
            return List.of(new FailRun("terminal movie mode expected "
                    + tail.expectedMode() + " but was " + actualMode));
        }
        phase = Phase.COMPLETE;
        return List.of(new CompleteRun(currentSegmentIndex));
    }

    public List<Action> abort(String diagnostic) {
        if (phase == Phase.COMPLETE || phase == Phase.FAILED) {
            return List.of();
        }
        phase = Phase.FAILED;
        return List.of(new FailRun(Objects.requireNonNull(diagnostic, "diagnostic")));
    }

    private boolean destinationReady(
            TraceRunManifest.Segment destination,
            RunPlaybackObservation observation) {
        TraceRunManifest.Transition expected =
                boundaries.exitBoundaries()[currentSegmentIndex];
        if ("level".equals(destination.kind())) {
            if (policy(destinationIndex())
                    == TraceRunReplayWalker.SegmentExecutionPolicy
                            .LEVEL_PRESENTATION_BRIDGE) {
                // The recorded bridge can begin while the native special-stage
                // exit fade still owns SPECIAL_STAGE. Source publication has
                // already closed to enter TRANSITION_GAP; exact physical-row
                // and destination level identity are the admission authority.
                return observation.sharedBk2Cursor()
                                == destination.bk2FrameOffset()
                        && matchesLevel(destination, observation.level());
            }
            if (observation.mode() != GameMode.LEVEL
                    || observation.initialTitleCardPending()) {
                return false;
            }
            if (observation.lagOnlySameLevelContinuation()
                    && sameRecordedLevel(currentSegment(), destination)
                    && matchesLevel(destination, observation.level())
                    && observation.level().loadGeneration()
                            == currentLevelGeneration) {
                return true;
            }
            return rememberedLevelLoad != null
                    && rememberedLevelLoad.identity().equals(observation.level())
                    && (expected == null || observedBoundary != null);
        }
        if (expected == null || observedBoundary == null) {
            return false;
        }
        if ("bonus_stage".equals(destination.kind())) {
            return matchesBonus(destination, observation);
        }
        if ("special_stage".equals(destination.kind())) {
            return observation.mode() == GameMode.SPECIAL_STAGE
                    && Objects.equals(destination.specialStageIndex(),
                            observation.specialStageIndex());
        }
        return false;
    }

    private boolean ownsCurrentSegment(RunPlaybackObservation observation) {
        TraceRunManifest.Segment segment = currentSegment();
        if (policy(currentSegmentIndex)
                == TraceRunReplayWalker.SegmentExecutionPolicy
                        .LEVEL_PRESENTATION_BRIDGE) {
            return matchesLevel(segment, observation.level())
                    && observation.sharedBk2Cursor()
                            >= segment.bk2FrameOffset()
                    && observation.sharedBk2Cursor()
                            <= Math.addExact(segment.bk2FrameOffset(),
                                    segment.traceFrameCount());
        }
        return switch (segment.kind()) {
            case "level" -> observation.mode() == GameMode.LEVEL
                    && matchesLevel(segment, observation.level())
                    && observation.level().loadGeneration()
                            == currentLevelGeneration;
            case "bonus_stage" -> matchesBonus(segment, observation);
            case "special_stage" -> observation.mode() == GameMode.SPECIAL_STAGE
                    && Objects.equals(segment.specialStageIndex(),
                            observation.specialStageIndex());
            default -> false;
        };
    }

    private List<Action> failSourceOwnership(
            RunPlaybackObservation observation) {
        phase = Phase.FAILED;
        String identity = observation.level() != null
                ? ", level=" + observation.level()
                : observation.bonus() != null
                        ? ", bonus=" + observation.bonus()
                        : observation.specialStageIndex() != null
                                ? ", specialStage=" + observation.specialStageIndex()
                                : "";
        return List.of(new FailRun(
                "segment " + currentSegmentIndex
                        + " lost production ownership before source closure"
                        + " (mode=" + observation.mode() + identity
                        + ", BK2 cursor=" + observation.sharedBk2Cursor() + ")"));
    }

    private AdmitDestination admissionFor(
            int segmentIndex, RunPlaybackObservation observation) {
        requireRowsConsumed(observation.destinationRowsConsumed());
        TraceRunManifest.Segment segment = run.segments().get(segmentIndex);
        int rowsConsumed = observation.destinationRowsConsumed();
        DestinationAdmissionReceipt.InputClock clock =
                "special_stage".equals(segment.kind())
                        ? DestinationAdmissionReceipt.InputClock.SPECIAL_LOCAL
                        : DestinationAdmissionReceipt.InputClock.SHARED;
        DestinationAdmissionReceipt.DestinationIdentity identity;
        long loadGeneration = -1;
        if ("level".equals(segment.kind())) {
            RunPlaybackObservation.LevelIdentity level =
                    Objects.requireNonNull(observation.level(), "level identity");
            if (policy(segmentIndex)
                    == TraceRunReplayWalker.SegmentExecutionPolicy
                            .LEVEL_PRESENTATION_BRIDGE) {
                identity = new DestinationAdmissionReceipt.LevelPresentationIdentity(
                        level.progressionZone(), level.romZone(), level.act());
            } else {
                identity = new DestinationAdmissionReceipt.LevelIdentity(
                        level.progressionZone(), level.romZone(), level.act());
                loadGeneration = level.loadGeneration();
            }
        } else if ("bonus_stage".equals(segment.kind())) {
            RunPlaybackObservation.BonusIdentity bonus =
                    Objects.requireNonNull(observation.bonus(), "bonus identity");
            identity = new DestinationAdmissionReceipt.BonusIdentity(
                    bonus.romZone(), bonus.act(), bonus.type());
        } else {
            identity = new DestinationAdmissionReceipt.SpecialStageIdentity(
                    Objects.requireNonNull(
                            observation.specialStageIndex(), "specialStageIndex"));
        }
        return new AdmitDestination(new DestinationAdmissionReceipt(
                segmentIndex, clock,
                Math.addExact(segment.bk2FrameOffset(), rowsConsumed),
                rowsConsumed, identity, loadGeneration,
                observation.timingScheduleGeneration(),
                observation.dynamicArtGeneration(), policy(segmentIndex)));
    }

    private int destinationIndex() {
        return currentSegmentIndex + 1;
    }

    private TraceRunReplayWalker.SegmentExecutionPolicy policy(int segmentIndex) {
        return executionPolicies.get(segmentIndex);
    }

    private boolean matchesLevel(
            TraceRunManifest.Segment segment,
            RunPlaybackObservation.LevelIdentity identity) {
        if (identity == null || !"level".equals(segment.kind())
                || segment.zoneId() == null || segment.act() == null) {
            return false;
        }
        TracePlaybackProfile.LevelIdentity expected =
                profile.resolveRecordedLevel(segment.zoneId(), segment.act());
        return identity.progressionZone() == expected.zone()
                && identity.romZone() == segment.zoneId()
                && identity.act() == expected.act();
    }

    private static boolean matchesBonus(
            TraceRunManifest.Segment segment,
            RunPlaybackObservation observation) {
        RunPlaybackObservation.BonusIdentity identity = observation.bonus();
        return observation.mode() == GameMode.BONUS_STAGE
                && identity != null
                && segment.zoneId() != null
                && segment.act() != null
                && identity.romZone() == segment.zoneId()
                && identity.act() == segment.act() - 1
                && identity.type() == bonusType(segment.bonusStageType());
    }

    private static boolean matchesBoundary(
            TraceRunManifest.Transition expected,
            RunBoundarySignal signal,
            TraceRunManifest.Segment destination) {
        if (expected == null) {
            return signal instanceof RunBoundarySignal.LevelLoaded loaded
                    && isTransitionlessLevelLoad(loaded.cause());
        }
        if (!TraceRunReplayWalker.withinBoundaryWindow(
                signal.physicalBk2Frame(), expected.modeChangeBk2Frame())) {
            return false;
        }
        return switch (expected.entryKind()) {
            case "starpost_bonus" -> signal instanceof RunBoundarySignal.BonusRequest bonus
                    && bonus.type() == bonusType(destination.bonusStageType());
            case "giant_ring", "starpost_special" ->
                    signal instanceof RunBoundarySignal.SpecialStageRequest special
                            && Objects.equals(destination.specialStageIndex(),
                                    special.stageIndex());
            case "stage_exit" -> signal instanceof RunBoundarySignal.StageExit;
            case "level_advance" -> signal instanceof RunBoundarySignal.LevelLoaded loaded
                    && loaded.cause() == RunLevelLoadCause.LEVEL_ADVANCE;
            case "death_restart" -> signal instanceof RunBoundarySignal.LevelLoaded loaded
                    && loaded.cause() == RunLevelLoadCause.DEATH_RESTART;
            default -> false;
        };
    }

    private static boolean matchesLoadCause(
            TraceRunManifest.Transition expected,
            RunLevelLoadCause actual) {
        if (expected == null) {
            return isTransitionlessLevelLoad(actual);
        }
        return switch (expected.entryKind()) {
            case "stage_exit" -> actual == RunLevelLoadCause.INTERIOR_RETURN;
            case "level_advance" -> actual == RunLevelLoadCause.LEVEL_ADVANCE;
            case "death_restart" -> actual == RunLevelLoadCause.DEATH_RESTART;
            default -> false;
        };
    }

    private static boolean isTransitionlessLevelLoad(
            RunLevelLoadCause cause) {
        return cause == RunLevelLoadCause.ORDINARY
                || cause == RunLevelLoadCause.LEVEL_ADVANCE;
    }

    private static BonusStageType bonusType(String token) {
        return switch (token) {
            case "gumball" -> BonusStageType.GUMBALL;
            case "pachinko" -> BonusStageType.GLOWING_SPHERE;
            case "slots" -> BonusStageType.SLOT_MACHINE;
            default -> throw new IllegalArgumentException(
                    "unknown bonus_stage_type '" + token + "'");
        };
    }

    private TraceRunManifest.Segment currentSegment() {
        return run.segments().get(currentSegmentIndex);
    }

    private TraceRunManifest.Segment nextSegment() {
        return run.segments().get(currentSegmentIndex + 1);
    }

    private static boolean sameRecordedLevel(
            TraceRunManifest.Segment first,
            TraceRunManifest.Segment second) {
        return "level".equals(first.kind()) && "level".equals(second.kind())
                && Objects.equals(first.zoneId(), second.zoneId())
                && Objects.equals(first.act(), second.act());
    }

    private static void requireRowsConsumed(int rowsConsumed) {
        if (rowsConsumed < 0 || rowsConsumed > 1) {
            throw new IllegalArgumentException("rowsConsumed must be 0 or 1");
        }
    }
}
