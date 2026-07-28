package com.openggf.game.sonic3k.resources;

import com.openggf.data.Rom;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkPreparation;
import com.openggf.game.timing.HardwareWorkPreparationSnapshot;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.tools.DecoderSnapshot;
import com.openggf.tools.KosinskiReader;
import com.openggf.tools.ResumableKosinskiDecoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * S3K's four-entry {@code Queue_Kos_Module} FIFO backed by session timing.
 *
 * <p>The internal standard-Kosinski decoder is serviced before the main loop;
 * module retirement and final readiness are visible only after object scans.
 * It is intentionally not submitted as separate direct-queue hardware work.
 */
public final class S3kKosModuleQueue {
    private static final int MAX_QUEUE_DEPTH = 4;
    private static final int PATTERN_BYTES = 32;
    private static final int INSPECTION_LIMIT = 0x40000;
    private static final String COMPRESSION_VARIANT = "kosinski_moduled";

    private final HardwareTimingService timing;
    private final Map<HardwareWorkHandle, S3kKosModuleDescriptor> descriptors =
            new HashMap<>();

    public S3kKosModuleQueue(HardwareTimingService timing) {
        this.timing = Objects.requireNonNull(timing, "timing");
    }

    public HardwareWorkHandle queue(
            Rom rom,
            int source,
            int destinationPatternAddress) throws IOException {
        Objects.requireNonNull(rom, "rom");
        if (timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE)
                >= MAX_QUEUE_DEPTH) {
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
                false,
                preparation));
        descriptors.put(handle, descriptor);
        return handle;
    }

    public void prepareQueuedModuleBeforeVSync() {
        timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
    }

    public void processModuleQueueAfterObjects() {
        timing.service(HardwareServiceBoundary.POST_OBJECTS);
    }

    public boolean modulesLeft() {
        return timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) != 0;
    }

    public boolean isReady(HardwareWorkHandle handle) {
        return timing.isReady(handle);
    }

    public byte[] claim(HardwareWorkHandle handle) {
        return timing.claim(handle);
    }

    public S3kKosModuleDescriptor descriptor(HardwareWorkHandle handle) {
        S3kKosModuleDescriptor descriptor = descriptors.get(handle);
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "descriptor is not owned by this queue facade");
        }
        return descriptor;
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
        private ResumableKosinskiDecoder activeDecoder;
        private boolean prepared;

        private S3kKosModulePreparation(
                S3kKosModuleDescriptor descriptor,
                byte[] archive) {
            this.descriptor = descriptor;
            this.archive = archive.clone();
            this.output = new ByteArrayOutputStream(
                    descriptor.destinationLength());
            this.activeModuleOffset = 2;
            this.prepared = descriptor.moduleCount() == 0;
        }

        private S3kKosModulePreparation(S3kKosModuleSnapshot snapshot) {
            this.descriptor = snapshot.descriptor();
            this.archive = snapshot.archive();
            this.completedModules = snapshot.completedModules();
            this.activeModuleOffset = snapshot.activeModuleOffset();
            this.output = new ByteArrayOutputStream(
                    descriptor.destinationLength());
            this.output.writeBytes(snapshot.output());
            DecoderSnapshot decoderSnapshot = snapshot.activeDecoder();
            this.activeDecoder = decoderSnapshot != null
                    ? ResumableKosinskiDecoder.fromSnapshot(decoderSnapshot)
                    : null;
            this.prepared = snapshot.prepared();
        }

        @Override
        public boolean stepOneWorkUnit() {
            return prepareCurrentModule();
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
            byte[] bytes = output.toByteArray();
            if (bytes.length == descriptor.destinationLength()) {
                return bytes;
            }
            return java.util.Arrays.copyOf(
                    bytes, descriptor.destinationLength());
        }

        @Override
        public HardwareWorkPreparationSnapshot snapshot() {
            return new S3kKosModuleSnapshot(
                    descriptor,
                    archive,
                    completedModules,
                    activeModuleOffset,
                    activeDecoder != null ? activeDecoder.snapshot() : null,
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

        @Override
        public boolean serviceBoundary(HardwareServiceBoundary boundary) {
            if (prepared) {
                return false;
            }
            return switch (boundary) {
                case PRE_MAIN_LOOP -> prepareCurrentModule();
                case POST_OBJECTS -> processModuleQueue();
                case VINT_SERVICE -> false;
            };
        }

        private boolean prepareCurrentModule() {
            if (activeDecoder == null || activeDecoder.complete()) {
                return false;
            }
            try {
                // Process_Kos_Queue normally runs until VBlank interrupts it.
                // The host has no instruction-accurate VBlank interruption
                // point, so one PRE service completes exactly one queued
                // module. The resumable decoder remains the preparation state
                // authority and is snapshotted before/after this service.
                boolean advanced = false;
                while (!activeDecoder.complete()) {
                    advanced |= activeDecoder.step(1)
                            .descriptorsProcessed() != 0;
                }
                return advanced;
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Unable to prepare S3K KosM module", e);
            }
        }

        private boolean processModuleQueue() {
            if (activeDecoder == null) {
                activeDecoder = newDecoder(activeModuleOffset);
                return true;
            }
            if (!activeDecoder.complete()) {
                return false;
            }

            output.writeBytes(activeDecoder.output());
            completedModules++;
            int moduleEnd = activeModuleOffset
                    + activeDecoder.compressedBytesConsumed();
            if (completedModules >= descriptor.moduleCount()) {
                activeDecoder = null;
                prepared = true;
                return true;
            }

            activeModuleOffset = 2 + align16(moduleEnd - 2);
            activeDecoder = newDecoder(activeModuleOffset);
            return true;
        }

        private ResumableKosinskiDecoder newDecoder(int offset) {
            try {
                return new ResumableKosinskiDecoder(archive, offset);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Unable to start S3K KosM module", e);
            }
        }

        private static int align16(int value) {
            return (value + 0xF) & ~0xF;
        }
    }
}
