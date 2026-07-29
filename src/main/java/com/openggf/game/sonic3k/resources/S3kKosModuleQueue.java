package com.openggf.game.sonic3k.resources;

import com.openggf.data.Rom;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkPreparation;
import com.openggf.game.timing.HardwareWorkPreparationSnapshot;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.tools.KosinskiReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    private HardwareWorkHandle queueInternal(
            Rom rom,
            int source,
            int destinationPatternAddress,
            boolean exportableAcrossSegment) throws IOException {
        Objects.requireNonNull(rom, "rom");
        if (physicalQueueSize() >= MAX_QUEUE_DEPTH) {
            throw new IllegalStateException("S3K KosM module FIFO is full");
        }
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
        HardwareWorkHandle handle = timing.submit(new HardwareWorkSubmission(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                descriptor.sourceAddress(),
                descriptor.compressedLength(),
                descriptor.destinationAddress(),
                descriptor.destinationLength(),
                COMPRESSION_VARIANT,
                descriptor.moduleCount(),
                exportableAcrossSegment,
                preparation));
        descriptors.put(handle, descriptor);
        return handle;
    }

    public void prepareQueuedModuleBeforeVSync() {
        timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        directQueue.afterTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
    }

    public void processModuleQueueAfterObjects() {
        timing.service(HardwareServiceBoundary.POST_OBJECTS);
        afterTimingService(HardwareServiceBoundary.POST_OBJECTS);
    }

    /** Runs after generic timing and direct retirement at a production boundary. */
    public void afterTimingService(HardwareServiceBoundary boundary) {
        if (boundary != HardwareServiceBoundary.POST_OBJECTS) {
            return;
        }
        for (HardwareWorkHandle handle : timing.pendingHandles()) {
            if (handle.kind() != HardwareWorkKind.KOS_MODULE_QUEUE
                    || timing.isReady(handle)) {
                continue;
            }
            HardwareWorkPreparation generic =
                    timing.coordinatorPreparation(handle);
            if (!(generic instanceof S3kKosModulePreparation preparation)) {
                throw new IllegalStateException(
                        "KosM parent has an unexpected preparation owner");
            }
            descriptors.putIfAbsent(handle, preparation.descriptor);
            if (preparation.isPrepared()) {
                continue;
            }
            boolean becamePrepared = preparation.coordinate(
                    handle, directQueue);
            if (becamePrepared) {
                timing.captureCoordinatorPreparation(
                        handle, HardwareServiceBoundary.POST_OBJECTS);
            }
            return;
        }
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

    public boolean isReady(HardwareWorkHandle handle) {
        return timing.isReady(handle);
    }

    public byte[] claim(HardwareWorkHandle handle) {
        byte[] payload = timing.claim(handle);
        descriptors.remove(handle);
        return payload;
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
