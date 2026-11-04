package com.openggf.trace.timing;

import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;
import java.util.Objects;

/**
 * Value-free scheduling input for an independently prepared native load phase.
 * The production continuation services interrupts and executes work; this port
 * only matches identities and consumes ordered observations for a mapped row.
 */
public final class InitializationTimingReplayPort implements NativeLevelLoadPhaseAdmission {
    private InitializationTimingSchedule schedule;
    private final java.util.function.Consumer<RepresentedLoadRow> preparedLoadObserver;
    private int cursor;
    private Integer rawFrame;
    private PhaseIdentity active;
    private PhaseIdentity retired;
    private Integer retiredFrame;
    private boolean held;
    private boolean aborted;
    private Long validatedLoad;

    private record PhaseIdentity(long load, int ordinal, Phase phase, String fingerprint) {
        static PhaseIdentity of(PreparedPhase phase) {
            return new PhaseIdentity(phase.loadOrdinal(), phase.phaseOrdinal(),
                    phase.phase(), phase.fingerprint());
        }
    }

    public InitializationTimingReplayPort(InitializationTimingSchedule schedule) {
        this(schedule, row -> { });
    }

    public InitializationTimingReplayPort(InitializationTimingSchedule schedule,
            java.util.function.Consumer<RepresentedLoadRow> preparedLoadObserver) {
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.preparedLoadObserver = Objects.requireNonNull(preparedLoadObserver);
    }

    /** Called only by the existing physical-row mapper, before production. */
    public void beginRawFrame(int nextRawFrame) {
        requireActiveRun();
        if (nextRawFrame < 0 || (rawFrame != null && nextRawFrame < rawFrame)) {
            throw new IllegalArgumentException("initialization raw frame moved backwards");
        }
        if (rawFrame != null && nextRawFrame == rawFrame) return;
        if (active != null && rawFrame != null && (long) nextRawFrame != (long) rawFrame + 1) {
            throw new IllegalStateException("pending initialization skipped physical rows");
        }
        if (cursor < schedule.events().size()
                && schedule.events().get(cursor).rawFrame() < nextRawFrame) {
            throw new IllegalStateException("unconsumed initialization timing before raw frame " + nextRawFrame);
        }
        rawFrame = nextRawFrame;
        held = false;
    }

    /** Opaque membership proof for work submitted during an observed loading row. */
    public static final class RepresentedLoadRow {
        private final InitializationTimingReplayPort owner;
        private final int frame;
        private final long load;
        private RepresentedLoadRow(InitializationTimingReplayPort owner, int frame, long load) {
            this.owner = owner; this.frame = frame; this.load = load;
        }
        void requireCurrent() {
            owner.requireActiveRun();
            if (!Objects.equals(owner.rawFrame, frame) || !Objects.equals(owner.validatedLoad, load)
                    || !owner.schedule.coversLoadRow(load, frame)) {
                throw new IllegalStateException("initialization membership token is no longer current");
            }
        }
        public long loadOrdinal() { requireCurrent(); return load; }
        public int rawFrame() { requireCurrent(); return frame; }
    }

    /**
     * Only a matched, independently prepared display-clear operation arms this
     * load. Later dedicated rows retain that load's submission membership;
     * they never supply queued completion edges or select work.
     */
    public RepresentedLoadRow representedLoadRow() {
        requireActiveRun();
        if (rawFrame == null || validatedLoad == null || !schedule.coversLoadRow(validatedLoad, rawFrame)) return null;
        return new RepresentedLoadRow(this, rawFrame, validatedLoad);
    }

    public void enterUnrepresentedGap() {
        requireActiveRun();
        if (active != null) throw new IllegalStateException("pending initialization lost physical coverage");
        rawFrame = null;
        held = false;
    }

    @Override
    public Decision next(PreparedPhase phase) {
        requireActiveRun();
        PhaseIdentity requested = PhaseIdentity.of(Objects.requireNonNull(phase, "phase"));
        if (active == null) {
            if (retired != null && (requested.load() < retired.load()
                    || (requested.load() == retired.load() && requested.ordinal() <= retired.ordinal()))) {
                throw new IllegalStateException("initialization phase was already retired or reordered");
            }
            if (!schedule.hasRecordedInput()) {
                retired = requested;
                return Decision.RESUME;
            }
        } else if (!active.equals(requested)) {
            throw new IllegalStateException("production initialization phase identity changed while pending");
        }
        if (rawFrame == null) {
            throw new IllegalStateException("represented initialization phase has no current timing row");
        }
        if (held) return Decision.HOLD;
        if (cursor >= schedule.events().size()) {
            throw new IllegalStateException("represented initialization phase has no recorded observation");
        }
        InitializationTimingEvent event = schedule.events().get(cursor);
        if (event.rawFrame() != rawFrame || !matches(event, requested)) {
            throw new IllegalStateException("initialization timing does not match the current prepared operation: "
                    + event + ", production=" + requested + ", current raw frame=" + rawFrame);
        }
        active = requested;
        if (requested.phase() == Phase.DISPLAY_CLEAR && !Objects.equals(validatedLoad, requested.load())) {
            validatedLoad = requested.load();
            preparedLoadObserver.accept(representedLoadRow());
        }
        cursor++;
        return switch (event.operation()) {
            case VINT_0 -> Decision.VINT_0;
            case HOLD -> {
                held = true;
                yield Decision.HOLD;
            }
            case RESUME -> {
                active = null;
                retired = requested;
                retiredFrame = rawFrame;
                yield Decision.RESUME;
            }
        };
    }

    private static boolean matches(InitializationTimingEvent event, PhaseIdentity phase) {
        Phase nativePhase = switch (event.phase()) {
            case DISPLAY_CLEAR -> Phase.DISPLAY_CLEAR;
            case LEVEL_DATA_DECODE -> Phase.LEVEL_DATA_DECODE;
            case LEVEL_SETUP -> Phase.LEVEL_SETUP;
            case INITIAL_PLACEMENT -> Phase.INITIAL_PLACEMENT;
            case INITIAL_OBJECTS -> Phase.INITIAL_OBJECTS;
            case INITIAL_PRESENTATION -> Phase.INITIAL_PRESENTATION;
        };
        return event.loadOrdinal() == phase.load() && event.phaseOrdinal() == phase.ordinal()
                && nativePhase == phase.phase() && event.submissionFingerprint().equals(phase.fingerprint());
    }

    /** Proof that this load's final phase ended on an earlier physical row. */
    public static final class CompletedLoadRow {
        private final InitializationTimingReplayPort owner;
        private final int frame;
        private final long load;
        private CompletedLoadRow(InitializationTimingReplayPort owner, int frame, long load) {
            this.owner = owner; this.frame = frame; this.load = load;
        }
        void requireCurrent() {
            owner.requireActiveRun();
            if (!Objects.equals(owner.rawFrame, frame) || !owner.completedBeforeCurrentRow(load))
                throw new IllegalStateException("completed initialization receipt is no longer current");
        }
        long loadOrdinal() { requireCurrent(); return load; }
    }
    private boolean completedBeforeCurrentRow(long load) {
        return rawFrame != null && retiredFrame != null && rawFrame > retiredFrame
                && retired != null && retired.load() == load && retired.phase() == Phase.INITIAL_PRESENTATION;
    }
    public CompletedLoadRow completedLoadRow(long load) {
        requireActiveRun();
        return completedBeforeCurrentRow(load) ? new CompletedLoadRow(this, rawFrame, load) : null;
    }

    /** Keeps a represented pending phase across a real segment/interstitial boundary. */
    public void handoffTo(InitializationTimingSchedule nextSchedule) {
        requireActiveRun();
        verifyScheduleConsumed();
        Objects.requireNonNull(nextSchedule, "nextSchedule");
        if (active != null && !nextSchedule.hasRecordedInput()) {
            throw new IllegalStateException("pending represented initialization lost timing coverage");
        }
        schedule = nextSchedule;
        cursor = 0;
        rawFrame = null;
        held = false;
    }

    public void verifyScheduleConsumed() {
        if (cursor != schedule.events().size()) {
            throw new IllegalStateException("unconsumed initialization timing observations: "
                    + (schedule.events().size() - cursor));
        }
    }

    public void verifyRunComplete() {
        requireActiveRun();
        verifyScheduleConsumed();
        if (active != null) throw new IllegalStateException("initialization phase remains pending at run end");
    }

    public void abort() {
        aborted = true;
        rawFrame = null;
        active = null;
    }

    private void requireActiveRun() {
        if (aborted) throw new IllegalStateException("initialization timing run was aborted");
    }
}
