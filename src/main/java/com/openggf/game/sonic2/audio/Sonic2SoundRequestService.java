package com.openggf.game.sonic2.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.presentation.AudioPresentationForwardService;
import com.openggf.audio.presentation.AudioRequestService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Production-owned Sonic 2 request service around the shipped mailbox and queue model.
 *
 * <p>Requests enter as immutable engine commands. One forward transaction performs the exact
 * {@code sndDriverInput -> zCycleQueue -> zPlaySoundByIndex} ordering, but publishes neither its
 * presentation consequence nor its diagnostic events until the owning presentation transaction
 * commits. The order and boundaries are source-owned by {@code s2.asm:1270-1332} and
 * {@code s2.sounddriver.asm:1496-1600}; SFX transforms follow {@code :2116-2178}, and music-load
 * state clearing follows {@code :2580-2655}.</p>
 */
public final class Sonic2SoundRequestService implements AudioRequestService {
    private final Sonic2SoundRequestPipeline<PendingRequest> pipeline =
            new Sonic2SoundRequestPipeline<>();
    private final List<EventTemplate> pendingSubmissionEvents = new ArrayList<>();
    private Consumer<Event> observer = ignored -> { };
    private Thread observerOwner;
    private boolean observerLeased;
    private boolean forwardOpen;
    private boolean publishingDiagnostics;
    private long nextEventOrdinal = 1;

    @Override
    public void submitMusic(int nativeRequestId, AudioCommand command) {
        assertNotPublishingDiagnostics();
        PendingRequest pending = new PendingRequest(nativeRequestId, command);
        Sonic2SoundRequestPipeline.SourceSlot slot =
                pipeline.snapshot().music0().requestByte() == 0
                        ? Sonic2SoundRequestPipeline.SourceSlot.MUSIC0
                        : Sonic2SoundRequestPipeline.SourceSlot.MUSIC1;
        pipeline.submitMusic(nativeRequestId, pending);
        pendingSubmissionEvents.add(new SubmissionTemplate(slot, nativeRequestId));
    }

    @Override
    public void submitSound(int nativeRequestId, AudioCommand command) {
        assertNotPublishingDiagnostics();
        pipeline.submitSound(nativeRequestId, new PendingRequest(nativeRequestId, command));
        pendingSubmissionEvents.add(new SubmissionTemplate(
                Sonic2SoundRequestPipeline.SourceSlot.SFX0, nativeRequestId));
    }

    public void submitSound2(int nativeRequestId, AudioCommand command) {
        assertNotPublishingDiagnostics();
        pipeline.submitSound2(nativeRequestId, new PendingRequest(nativeRequestId, command));
        pendingSubmissionEvents.add(new SubmissionTemplate(
                Sonic2SoundRequestPipeline.SourceSlot.SFX1, nativeRequestId));
    }

    public ObserverLease addObserver(Consumer<Event> candidate) {
        Objects.requireNonNull(candidate, "observer");
        if (observerLeased) {
            throw new IllegalStateException("Sonic 2 request observer is already leased");
        }
        observer = candidate;
        observerOwner = Thread.currentThread();
        observerLeased = true;
        return new ObserverLease();
    }

    @Override
    public ForwardBoundary beginForwardBoundary() {
        assertNotPublishingDiagnostics();
        if (forwardOpen) {
            throw new IllegalStateException("Sonic 2 forward request boundary is already open");
        }
        forwardOpen = true;
        return new ForwardBoundary(snapshot());
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(pipeline.snapshot(), List.copyOf(pendingSubmissionEvents),
                nextEventOrdinal);
    }

    @Override
    public void restore(AudioPresentationForwardService.Snapshot snapshot) {
        if (!(snapshot instanceof Snapshot selected)) {
            throw new IllegalArgumentException("snapshot does not belong to the Sonic 2 request service");
        }
        if (forwardOpen) {
            throw new IllegalStateException("cannot restore during an open forward boundary");
        }
        restoreUnchecked(selected);
    }

    private void restoreUnchecked(Snapshot snapshot) {
        pipeline.restore(snapshot.pipeline());
        pendingSubmissionEvents.clear();
        pendingSubmissionEvents.addAll(snapshot.pendingSubmissionEvents());
        nextEventOrdinal = snapshot.nextEventOrdinal();
    }

    public final class ForwardBoundary implements AudioPresentationForwardService.ForwardBoundary {
        private final Snapshot before;
        private final List<EventTemplate> stagedEvents = new ArrayList<>();
        private final List<AudioCommand> stagedCommands = new ArrayList<>();
        private PreparedSonic2Lifecycle preparedLifecycle;
        private CommittedSonic2RequestReceipt receipt;
        private long preparedNextEventOrdinal;
        private boolean serviced;
        private boolean consequencesApplied;
        private boolean prepared;
        private boolean closed;
        private boolean diagnosticsPublished;

        private ForwardBoundary(Snapshot before) {
            this.before = before;
        }

        @Override
        public void service(Consumer<AudioCommand> commandSink) {
            requireOpen();
            if (serviced) {
                throw new IllegalStateException("Sonic 2 request boundary was already serviced");
            }
            serviced = true;
            stagedEvents.addAll(pendingSubmissionEvents);
            pendingSubmissionEvents.clear();

            Sonic2SoundRequestPipeline.BridgeResult<PendingRequest> bridge = pipeline.bridge();
            for (Sonic2SoundRequestPipeline.PhysicalTransfer<PendingRequest> transfer
                    : bridge.transfers()) {
                stagedEvents.add(new TransferTemplate(transfer.sourceSlot(), transfer.physicalSlot(),
                        transfer.requestByte(), transfer.voiceTablePointerAlias()));
            }

            int priorityBefore = pipeline.snapshot().sfxPriorityValue();
            Sonic2SoundRequestPipeline.CycleResult<PendingRequest> cycle = pipeline.cycleQueue();
            for (Sonic2SoundRequestPipeline.Request<PendingRequest> discarded
                    : cycle.invalidDiscards()) {
                stagedEvents.add(new DecisionTemplate(discarded.sourceSlot(), discarded.requestByte(),
                        DecisionReason.INVALID_DISCARD, priorityBefore, priorityBefore));
            }
            int priorityAfter = pipeline.snapshot().sfxPriorityValue();
            if (cycle.request() != null) {
                stagedEvents.add(new DecisionTemplate(cycle.request().sourceSlot(),
                        cycle.request().requestByte(), reason(cycle.kind()), priorityBefore,
                        priorityAfter));
            }

            Sonic2SoundRequestPipeline.DispatchResult<PendingRequest> dispatch =
                    pipeline.dispatchQueuedRequest();
            if (dispatch.kind() != Sonic2SoundRequestPipeline.DispatchKind.NOTHING_TO_DISPATCH) {
                stagedEvents.add(new DispatchTemplate(dispatch.originalRequestByte(),
                        dispatch.selectedRequestByte(), dispatch.kind()));
                AudioCommand consequence = consequence(dispatch);
                if (consequence != null) {
                    commandSink.accept(consequence);
                    stagedCommands.add(consequence);
                }
            }
            pipeline.finishDriverInvocation();
        }

        @Override
        public void applyPreparedConsequences(
                List<AudioCommand> appliedCommands) {
            requireOpen();
            if (!serviced) {
                throw new IllegalStateException(
                        "cannot apply an unserviced Sonic 2 request boundary");
            }
            if (consequencesApplied) {
                throw new IllegalStateException(
                        "Sonic 2 request consequences were already applied");
            }
            List<Sonic2RequestOutcome> outcomes = new ArrayList<>();
            for (AudioCommand command : stagedCommands) {
                if (!appliedCommands.contains(command)) {
                    // Rejected SFX/musical data never reaches the device; only
                    // source-owned lifecycle consequences of a resolved command
                    // are eligible for this transaction.
                    continue;
                }
                if (command instanceof AudioCommand.PlayMusic music) {
                    // zPlayMusic initializes zAbsVar and loses Queue2 in the shipped
                    // fixBugs=0 path (s2.sounddriver.asm:2580-2655).
                    outcomes.add(new Sonic2RequestOutcome(command,
                            AppliedOutcomeKind.MUSIC_PLAYBACK_INITIALIZED));
                    if (music.override()) {
                        // fixBugs=0 preserves the one-up's save-before-stop order;
                        // The prepared session mutation has already saved/replaced
                        // the music before this source-owned latch is cleared.
                        outcomes.add(new Sonic2RequestOutcome(command,
                                AppliedOutcomeKind.ONE_UP_STARTED));
                    }
                } else if (command instanceof AudioCommand.StopAllSfx) {
                    // F8 reaches zStopSoundEffects, whose tail clears SFXPriorityVal
                    // (s2.sounddriver.asm:2334-2347).
                    outcomes.add(new Sonic2RequestOutcome(command,
                            AppliedOutcomeKind.STOP_ALL_SFX));
                }
            }
            preparedLifecycle = new PreparedSonic2Lifecycle(outcomes);
            preparedLifecycle.apply(pipeline);
            consequencesApplied = true;
        }

        @Override
        public void prepareCommit() {
            requireOpen();
            if (!serviced || !consequencesApplied) {
                throw new IllegalStateException(
                        "cannot prepare an unapplied Sonic 2 request boundary");
            }
            if (prepared) {
                throw new IllegalStateException(
                        "Sonic 2 request boundary is already prepared");
            }
            List<Event> events = new ArrayList<>(stagedEvents.size());
            long ordinal = nextEventOrdinal;
            for (EventTemplate template : stagedEvents) {
                events.add(template.materialize(ordinal++));
            }
            receipt = new CommittedSonic2RequestReceipt(stagedCommands,
                    preparedLifecycle.outcomes(), events);
            preparedNextEventOrdinal = ordinal;
            prepared = true;
        }

        @Override
        public void commit() {
            requireOpen();
            if (!prepared) {
                throw new IllegalStateException(
                        "cannot commit an unprepared Sonic 2 request boundary");
            }
            nextEventOrdinal = preparedNextEventOrdinal;
            closed = true;
            forwardOpen = false;
        }

        @Override
        public void publishDiagnostics() {
            if (!closed || receipt == null) {
                throw new IllegalStateException(
                        "cannot publish an uncommitted Sonic 2 request receipt");
            }
            if (diagnosticsPublished) {
                return;
            }
            diagnosticsPublished = true;
            publishingDiagnostics = true;
            try {
                for (Event event : receipt.events()) {
                    try {
                        observer.accept(event);
                    } catch (RuntimeException ignored) {
                        // Comparison observers cannot reject committed production audio.
                    }
                }
            } finally {
                publishingDiagnostics = false;
            }
        }

        /** Returns the immutable receipt prepared before this boundary sealed. */
        public CommittedSonic2RequestReceipt preparedReceipt() {
            if (!prepared || receipt == null) {
                throw new IllegalStateException(
                        "Sonic 2 request receipt is not prepared");
            }
            return receipt;
        }

        @Override
        public void rollback() {
            requireOpen();
            restoreUnchecked(before);
            closed = true;
            forwardOpen = false;
        }

        private void requireOpen() {
            if (closed || !forwardOpen) {
                throw new IllegalStateException("Sonic 2 request boundary is closed");
            }
        }
    }

    private static AudioCommand consequence(
            Sonic2SoundRequestPipeline.DispatchResult<PendingRequest> dispatch) {
        if (dispatch.kind() == Sonic2SoundRequestPipeline.DispatchKind.SUPPRESSED_GLOOP
                || dispatch.kind() == Sonic2SoundRequestPipeline.DispatchKind.IGNORED_UNDEFINED_ID) {
            return null;
        }
        AudioCommand command = dispatch.payload().command();
        if (command instanceof AudioCommand.PlaySfx sfx
                && dispatch.selectedRequestByte() != sfx.sfxId()) {
            return new AudioCommand.PlaySfx(dispatch.selectedRequestByte(), sfx.sfxName(),
                    sfx.route(), sfx.pitch(), sfx.donorGameId());
        }
        return command;
    }

    private void assertNotPublishingDiagnostics() {
        if (publishingDiagnostics) {
            throw new IllegalStateException(
                    "Sonic 2 request diagnostics cannot reenter production state");
        }
    }

    private static DecisionReason reason(Sonic2SoundRequestPipeline.DecisionKind kind) {
        return switch (kind) {
            case PROMOTED_MUSIC -> DecisionReason.PROMOTED_MUSIC;
            case PROMOTED_COMMAND -> DecisionReason.PROMOTED_COMMAND;
            case ACCEPTED_SFX -> DecisionReason.ACCEPTED_PRIORITY;
            case REJECTED_SFX -> DecisionReason.REJECTED_PRIORITY;
            case IDLE, BUSY -> throw new IllegalArgumentException("idle/busy has no queue decision");
        };
    }

    public record PendingRequest(int nativeRequestId, AudioCommand command) {
        public PendingRequest {
            if (nativeRequestId < 0 || nativeRequestId > 0xFF) {
                throw new IllegalArgumentException("native request id must be an unsigned byte");
            }
            Objects.requireNonNull(command, "command");
        }
    }

    public record Snapshot(
            Sonic2SoundRequestPipeline.Snapshot<PendingRequest> pipeline,
            List<EventTemplate> pendingSubmissionEvents,
            long nextEventOrdinal)
            implements AudioPresentationForwardService.Snapshot {
        public Snapshot {
            Objects.requireNonNull(pipeline, "pipeline");
            pendingSubmissionEvents = List.copyOf(
                    Objects.requireNonNull(pendingSubmissionEvents, "pendingSubmissionEvents"));
            if (nextEventOrdinal < 1) {
                throw new IllegalArgumentException(
                        "nextEventOrdinal must be positive");
            }
        }
    }

    /** Immutable, post-seal-safe record of one applied request consequence. */
    public record Sonic2RequestOutcome(AudioCommand command,
                                       AppliedOutcomeKind kind) {
        public Sonic2RequestOutcome {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(kind, "kind");
        }
    }

    public enum AppliedOutcomeKind {
        MUSIC_PLAYBACK_INITIALIZED,
        ONE_UP_STARTED,
        STOP_ALL_SFX
    }

    /** Prepared, rollback-covered Sonic 2 driver-byte lifecycle mutation. */
    private record PreparedSonic2Lifecycle(
            List<Sonic2RequestOutcome> outcomes) {
        private PreparedSonic2Lifecycle {
            outcomes = List.copyOf(Objects.requireNonNull(outcomes,
                    "outcomes"));
        }

        private void apply(Sonic2SoundRequestPipeline<PendingRequest> pipeline) {
            for (Sonic2RequestOutcome outcome : outcomes) {
                switch (outcome.kind()) {
                    case MUSIC_PLAYBACK_INITIALIZED ->
                            pipeline.onMusicPlaybackInitialized();
                    case ONE_UP_STARTED -> pipeline.onOneUpStarted();
                    case STOP_ALL_SFX -> pipeline.onStopAllSfx();
                }
            }
        }
    }

    /** Immutable receipt built before seal and handed only to diagnostic observers afterward. */
    public record CommittedSonic2RequestReceipt(List<AudioCommand> commands,
                                                 List<Sonic2RequestOutcome> outcomes,
                                                 List<Event> events) {
        public CommittedSonic2RequestReceipt {
            commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
            outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
            events = List.copyOf(Objects.requireNonNull(events, "events"));
        }
    }

    public sealed interface Event permits Submission, Transfer, Decision, Dispatch {
        long ordinal();
    }

    public record Submission(long ordinal, Sonic2SoundRequestPipeline.SourceSlot sourceMailbox,
                             int rawRequestId) implements Event { }

    public record Transfer(long ordinal, Sonic2SoundRequestPipeline.SourceSlot sourceMailbox,
                           int physicalSlot, int rawRequestId,
                           boolean slot3Alias) implements Event { }

    public record Decision(long ordinal, Sonic2SoundRequestPipeline.QueueSlot queueSlot,
                           int rawRequestId, DecisionReason reason,
                           int priorityBefore, int priorityAfter) implements Event { }

    public record Dispatch(long ordinal, int originalRequestId, int selectedRequestId,
                           Sonic2SoundRequestPipeline.DispatchKind kind) implements Event { }

    public enum DecisionReason {
        INVALID_DISCARD,
        PROMOTED_MUSIC,
        PROMOTED_COMMAND,
        ACCEPTED_PRIORITY,
        REJECTED_PRIORITY
    }

    private sealed interface EventTemplate permits SubmissionTemplate, TransferTemplate,
            DecisionTemplate, DispatchTemplate {
        Event materialize(long ordinal);
    }

    private record SubmissionTemplate(Sonic2SoundRequestPipeline.SourceSlot sourceMailbox,
                                      int rawRequestId) implements EventTemplate {
        @Override public Event materialize(long ordinal) {
            return new Submission(ordinal, sourceMailbox, rawRequestId);
        }
    }

    private record TransferTemplate(Sonic2SoundRequestPipeline.SourceSlot sourceMailbox,
                                    int physicalSlot, int rawRequestId,
                                    boolean slot3Alias) implements EventTemplate {
        @Override public Event materialize(long ordinal) {
            return new Transfer(ordinal, sourceMailbox, physicalSlot, rawRequestId, slot3Alias);
        }
    }

    private record DecisionTemplate(Sonic2SoundRequestPipeline.QueueSlot queueSlot,
                                    int rawRequestId, DecisionReason reason,
                                    int priorityBefore, int priorityAfter) implements EventTemplate {
        @Override public Event materialize(long ordinal) {
            return new Decision(ordinal, queueSlot, rawRequestId, reason, priorityBefore, priorityAfter);
        }
    }

    private record DispatchTemplate(int originalRequestId, int selectedRequestId,
                                    Sonic2SoundRequestPipeline.DispatchKind kind)
            implements EventTemplate {
        @Override public Event materialize(long ordinal) {
            return new Dispatch(ordinal, originalRequestId, selectedRequestId, kind);
        }
    }

    public final class ObserverLease implements AutoCloseable {
        private boolean active = true;

        @Override public void close() {
            if (!active) {
                return;
            }
            if (Thread.currentThread() != observerOwner) {
                throw new IllegalStateException("Sonic 2 request observer lease is owner-thread confined");
            }
            active = false;
            observer = ignored -> { };
            observerOwner = null;
            observerLeased = false;
        }
    }
}
