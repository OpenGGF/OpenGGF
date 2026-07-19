package com.openggf.trace.replay.runs;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceRunManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Chained-driver core for multi-stage trace runs (spec: docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md).
 * Plans a {@link TraceRunManifest} into per-segment {@link SegmentPlan}s and drives a boundary-observing
 * {@link BoundaryProbe} across a transition. Serves both the headless chain test and the visual run session.
 *
 * Comparison-only: consumes trace/manifest data and engine-observation hooks as read-only diagnostic input; never feeds engine state.
 */
public final class TraceRunReplayWalker {

    /**
     * Tolerance window (in BK2 frames) a latched boundary observation must
     * fall within, measured backward from the manifest's recorded edge
     * ({@code mode_change_bk2_frame}). An observation strictly after the
     * recorded edge is never within the window.
     */
    public static final int BOUNDARY_WINDOW_FRAMES = 600;

    private TraceRunReplayWalker() {
    }

    /**
     * A planned segment: its manifest {@link TraceRunManifest.Segment}, its
     * loaded {@link TraceData}, and the {@link TraceRunManifest.Transition}
     * records bounding it (null when the segment starts/ends the run or
     * abuts a plain level-to-level boundary with no transition record).
     */
    public record SegmentPlan(
        TraceRunManifest.Segment segment,
        TraceData trace,
        TraceRunManifest.Transition entryBoundary,
        TraceRunManifest.Transition exitBoundary
    ) {}

    /** Result of {@link #awaitBoundary}: whether the boundary was observed, and at what BK2 frame. */
    public record BoundaryObservation(boolean observed, int observedBk2Frame) {
        static final BoundaryObservation NOT_OBSERVED = new BoundaryObservation(false, -1);
    }

    /**
     * Engine-observation surface read INSIDE {@link BoundaryProbe#afterFrameAdvanced}
     * (for transient boundary kinds) or lazily from {@link BoundaryProbe#latched()}
     * (for the persistent {@code stage_exit} kind). See the TRANSIENT-PEEK REALITY
     * note on {@link BoundaryProbe}: the underlying coordinator peeks are only
     * non-null during the frame observer callback, never on post-step polling.
     */
    public interface EngineHooks {
        int currentBk2Frame();

        BonusStageType peekBonusRequest();

        boolean isSpecialStageRequested();

        GameMode currentMode();
    }

    /**
     * Validates the manifest, loads each segment's {@link TraceData}, and
     * pairs transitions to segments by their explicit {@code from_segment}/
     * {@code to_segment} indices — never by list position. A segment index
     * named by no transition keeps a null boundary on that side (plain
     * level-to-level boundaries carry no transition record).
     */
    public static List<SegmentPlan> plan(TraceRunManifest run, Path runDir) throws IOException {
        run.validate(runDir);
        List<TraceRunManifest.Segment> segments = run.segments();
        int segmentCount = segments.size();

        TraceData[] traces = new TraceData[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            traces[i] = TraceData.load(runDir.resolve(segments.get(i).dir()));
        }

        TraceRunManifest.Transition[] entryBoundaries = new TraceRunManifest.Transition[segmentCount];
        TraceRunManifest.Transition[] exitBoundaries = new TraceRunManifest.Transition[segmentCount];
        if (run.transitions() != null) {
            for (TraceRunManifest.Transition transition : run.transitions()) {
                exitBoundaries[transition.fromSegment()] = transition;
                entryBoundaries[transition.toSegment()] = transition;
            }
        }

        List<SegmentPlan> plans = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            plans.add(new SegmentPlan(segments.get(i), traces[i], entryBoundaries[i], exitBoundaries[i]));
        }
        return plans;
    }

    /**
     * True when {@code observedBk2Frame} falls within {@link #BOUNDARY_WINDOW_FRAMES}
     * frames before (inclusive of) {@code recordedEdge}, and never after it.
     */
    public static boolean withinBoundaryWindow(int observedBk2Frame, int recordedEdge) {
        return observedBk2Frame <= recordedEdge
            && observedBk2Frame >= recordedEdge - BOUNDARY_WINDOW_FRAMES;
    }

    /**
     * The single {@link PlaybackDebugManager.PlaybackFrameObserver} the chain
     * installs. Delegates BOTH interface methods to an attached delegate
     * (typically a {@link com.openggf.trace.live.LiveTraceComparator} at
     * integration time, or a lightweight stub in unit tests — the delegate
     * type is exactly {@link PlaybackDebugManager.PlaybackFrameObserver},
     * which already exposes only the two methods that need forwarding):
     *
     * <ul>
     *   <li>{@link #shouldSkipGameplayTick} forwards to the delegate's ROM-lag
     *       gating result, or {@code false} when detached (no delegate
     *       attached — e.g. mid-transition).</li>
     *   <li>{@link #afterFrameAdvanced} forwards to the delegate first, then,
     *       when armed with a transient-kind {@link TraceRunManifest.Transition}
     *       ({@code starpost_bonus}, {@code giant_ring}, {@code starpost_special}),
     *       evaluates the boundary predicate DURING the callback — the only
     *       point the underlying request peek is visible — and latches a
     *       {@link BoundaryObservation} on first match within the window.</li>
     * </ul>
     *
     * <p><b>TRANSIENT-PEEK REALITY:</b> the entry request is raised during the
     * gameplay tick and consumed later in the same {@code loop.step()}; by the
     * time {@code step()} returns, the peek is already null. Post-step polling
     * can never observe an entry — only the observer callback can.
     *
     * <p>{@code stage_exit} is PERSISTENT ({@code currentMode() == GameMode.LEVEL})
     * and is instead evaluated lazily, live from the injected {@link EngineHooks},
     * whenever {@link #latched()} is queried — no observer callback fires during
     * a frozen return transition, so {@link #awaitBoundary} (which holds only the
     * probe) observes it by polling {@link #latched()} after each step.
     */
    public static final class BoundaryProbe implements PlaybackDebugManager.PlaybackFrameObserver {
        private final EngineHooks hooks;
        private PlaybackDebugManager.PlaybackFrameObserver delegate;
        private TraceRunManifest.Transition armed;
        private BoundaryObservation latchedObservation;

        public BoundaryProbe(EngineHooks hooks) {
            this.hooks = hooks;
        }

        /** Attaches (or, with {@code null}, detaches) the delegate observer. */
        public void setDelegate(PlaybackDebugManager.PlaybackFrameObserver delegate) {
            this.delegate = delegate;
        }

        /** Arms the probe for a new boundary, clearing any prior latch. */
        public void arm(TraceRunManifest.Transition boundary) {
            this.armed = boundary;
            this.latchedObservation = null;
        }

        /**
         * True once a boundary observation has latched. For the persistent
         * {@code stage_exit} kind this lazily evaluates {@link EngineHooks#currentMode()}
         * on first call after the condition becomes true, caching the frame
         * at which it was first noticed so repeated queries stay stable.
         */
        public boolean latched() {
            if (latchedObservation == null && armed != null
                && "stage_exit".equals(armed.entryKind())
                && hooks.currentMode() == GameMode.LEVEL) {
                latchedObservation = new BoundaryObservation(true, hooks.currentBk2Frame());
            }
            return latchedObservation != null;
        }

        /** The latched observation, or a not-observed sentinel if {@link #latched()} is false. */
        public BoundaryObservation observation() {
            return latchedObservation != null ? latchedObservation : BoundaryObservation.NOT_OBSERVED;
        }

        /** For {@link #awaitBoundary}'s fail-closed edge check. */
        int currentBk2Frame() {
            return hooks.currentBk2Frame();
        }

        @Override
        public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
            return delegate != null && delegate.shouldSkipGameplayTick(frame);
        }

        @Override
        public void afterFrameAdvanced(Bk2FrameInput frame, boolean wasSkipped) {
            if (delegate != null) {
                delegate.afterFrameAdvanced(frame, wasSkipped);
            }
            if (armed == null || latchedObservation != null) {
                return;
            }
            boolean transientHit = switch (armed.entryKind()) {
                case "starpost_bonus" -> hooks.peekBonusRequest() != null;
                case "giant_ring", "starpost_special" -> hooks.isSpecialStageRequested();
                default -> false; // stage_exit is persistent; evaluated in latched().
            };
            if (transientHit) {
                int observedFrame = hooks.currentBk2Frame();
                if (withinBoundaryWindow(observedFrame, armed.modeChangeBk2Frame())) {
                    latchedObservation = new BoundaryObservation(true, observedFrame);
                }
            }
        }
    }

    /**
     * Arms {@code probe} for {@code boundary}, then repeatedly invokes
     * {@code stepOneFrame} until the probe latches an observation or the
     * cursor passes {@code boundary.modeChangeBk2Frame()}. Fails closed
     * (returns a not-observed result) rather than throwing or looping
     * forever.
     */
    public static BoundaryObservation awaitBoundary(
            BoundaryProbe probe, TraceRunManifest.Transition boundary, Runnable stepOneFrame) {
        probe.arm(boundary);
        while (true) {
            stepOneFrame.run();
            if (probe.latched()) {
                return probe.observation();
            }
            if (probe.currentBk2Frame() > boundary.modeChangeBk2Frame()) {
                return BoundaryObservation.NOT_OBSERVED;
            }
        }
    }
}
