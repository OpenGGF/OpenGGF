package com.openggf.trace.replay.runs;

import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.replay.runs.TraceRunReplayWalker.SegmentExecutionPolicy;

import java.util.Objects;

/**
 * Shared physical-row sequencer for visual and headless whole-run replay.
 * The driver owns ordering only; adapters supply production and diagnostic
 * operations without giving recorded comparison values to gameplay owners.
 */
public final class TraceRunFrameDriver {
    private Step currentStep;

    public enum Disposition {
        GAMEPLAY_SHARED(true),
        SPECIAL_LOCAL(true),
        PRESENTATION_VBLANK(true),
        PRESENTATION_SUPPRESSED_CLOSURE(true),
        PRESENTATION_ADVANCE_ONLY(false),
        SHARED_GAP(true),
        OFFSET_HANDOFF(false),
        TERMINAL_TAIL(true);

        private final boolean productionLifecycle;

        Disposition(boolean productionLifecycle) {
            this.productionLifecycle = productionLifecycle;
        }

        public boolean runsProductionLifecycle() {
            return productionLifecycle;
        }
    }

    public record Step(
            Disposition disposition,
            int movieRow,
            boolean terminalSegmentRow,
            boolean deferBoundaryCommitAfterRow,
            boolean commitDeferredBoundaryAfterClosure,
            boolean observedVblankCounterAdvance) {
        public Step(
                Disposition disposition,
                int movieRow,
                boolean terminalSegmentRow) {
            this(disposition, movieRow, terminalSegmentRow,
                    false, false, true);
        }

        public Step(
                Disposition disposition,
                int movieRow,
                boolean terminalSegmentRow,
                boolean deferBoundaryCommitAfterRow) {
            this(disposition, movieRow, terminalSegmentRow,
                    deferBoundaryCommitAfterRow, false, true);
        }

        public Step {
            Objects.requireNonNull(disposition, "disposition");
            if (movieRow < 0) {
                throw new IllegalArgumentException("movieRow must be non-negative");
            }
        }
    }

    public interface Hooks<S> {
        void preparePhysicalRow(Step step);

        void prepareHardwareTiming(Step step);

        S captureBefore(Step step);

        void runProductionLifecycle(Step step);

        void advancePhysicalRow(Step step);

        S captureAfter(Step step);

        void compare(Step step, S before, S after);

        void afterStep(Step step);
    }

    public <S> void execute(Step step, Hooks<S> hooks) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(hooks, "hooks");
        if (step.disposition() == Disposition.OFFSET_HANDOFF) {
            throw new IllegalStateException(
                    "OFFSET_HANDOFF cannot consume a host step");
        }
        if (currentStep != null) {
            throw new IllegalStateException("trace run frame driver is already executing");
        }
        currentStep = step;
        Throwable primary = null;
        try {
            hooks.preparePhysicalRow(step);
            hooks.prepareHardwareTiming(step);
            S before = hooks.captureBefore(step);
            if (step.disposition().runsProductionLifecycle()) {
                hooks.runProductionLifecycle(step);
            }
            hooks.advancePhysicalRow(step);
            S after = hooks.captureAfter(step);
            hooks.compare(step, before, after);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                hooks.afterStep(step);
            } catch (RuntimeException | Error cleanupFailure) {
                if (primary != null) {
                    primary.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            } finally {
                currentStep = null;
            }
        }
    }

    public Disposition currentDisposition() {
        return currentStep != null ? currentStep.disposition() : null;
    }

    public boolean defersBoundaryCommitAfterCurrentRow() {
        return currentStep != null
                && currentStep.deferBoundaryCommitAfterRow();
    }

    /**
     * Keeps a completed presentation owner in place across a recorded span
     * where the ROM did not advance its VBlank counter. The row immediately
     * before the span is included because committing at its end would expose
     * destination work one physical row too early.
     */
    public static boolean shouldDeferBoundaryCommit(
            boolean currentObservedVblankAdvance,
            boolean nextObservedVblankAdvance) {
        return !currentObservedVblankAdvance
                || !nextObservedVblankAdvance;
    }

    /** True on the first represented closure after a no-VBlank span. */
    public static boolean shouldCommitDeferredBoundaryAfterClosure(
            boolean previousObservedVblankAdvance,
            boolean currentObservedVblankAdvance) {
        return !previousObservedVblankAdvance
                && currentObservedVblankAdvance;
    }

    public static Disposition selectDisposition(
            TraceRunPlaybackCoordinator.Phase coordinatorPhase,
            SegmentExecutionPolicy executionPolicy,
            TraceExecutionPhase rowPhase) {
        return selectDisposition(
                coordinatorPhase, executionPolicy, rowPhase,
                true, true, false);
    }

    public static Disposition selectDisposition(
            TraceRunPlaybackCoordinator.Phase coordinatorPhase,
            SegmentExecutionPolicy executionPolicy,
            TraceExecutionPhase rowPhase,
            boolean observedVblankCounterAdvance) {
        return selectDisposition(
                coordinatorPhase, executionPolicy, rowPhase,
                observedVblankCounterAdvance, true, false);
    }

    public static Disposition selectDisposition(
            TraceRunPlaybackCoordinator.Phase coordinatorPhase,
            SegmentExecutionPolicy executionPolicy,
            TraceExecutionPhase rowPhase,
            boolean observedVblankCounterAdvance,
            boolean previousObservedVblankCounterAdvance) {
        return selectDisposition(
                coordinatorPhase, executionPolicy, rowPhase,
                observedVblankCounterAdvance,
                previousObservedVblankCounterAdvance, false);
    }

    public static Disposition selectDisposition(
            TraceRunPlaybackCoordinator.Phase coordinatorPhase,
            SegmentExecutionPolicy executionPolicy,
            TraceExecutionPhase rowPhase,
            boolean observedVblankCounterAdvance,
            boolean previousObservedVblankCounterAdvance,
            boolean destinationGameplayModeReached) {
        Objects.requireNonNull(coordinatorPhase, "coordinatorPhase");
        Objects.requireNonNull(executionPolicy, "executionPolicy");
        Objects.requireNonNull(rowPhase, "rowPhase");
        return switch (coordinatorPhase) {
            case TRANSITION_GAP -> Disposition.SHARED_GAP;
            case TERMINAL_TAIL -> Disposition.TERMINAL_TAIL;
            case CURRENT_SEGMENT -> switch (executionPolicy) {
                case GAMEPLAY -> Disposition.GAMEPLAY_SHARED;
                case SPECIAL_LOCAL -> Disposition.SPECIAL_LOCAL;
                case LEVEL_PRESENTATION_BRIDGE -> destinationGameplayModeReached
                        ? Disposition.PRESENTATION_SUPPRESSED_CLOSURE
                        : switch (rowPhase) {
                    // Recorder row zero carries a full-state bootstrap shape,
                    // but the segment policy has already proven that the
                    // destination is native presentation rather than gameplay.
                    case FULL_LEVEL_FRAME -> Disposition.PRESENTATION_VBLANK;
                    case VBLANK_ONLY -> observedVblankCounterAdvance
                            && previousObservedVblankCounterAdvance
                            ? Disposition.PRESENTATION_VBLANK
                            : Disposition.PRESENTATION_SUPPRESSED_CLOSURE;
                    case ADVANCE_ONLY -> Disposition.PRESENTATION_ADVANCE_ONLY;
                    default -> throw new IllegalStateException(
                            "presentation bridge cannot execute " + rowPhase);
                };
            };
            case DESTINATION_READY -> Disposition.OFFSET_HANDOFF;
            case COMPLETE, FAILED -> throw new IllegalStateException(
                    "completed run cannot consume another physical row");
        };
    }
}
