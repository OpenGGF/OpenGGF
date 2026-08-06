package com.openggf.game.sonic3k.resources;

import com.openggf.data.Rom;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkPreparation;
import com.openggf.game.timing.HardwareWorkPreparationSnapshot;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.resources.QueueServiceObservation;
import com.openggf.tools.KosinskiReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Coordinator for S3K's four-entry {@code Queue_Kos_Module} FIFO.
 *
 * <p>Archive parents own no decoder. Each aligned standard-Kosinski module is
 * submitted to the one session-owned direct FIFO and claimed only after that
 * complete physical FIFO becomes empty.
 */
public final class S3kKosModuleQueue {
    private static final int MAX_QUEUE_DEPTH = 4;
    private static final int PATTERN_BYTES = 32;
    private static final int INSPECTION_LIMIT = 0x40000;
    private static final String COMPRESSION_VARIANT = "kosinski_moduled";

    private final HardwareTimingService timing;
    private final S3kKosDecompressionQueue directQueue;
    private final Map<HardwareWorkHandle, S3kKosModuleDescriptor> descriptors =
            new HashMap<>();
    private final Set<HardwareWorkHandle> freshLevelHandoffHandles = new HashSet<>();

    public S3kKosModuleQueue(
            HardwareTimingService timing,
            S3kKosDecompressionQueue directQueue) {
        this.timing = Objects.requireNonNull(timing, "timing");
        this.directQueue = Objects.requireNonNull(directQueue, "directQueue");
        if (directQueue.timingOwner() != timing) {
            throw new IllegalArgumentException(
                    "KosM and direct Kos queues must share one hardware timing ledger");
        }
    }

    public HardwareWorkHandle queue(
            Rom rom,
            int source,
            int destinationPatternAddress) throws IOException {
        return queueInternal(
                rom, source, destinationPatternAddress, false);
    }

    public HardwareWorkHandle queueForIczSeamlessHandoff(
            Rom rom,
            int source,
            int destinationPatternAddress) throws IOException {
        return queueInternal(
                rom, source, destinationPatternAddress, true);
    }

    /**
     * Appends one native sequential batch only after every archive has been
     * validated and the physical FIFO has room for the whole batch.
     */
    public List<HardwareWorkHandle> queueSequentialBatch(
            Rom rom, List<Integer> sources, int destinationPatternAddress)
            throws IOException {
        Objects.requireNonNull(rom, "rom");
        List<Integer> stableSources = List.copyOf(sources);
        if (!hasCapacityFor(stableSources.size())) {
            throw new IllegalStateException(
                    "S3K KosM module FIFO cannot fit batch of "
                            + stableSources.size());
        }
        List<PreparedSubmission> prepared = new java.util.ArrayList<>();
        int destination = destinationPatternAddress;
        for (int source : stableSources) {
            PreparedSubmission submission = prepareSubmission(
                    rom, source, destination, false);
            prepared.add(submission);
            destination = Math.addExact(
                    destination,
                    submission.descriptor().destinationLength() / PATTERN_BYTES);
        }
        List<HardwareWorkHandle> handles = new java.util.ArrayList<>();
        for (PreparedSubmission submission : prepared) {
            handles.add(submitPrepared(submission));
        }
        return List.copyOf(handles);
    }

    /** Whether a producer can append an entire native FIFO batch without partial submission. */
    public boolean hasCapacityFor(int submissions) {
        if (submissions < 0) {
            throw new IllegalArgumentException("submissions must not be negative");
        }
        return physicalQueueSize() + submissions <= MAX_QUEUE_DEPTH;
    }

    private HardwareWorkHandle queueInternal(
            Rom rom,
            int source,
            int destinationPatternAddress,
            boolean exportableAcrossSegment) throws IOException {
        Objects.requireNonNull(rom, "rom");
        if (physicalQueueSize() >= MAX_QUEUE_DEPTH) {
            throw new IllegalStateException("S3K KosM module FIFO is full");
        }
        return submitPrepared(prepareSubmission(
                rom, source, destinationPatternAddress, exportableAcrossSegment));
    }

    private PreparedSubmission prepareSubmission(
            Rom rom,
            int source,
            int destinationPatternAddress,
            boolean exportableAcrossSegment) throws IOException {
        Objects.requireNonNull(rom, "rom");
        long remaining = rom.getSize() - source;
        if (source < 0 || remaining < 2) {
            throw new IOException("KosM source is outside ROM: 0x"
                    + Integer.toHexString(source));
        }
        int inspectionLength = (int) Math.min(remaining, INSPECTION_LIMIT);
        byte[] inspection = rom.readBytes(source, inspectionLength);
        KosinskiReader.ModuledArchiveInfo info =
                KosinskiReader.inspectModuled(inspection, 0);
        byte[] archive = info.compressedLength() == inspection.length
                ? inspection
                : rom.readBytes(source, info.compressedLength());
        S3kKosModuleDescriptor descriptor = new S3kKosModuleDescriptor(
                source,
                info.compressedLength(),
                Math.multiplyExact(destinationPatternAddress, PATTERN_BYTES),
                info.decompressedLength(),
                info.moduleCount());
        S3kKosModulePreparation preparation =
                new S3kKosModulePreparation(descriptor, archive);
        return new PreparedSubmission(descriptor, preparation,
                exportableAcrossSegment);
    }

    private HardwareWorkHandle submitPrepared(PreparedSubmission prepared) {
        S3kKosModuleDescriptor descriptor = prepared.descriptor();
        HardwareWorkHandle handle = timing.submit(new HardwareWorkSubmission(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                descriptor.sourceAddress(),
                descriptor.compressedLength(),
                descriptor.destinationAddress(),
                descriptor.destinationLength(),
                COMPRESSION_VARIANT,
                descriptor.moduleCount(),
                prepared.exportableAcrossSegment(),
                prepared.preparation()));
        descriptors.put(handle, descriptor);
        return handle;
    }

    private record PreparedSubmission(
            S3kKosModuleDescriptor descriptor,
            S3kKosModulePreparation preparation,
            boolean exportableAcrossSegment) {
    }

    public void prepareQueuedModuleBeforeVSync() {
        timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        directQueue.afterTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
    }

    public void processModuleQueueAfterObjects() {
        beforeTimingService(HardwareServiceBoundary.POST_OBJECTS);
        timing.service(HardwareServiceBoundary.POST_OBJECTS);
        afterTimingService(HardwareServiceBoundary.POST_OBJECTS);
    }

    /**
     * Runs the native module state step for a parent published by ScreenEvents
     * after this iteration's direct-queue service, without inventing another
     * hardware timing boundary. The child remains unprepared until the next
     * iteration's direct tail.
     */
    public void stepHeadModuleAfterDirectTail() {
        stepHeadArchive();
    }

    /**
     * Runs the frame's module state step, before the timing ledger is serviced.
     *
     * <p>ROM {@code LevelLoop} calls {@code Process_Kos_Module_Queue} in the loop
     * tail (sonic3k.asm:7908), after {@code ScreenEvents} (7898) and before
     * {@code Process_Kos_Queue} (7887) — which reads as the head of the next
     * iteration but runs ahead of {@code Wait_VSync} (7888) and its
     * {@code addq.w #1,(Level_frame_counter)} (7889), so both calls belong to the
     * tail of the frame whose objects just ran. The step therefore sits at
     * {@code POST_OBJECTS}, immediately ahead of the direct FIFO's own
     * {@code PRE_MAIN_LOOP} service: an archive queued by an object this frame
     * hands its first module to the direct FIFO and sees that child decompressed
     * on the same frame, while an archive whose child is still outstanding is
     * only retired by the next frame's step.
     *
     * <p>Readiness is captured at the same boundary: the archive parent's
     * completion edge is recorded at {@code POST_OBJECTS}, and
     * {@link HardwareTimingService#captureCoordinatorPreparation} requires the
     * capture to name the boundary production last serviced.
     */
    public void beforeTimingService(HardwareServiceBoundary boundary) {
        if (boundary == HardwareServiceBoundary.POST_OBJECTS) {
            stepHeadArchive();
        }
    }

    public void afterTimingService(HardwareServiceBoundary boundary) {
        if (boundary == HardwareServiceBoundary.POST_OBJECTS) {
            capturePreparedArchives();
        }
    }

    /**
     * Performs the single {@code Process_Kos_Module_Queue} state step this frame
     * (sonic3k.asm:2726-2790): either submit the head archive's current module to
     * the direct FIFO, or — once that FIFO has drained — claim it. An archive that
     * has already been retired no longer occupies a queue slot (the DMA branch
     * shifts it out at 2778-2788), so it is skipped rather than blocking.
     */
    private void stepHeadArchive() {
        for (HardwareWorkHandle handle : timing.pendingHandles()) {
            if (handle.kind() != HardwareWorkKind.KOS_MODULE_QUEUE
                    || timing.isReady(handle)) {
                continue;
            }
            S3kKosModulePreparation preparation = preparationFor(handle);
            if (preparation.isPrepared()) {
                continue;
            }
            preparation.coordinate(handle, directQueue);
            return;
        }
    }

    private void capturePreparedArchives() {
        for (HardwareWorkHandle handle : timing.pendingHandles()) {
            if (handle.kind() != HardwareWorkKind.KOS_MODULE_QUEUE
                    || timing.isReady(handle)) {
                continue;
            }
            if (preparationFor(handle).isPrepared()) {
                timing.captureCoordinatorPreparation(
                        handle, HardwareServiceBoundary.POST_OBJECTS);
            }
        }
    }

    private S3kKosModulePreparation preparationFor(HardwareWorkHandle handle) {
        HardwareWorkPreparation generic = timing.coordinatorPreparation(handle);
        if (!(generic instanceof S3kKosModulePreparation preparation)) {
            throw new IllegalStateException(
                    "KosM parent has an unexpected preparation owner");
        }
        descriptors.putIfAbsent(handle, preparation.descriptor);
        return preparation;
    }

    int physicalQueueSize() {
        int count = 0;
        for (HardwareWorkHandle handle : timing.pendingHandles()) {
            if (handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE
                    && !timing.coordinatorPreparation(handle).isPrepared()) {
                count++;
            }
        }
        return count;
    }

    public boolean hasCapacity() {
        return physicalQueueSize() < MAX_QUEUE_DEPTH;
    }

    public boolean modulesLeft() {
        return timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) != 0;
    }

    QueueDiagnosticSnapshot captureDiagnostics(
            List<QueueServiceObservation> observations) {
        List<HardwareWorkHandle> physical = timing.pendingHandles().stream()
                .filter(handle -> handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                .filter(handle -> !timing.coordinatorPreparation(handle).isPrepared())
                .sorted(java.util.Comparator.comparingLong(
                        HardwareWorkHandle::ordinal))
                .toList();
        if (physical.isEmpty()) {
            return QueueDiagnosticSnapshot.idle(
                    QueueDiagnosticSnapshot.Kind.S3K_KOS_MODULE,
                    observations);
        }
        HardwareWorkHandle active = physical.getFirst();
        S3kKosModulePreparation activePreparation =
                (S3kKosModulePreparation) timing.coordinatorPreparation(active);
        S3kKosModuleDescriptor activeDescriptor =
                activePreparation.descriptor;
        List<String> waiting = physical.stream()
                .skip(1)
                .map(handle -> (S3kKosModulePreparation)
                        timing.coordinatorPreparation(handle))
                .map(preparation -> preparation.descriptor)
                .map(descriptor -> QueueDiagnosticSnapshot.fingerprint(
                        QueueDiagnosticSnapshot.Kind.S3K_KOS_MODULE,
                        descriptor.sourceAddress(),
                        descriptor.destinationAddress(),
                        descriptor.moduleCount()))
                .toList();
        return new QueueDiagnosticSnapshot(
                QueueDiagnosticSnapshot.Kind.S3K_KOS_MODULE,
                true, true,
                -1, -1, -1,
                activeDescriptor.moduleCount()
                        - activePreparation.completedModules,
                waiting, observations);
    }

    public boolean isReady(HardwareWorkHandle handle) {
        return timing.isReady(handle);
    }

    public byte[] claim(HardwareWorkHandle handle) {
        byte[] payload = timing.claim(handle);
        descriptors.remove(handle);
        freshLevelHandoffHandles.remove(handle);
        return payload;
    }

    /** Marks a fresh-level parent for the synchronous loader's handoff owner. */
    public void claimAfterFreshLevelHandoff(HardwareWorkHandle handle) {
        if (!descriptors.containsKey(handle)) {
            throw new IllegalArgumentException(
                    "fresh-level handoff handle is not a KosM parent");
        }
        freshLevelHandoffHandles.add(handle);
    }

    /** Claims marked parents immediately after the boundary admits readiness. */
    public void claimReadyFreshLevelHandoffs() {
        for (HardwareWorkHandle handle : List.copyOf(freshLevelHandoffHandles)) {
            if (timing.isReady(handle)) {
                claim(handle);
            }
        }
    }

    List<HardwareWorkHandle> captureFreshLevelHandoffHandles() {
        return freshLevelHandoffHandles.stream()
                .sorted(java.util.Comparator.comparingLong(HardwareWorkHandle::ordinal))
                .toList();
    }

    void restoreFreshLevelHandoffHandles(List<HardwareWorkHandle> handles) {
        freshLevelHandoffHandles.clear();
        for (HardwareWorkHandle captured : handles) {
            HardwareWorkHandle rebound = timing.pendingHandle(
                            captured.kind(), captured.ordinal())
                    .orElseThrow(() -> new IllegalStateException(
                            "missing restored fresh-level handoff "
                                    + captured.kind() + "#" + captured.ordinal()));
            if (!captured.equals(rebound)) {
                throw new IllegalStateException(
                        "restored fresh-level handoff identity mismatch: expected "
                                + captured + ", actual " + rebound);
            }
            if (captured.kind() != HardwareWorkKind.KOS_MODULE_QUEUE) {
                throw new IllegalStateException(
                        "fresh-level handoff is not a KosM parent: " + captured);
            }
            freshLevelHandoffHandles.add(rebound);
        }
    }

    public S3kKosModuleDescriptor descriptor(HardwareWorkHandle handle) {
        S3kKosModuleDescriptor descriptor = descriptors.get(handle);
        if (descriptor == null
                && timing.isPending(handle)
                && timing.coordinatorPreparation(handle)
                instanceof S3kKosModulePreparation preparation) {
            descriptor = preparation.descriptor;
            descriptors.put(handle, descriptor);
        }
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "descriptor is not owned by this queue facade");
        }
        return descriptor;
    }

    void resetForMissingSnapshot() {
        descriptors.clear();
        freshLevelHandoffHandles.clear();
    }

    static HardwareWorkPreparation recreatePreparation(
            S3kKosModuleSnapshot snapshot) {
        return new S3kKosModulePreparation(snapshot);
    }

    private static final class S3kKosModulePreparation
            implements HardwareWorkPreparation {
        private final S3kKosModuleDescriptor descriptor;
        private final byte[] archive;
        private final ByteArrayOutputStream output;
        private int completedModules;
        private int activeModuleOffset;
        private HardwareWorkHandle activeChild;
        private int activeChildCompressedLength;
        private boolean prepared;

        private S3kKosModulePreparation(
                S3kKosModuleDescriptor descriptor,
                byte[] archive) {
            this.descriptor = descriptor;
            this.archive = archive.clone();
            this.output = new ByteArrayOutputStream(
                    descriptor.destinationLength());
            this.activeModuleOffset = 2;
            this.prepared = false;
        }

        private S3kKosModulePreparation(S3kKosModuleSnapshot snapshot) {
            this.descriptor = snapshot.descriptor();
            this.archive = snapshot.archive();
            this.completedModules = snapshot.completedModules();
            this.activeModuleOffset = snapshot.activeModuleOffset();
            this.activeChild = snapshot.activeChild();
            this.activeChildCompressedLength =
                    snapshot.activeChildCompressedLength();
            this.output = new ByteArrayOutputStream(
                    descriptor.destinationLength());
            this.output.writeBytes(snapshot.output());
            this.prepared = snapshot.prepared();
        }

        private boolean coordinate(
                HardwareWorkHandle parent,
                S3kKosDecompressionQueue directQueue) {
            if (prepared) {
                return false;
            }
            if (descriptor.moduleCount() == 0) {
                prepared = true;
                return true;
            }
            if (activeChild == null) {
                if (!directQueue.hasCapacity()) {
                    return false;
                }
                try {
                    activeChild = directQueue.queueModuleChild(
                            archive,
                            activeModuleOffset,
                            descriptor.sourceAddress() + activeModuleOffset,
                            S3kKosRamDestinations.KOS_DECOMP_BUFFER);
                    activeChildCompressedLength =
                            directQueue.descriptor(activeChild).compressedLength();
                    return false;
                } catch (IOException exception) {
                    throw new IllegalStateException(
                            "Unable to submit S3K KosM child for "
                                    + parent.kind() + "#" + parent.ordinal(),
                            exception);
                }
            }
            S3kKosDecompressionDescriptor childDescriptor =
                    directQueue.descriptor(activeChild);
            if (childDescriptor.sourceAddress()
                    != descriptor.sourceAddress() + activeModuleOffset
                    || childDescriptor.compressedLength()
                    != activeChildCompressedLength
                    || childDescriptor.destinationAddress()
                    != S3kKosRamDestinations.KOS_DECOMP_BUFFER) {
                throw new IllegalStateException(
                        "KosM parent/child descriptor mismatch for "
                                + parent.kind() + "#" + parent.ordinal());
            }
            if (directQueue.decompressionsPending()
                    || !directQueue.isReady(activeChild)) {
                return false;
            }

            output.writeBytes(directQueue.claim(activeChild));
            completedModules++;
            int moduleEnd = activeModuleOffset
                    + activeChildCompressedLength;
            activeChild = null;
            activeChildCompressedLength = 0;
            if (completedModules >= descriptor.moduleCount()) {
                prepared = true;
                return true;
            }
            activeModuleOffset = 2 + align16(moduleEnd - 2);
            return false;
        }

        @Override
        public boolean stepOneWorkUnit() {
            return false;
        }

        @Override
        public boolean isPrepared() {
            return prepared;
        }

        @Override
        public byte[] preparedPayload() {
            if (!prepared) {
                throw new IllegalStateException("KosM archive is not prepared");
            }
            return Arrays.copyOf(
                    output.toByteArray(), descriptor.destinationLength());
        }

        @Override
        public HardwareWorkPreparationSnapshot snapshot() {
            return new S3kKosModuleSnapshot(
                    descriptor,
                    archive,
                    completedModules,
                    activeModuleOffset,
                    activeChild,
                    activeChildCompressedLength,
                    output.toByteArray(),
                    prepared);
        }

        @Override
        public void restore(HardwareWorkPreparationSnapshot snapshot) {
            throw new UnsupportedOperationException(
                    "hardware timing restores by recreating preparations");
        }

        @Override
        public boolean isBoundaryDriven() {
            return true;
        }

        private static int align16(int value) {
            return (value + 0xF) & ~0xF;
        }
    }
}
