package com.openggf.trace.timing;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.PendingRecordedSubmission;
import com.openggf.game.timing.RecordedCompletionAuthority;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded adapter from recorded completion edges to the narrow production
 * readiness-admission capability.
 *
 * <p>This adapter can neither submit nor prepare work. Production independently
 * owns the FIFO identity and payload; a recorded edge can only release its
 * already-prepared matching head at the represented service boundary.
 */
public final class HardwareTimingReplayPort
        implements RewindSnapshottable<HardwareTimingReplaySnapshot> {
    public static final String REWIND_KEY = "hardware-timing-replay";

    private final RecordedCompletionAuthority authority;
    private final Set<String> consumedIdentities = new LinkedHashSet<>();

    private HardwareTimingSchedule schedule = HardwareTimingSchedule.empty();
    private int edgeCursor;
    private Integer rawFrameLatch;
    private HardwareServiceBoundary lastAppliedBoundary;
    private boolean installed;
    private boolean runComplete;

    public HardwareTimingReplayPort(RecordedCompletionAuthority authority) {
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    public void install(HardwareTimingSchedule schedule) {
        HardwareTimingSchedule checked = validateSchedule(schedule);
        install(checked, firstOrdinals(checked));
    }

    /**
     * Installs the initial structural-run schedule and its one-time hardware
     * identity base. The explicit form is used when the first represented
     * segment is empty but the run's initial state still has a reviewed base.
     * Segment handoffs can never establish or change this base.
     */
    public void install(
            HardwareTimingSchedule schedule,
            Map<HardwareWorkKind, Long> initialOrdinalBases) {
        HardwareTimingSchedule checked = validateSchedule(schedule);
        Objects.requireNonNull(initialOrdinalBases, "initialOrdinalBases");
        if (installed && !runComplete) {
            throw new IllegalStateException(
                    "hardware timing replay is already installed");
        }
        this.schedule = checked;
        edgeCursor = 0;
        consumedIdentities.clear();
        authority.configureAdmissionPolicies(checked.admissionPolicies());
        authority.initializeOrdinalBases(initialOrdinalBases);
        rawFrameLatch = null;
        lastAppliedBoundary = null;
        installed = true;
        runComplete = false;
    }

    public void beginRawFrame(int rawFrame) {
        requireActive();
        if (rawFrame < 0) {
            throw new IllegalArgumentException(
                    "hardware timing raw_frame must be non-negative: " + rawFrame);
        }
        if (rawFrameLatch != null) {
            if (rawFrame < rawFrameLatch) {
                throw new IllegalStateException(
                        "hardware timing raw_frame moved backward: previous="
                                + rawFrameLatch + ", current=" + rawFrame);
            }
            if (rawFrame == rawFrameLatch) {
                // A setup/admission retry represents a fresh production drive
                // of the same trace row, so boundary ordering restarts even
                // though the physical raw-frame identity does not advance.
                lastAppliedBoundary = null;
                return;
            }
        }
        rejectEdgeBefore(rawFrame);
        rawFrameLatch = rawFrame;
        lastAppliedBoundary = null;
    }

    /**
     * Deactivates row authority while production crosses a movie frame that
     * has no represented trace row. Production hardware work may continue,
     * but no recorded completion edge may be applied until the next
     * {@link #beginRawFrame(int)} call.
     */
    public void enterUnrepresentedGap() {
        requireActive();
        rawFrameLatch = null;
        lastAppliedBoundary = null;
    }

    public void apply(HardwareServiceBoundary boundary) {
        requireActive();
        Objects.requireNonNull(boundary, "boundary");
        if (rawFrameLatch == null) {
            return;
        }
        if (lastAppliedBoundary != null
                && boundary.ordinal() <= lastAppliedBoundary.ordinal()) {
            throw new IllegalStateException(
                    "duplicate or reordered hardware service boundary at raw_frame="
                            + rawFrameLatch + ": previous=" + lastAppliedBoundary
                            + ", current=" + boundary);
        }

        rejectEdgeBefore(rawFrameLatch);
        HardwareCompletionEdge next = nextEdge();
        if (next != null
                && next.rawFrame() == rawFrameLatch
                && next.boundary().ordinal() < boundary.ordinal()) {
            throw new IllegalStateException(
                    "unconsumed hardware completion edge before boundary "
                            + boundary + ": " + describe(next));
        }

        consumeAtBoundary(boundary, false);
        lastAppliedBoundary = boundary;
    }

    /**
     * Exposes an exact loop-tail completion recorded on the current suppressed
     * row after that row has traversed VInt service.
     */
    public boolean applySuppressedRowCompletion() {
        requireActive();
        if (rawFrameLatch == null) {
            return false;
        }
        rejectEdgeBefore(rawFrameLatch);
        HardwareCompletionEdge next = nextEdge();
        if (next == null || next.rawFrame() > rawFrameLatch) {
            return false;
        }
        if (lastAppliedBoundary != HardwareServiceBoundary.VINT_SERVICE) {
            throw new IllegalStateException(
                    "suppressed-row completion requires current-row VINT_SERVICE: "
                            + describe(next));
        }
        if (next.boundary() != HardwareServiceBoundary.PRE_MAIN_LOOP) {
            throw new IllegalStateException(
                    "suppressed row cannot expose hardware completion at "
                            + next.boundary() + ": " + describe(next));
        }
        consumeAtBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP, true);
        lastAppliedBoundary = HardwareServiceBoundary.PRE_MAIN_LOOP;
        return true;
    }

    private void consumeAtBoundary(
            HardwareServiceBoundary boundary,
            boolean suppressedRow) {
        HardwareCompletionEdge next;
        while ((next = nextEdge()) != null
                && next.rawFrame() == rawFrameLatch
                && next.boundary() == boundary) {
            String identity = identity(next);
            if (!consumedIdentities.add(identity)) {
                throw new IllegalStateException(
                        "duplicate hardware completion edge consumption: "
                                + describe(next));
            }
            try {
                if (suppressedRow) {
                    authority.admitRecordedSuppressedRowCompletion(
                            boundary,
                            next.kind(),
                            next.ordinal(),
                            next.submissionFingerprint());
                } else {
                    authority.admitRecordedCompletion(
                            boundary,
                            next.kind(),
                            next.ordinal(),
                            next.submissionFingerprint());
                }
            } catch (RuntimeException failure) {
                consumedIdentities.remove(identity);
                throw failure;
            }
            edgeCursor++;
        }
    }

    public void handoffTo(HardwareTimingSchedule nextSchedule) {
        requireActive();
        verifySegmentEdges();
        HardwareTimingSchedule checkedNext = validateSchedule(nextSchedule);
        if (!checkedNext.admissionPolicies().equals(schedule.admissionPolicies())) {
            throw new IllegalArgumentException(
                    "hardware timing segment changes recorded admission policy");
        }
        for (HardwareCompletionEdge edge : checkedNext.edges()) {
            if (consumedIdentities.contains(identity(edge))) {
                throw new IllegalStateException(
                        "next segment repeats an already-consumed hardware completion edge: "
                                + describe(edge));
            }
        }

        List<PendingRecordedSubmission> pending = authority.pendingSubmissions();
        for (PendingRecordedSubmission submission : pending) {
            HardwareWorkHandle handle = submission.handle();
            if (!submission.exportableAcrossSegment()) {
                throw new IllegalStateException(
                        "non-exportable pending hardware submission at segment end: "
                                + describe(handle));
            }
            List<HardwareCompletionEdge> candidates = checkedNext.edges().stream()
                    .filter(edge -> edge.kind() == handle.kind()
                            && edge.ordinal() == handle.ordinal())
                    .toList();
            boolean exactFutureEdge = candidates.stream().anyMatch(edge ->
                    edge.submissionFingerprint()
                            .equals(handle.submissionFingerprint()));
            if (!exactFutureEdge) {
                throw new IllegalStateException(
                        "exportable pending hardware submission requires a matching "
                                + "next-segment edge: engine pending=" + describe(handle)
                                + "; next-segment candidates="
                                + candidates.stream()
                                .map(HardwareTimingReplayPort::describe)
                                .toList());
            }
        }

        schedule = checkedNext;
        edgeCursor = 0;
        rawFrameLatch = null;
        lastAppliedBoundary = null;
    }

    public void verifySegmentEdges() {
        requireActive();
        HardwareCompletionEdge edge = nextEdge();
        if (edge != null) {
            throw new IllegalStateException(
                    "unconsumed hardware completion edge at segment end: "
                            + describe(edge));
        }
    }

    public void verifyRunComplete() {
        if (runComplete) {
            return;
        }
        requireActive();
        verifySegmentEdges();
        authority.endRecordedAdmission();
        runComplete = true;
    }

    /**
     * Closes a semantically complete trace prefix without admitting future
     * recorded edges. Every edge represented by the prefix and every
     * production submission it created must already be complete.
     */
    public void verifyPrefixComplete(int inclusiveRawFrame) {
        requireActive();
        HardwareCompletionEdge next = nextEdge();
        if (next != null && next.rawFrame() <= inclusiveRawFrame) {
            throw new IllegalStateException(
                    "unconsumed hardware completion edge at prefix end raw_frame="
                            + inclusiveRawFrame + ": " + describe(next));
        }
        List<PendingRecordedSubmission> pendingSubmissions = authority.pendingSubmissions();
        if (!pendingSubmissions.isEmpty()) {
            throw new IllegalStateException(
                    "pending recorded hardware submissions at prefix end raw_frame="
                            + inclusiveRawFrame + ": " + pendingSubmissions.stream()
                            .map(PendingRecordedSubmission::handle)
                            .map(HardwareTimingReplayPort::describe)
                            .toList());
        }
        authority.endRecordedAdmission();
        runComplete = true;
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public HardwareTimingReplaySnapshot capture() {
        return new HardwareTimingReplaySnapshot(
                schedule,
                edgeCursor,
                consumedIdentities,
                rawFrameLatch,
                lastAppliedBoundary,
                installed,
                runComplete);
    }

    @Override
    public void restore(HardwareTimingReplaySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        schedule = validateSchedule(snapshot.schedule());
        edgeCursor = snapshot.edgeCursor();
        consumedIdentities.clear();
        consumedIdentities.addAll(snapshot.consumedIdentities());
        rawFrameLatch = snapshot.rawFrameLatch();
        lastAppliedBoundary = snapshot.lastAppliedBoundary();
        installed = snapshot.installed();
        runComplete = snapshot.runComplete();
    }

    @Override
    public void resetForMissingSnapshot() {
        schedule = HardwareTimingSchedule.empty();
        edgeCursor = 0;
        consumedIdentities.clear();
        rawFrameLatch = null;
        lastAppliedBoundary = null;
        installed = false;
        runComplete = false;
    }

    private HardwareCompletionEdge nextEdge() {
        return edgeCursor < schedule.edges().size()
                ? schedule.edges().get(edgeCursor)
                : null;
    }

    private void rejectEdgeBefore(int rawFrame) {
        HardwareCompletionEdge next = nextEdge();
        if (next != null && next.rawFrame() < rawFrame) {
            throw new IllegalStateException(
                    "unconsumed hardware completion edge before raw_frame="
                            + rawFrame + ": " + describe(next));
        }
    }

    private void requireActive() {
        if (!installed) {
            throw new IllegalStateException(
                    "hardware timing replay schedule is not installed");
        }
        if (runComplete) {
            throw new IllegalStateException(
                    "hardware timing replay run is already complete");
        }
    }

    private static HardwareTimingSchedule validateSchedule(
            HardwareTimingSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        HardwareCompletionEdge previous = null;
        Set<String> identities = new LinkedHashSet<>();
        EnumMap<HardwareWorkKind, Long> lastOrdinals =
                new EnumMap<>(HardwareWorkKind.class);
        for (HardwareCompletionEdge edge : schedule.edges()) {
            Objects.requireNonNull(edge, "hardware completion edge");
            if (edge.rawFrame() < 0) {
                throw new IllegalArgumentException(
                        "hardware completion edge raw_frame must be non-negative: "
                                + edge.rawFrame());
            }
            Objects.requireNonNull(edge.boundary(), "hardware completion boundary");
            Objects.requireNonNull(edge.kind(), "hardware completion kind");
            if (schedule.admissionPolicies().get(edge.kind())
                    != com.openggf.game.timing.HardwareReadinessAdmissionPolicy.RECORDED) {
                throw new IllegalArgumentException(
                        "hardware completion edge kind is not recorded by schema "
                                + schedule.schema() + ": " + edge.kind());
            }
            Objects.requireNonNull(
                    edge.submissionFingerprint(), "hardware completion fingerprint");
            if (edge.ordinal() < 0) {
                throw new IllegalArgumentException(
                        "hardware completion ordinal must be non-negative: "
                                + edge.ordinal());
            }
            if (previous != null
                    && HardwareTimingSchedule.CANONICAL_ORDER.compare(previous, edge) > 0) {
                throw new IllegalArgumentException(
                        "hardware completion edges are not in canonical order at "
                                + describe(edge));
            }
            if (!identities.add(identity(edge))) {
                throw new IllegalArgumentException(
                        "duplicate hardware completion edge identity: "
                                + describe(edge));
            }
            Long lastOrdinal = lastOrdinals.put(
                    edge.kind(), edge.ordinal());
            if (lastOrdinal != null
                    && (lastOrdinal == Long.MAX_VALUE
                    || edge.ordinal() != lastOrdinal + 1)) {
                throw new IllegalArgumentException(
                        "noncontiguous hardware completion ordinals for "
                                + edge.kind() + ": expected ordinal "
                                + (lastOrdinal == Long.MAX_VALUE
                                ? "<exhausted>" : lastOrdinal + 1)
                                + ", found " + edge.ordinal());
            }
            previous = edge;
        }
        return schedule;
    }

    private static Map<HardwareWorkKind, Long> firstOrdinals(
            HardwareTimingSchedule schedule) {
        EnumMap<HardwareWorkKind, Long> firstOrdinals =
                new EnumMap<>(HardwareWorkKind.class);
        for (HardwareCompletionEdge edge : schedule.edges()) {
            firstOrdinals.putIfAbsent(edge.kind(), edge.ordinal());
        }
        return Map.copyOf(firstOrdinals);
    }

    private static String identity(HardwareCompletionEdge edge) {
        return edge.kind() + "#" + edge.ordinal();
    }

    private static String describe(HardwareCompletionEdge edge) {
        return "raw_frame=" + edge.rawFrame()
                + " boundary=" + edge.boundary()
                + " expected completion=" + edge.kind() + "#" + edge.ordinal()
                + " " + edge.submissionFingerprint();
    }

    private static String describe(HardwareWorkHandle handle) {
        return handle.kind() + "#" + handle.ordinal()
                + " " + handle.submissionFingerprint();
    }
}
