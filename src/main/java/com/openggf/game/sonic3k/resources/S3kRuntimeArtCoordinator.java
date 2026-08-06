package com.openggf.game.sonic3k.resources;

import com.openggf.data.Rom;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.level.objects.ObjectServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** S3K-owned facade for the direct Kosinski FIFO and KosM parent queue. */
public final class S3kRuntimeArtCoordinator implements RuntimeArtCoordinator,
        RewindSnapshottable<S3kRuntimeArtCoordinator.Snapshot> {
    public static final String REWIND_KEY = "s3k-runtime-art-coordinator";

    private final S3kKosDecompressionQueue directQueue;
    private final S3kKosModuleQueue moduleQueue;
    private FreshLevelRuntimeArtRequest deferredFreshLevelRuntimeArt;

    private record FreshLevelRuntimeArtRequest(
            Rom rom, int primarySource, int secondarySource) {
    }

    public record Snapshot(
            Rom deferredRom,
            int deferredPrimarySource,
            int deferredSecondarySource,
            List<HardwareWorkHandle> freshLevelHandoffHandles) {
        public Snapshot {
            freshLevelHandoffHandles = List.copyOf(freshLevelHandoffHandles);
        }
    }

    public S3kRuntimeArtCoordinator(HardwareTimingService timing) {
        directQueue = new S3kKosDecompressionQueue(
                Objects.requireNonNull(timing, "timing"));
        moduleQueue = new S3kKosModuleQueue(timing, directQueue);
    }

    public static S3kRuntimeArtCoordinator from(
            RuntimeArtCoordinator coordinator) {
        if (coordinator instanceof S3kRuntimeArtCoordinator s3k) {
            return s3k;
        }
        throw new IllegalStateException(
                "S3K runtime art requires the S3K game-owned coordinator");
    }

    public static S3kRuntimeArtCoordinator current() {
        return from(GameServices.runtimeArtCoordinator());
    }

    public static S3kRuntimeArtCoordinator from(ObjectServices services) {
        return from(services.runtimeArtCoordinator());
    }

    public S3kKosDecompressionQueue directQueue() {
        return directQueue;
    }

    public S3kKosModuleQueue moduleQueue() {
        return moduleQueue;
    }

    /**
     * Defers a fresh-level terrain submission until the current loop tail has
     * serviced PRE_MAIN_LOOP. The ROM publishes the new KosM parents after
     * that service, so their first module begins on the following iteration.
     */
    public void deferFreshLevelRuntimeArt(
            Rom rom, int primarySource, int secondarySource) {
        // A newer fresh load supersedes a handoff that has not yet reached the
        // publication tail. No production work for the older request exists.
        deferredFreshLevelRuntimeArt = new FreshLevelRuntimeArtRequest(
                Objects.requireNonNull(rom, "rom"), primarySource, secondarySource);
    }

    /**
     * ROM {@code LevelLoop} runs {@code Process_Kos_Module_Queue} in the loop
     * tail (sonic3k.asm:7908) and {@code Process_Kos_Queue} (7887) directly
     * after it, still ahead of {@code Wait_VSync} (7888) and the
     * {@code Level_frame_counter} increment (7889). Both are tail work for the
     * frame whose objects just ran, so the module step lands at
     * {@code POST_OBJECTS} and the direct FIFO service at {@code PRE_MAIN_LOOP},
     * in that order, at the end of the same frame.
     */
    @Override
    public void beforeTimingService(HardwareServiceBoundary boundary) {
        moduleQueue.beforeTimingService(boundary);
    }

    @Override
    public void afterTimingService(HardwareServiceBoundary boundary) {
        directQueue.afterTimingService(boundary);
        moduleQueue.afterTimingService(boundary);
        moduleQueue.claimReadyFreshLevelHandoffs();
        if (boundary == HardwareServiceBoundary.PRE_MAIN_LOOP
                && deferredFreshLevelRuntimeArt != null) {
            FreshLevelRuntimeArtRequest request = deferredFreshLevelRuntimeArt;
            List<Integer> sources = new ArrayList<>();
            sources.add(request.primarySource());
            if (request.secondarySource() != request.primarySource()
                    && request.secondarySource() > 0) {
                sources.add(request.secondarySource());
            }
            if (!moduleQueue.hasCapacityFor(sources.size())) {
                return;
            }
            try {
                List<HardwareWorkHandle> handles =
                        moduleQueue.queueSequentialBatch(request.rom(), sources, 0);
                for (HardwareWorkHandle handle : handles) {
                    moduleQueue.claimAfterFreshLevelHandoff(handle);
                }
                deferredFreshLevelRuntimeArt = null;
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(
                        "Unable to publish deferred fresh-level runtime art", exception);
            }
        }
    }

    @Override
    public void deferProductionSubmissionForHeldLoopTail() {
        moduleQueue.deferChildSubmissionForHeldLoopTail();
    }

    @Override
    public boolean ownsHeldLevelCounterHardwareTail() {
        return true;
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public Snapshot capture() {
        FreshLevelRuntimeArtRequest request = deferredFreshLevelRuntimeArt;
        return new Snapshot(
                request == null ? null : request.rom(),
                request == null ? -1 : request.primarySource(),
                request == null ? -1 : request.secondarySource(),
                moduleQueue.captureFreshLevelHandoffHandles());
    }

    @Override
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        moduleQueue.restoreFreshLevelHandoffHandles(
                snapshot.freshLevelHandoffHandles());
        if (snapshot.deferredPrimarySource() < 0) {
            if (snapshot.deferredRom() != null
                    || snapshot.deferredSecondarySource() >= 0) {
                throw new IllegalStateException(
                        "invalid empty fresh-level runtime-art snapshot");
            }
            deferredFreshLevelRuntimeArt = null;
            return;
        }
        if (snapshot.deferredRom() == null
                || snapshot.deferredSecondarySource() < 0) {
            throw new IllegalStateException(
                    "invalid deferred fresh-level runtime-art snapshot");
        }
        deferredFreshLevelRuntimeArt = new FreshLevelRuntimeArtRequest(
                snapshot.deferredRom(), snapshot.deferredPrimarySource(),
                snapshot.deferredSecondarySource());
    }

    @Override
    public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() {
        return List.of(
                directQueue.captureDiagnostics(List.of()),
                moduleQueue.captureDiagnostics(List.of()));
    }

    @Override
    public void registerRewindAdapters(RewindRegistry registry) {
        registry.register(directQueue);
        registry.register(this);
    }

    @Override
    public void deregisterRewindAdapters(RewindRegistry registry) {
        registry.deregister(REWIND_KEY);
        registry.deregister(S3kKosDecompressionQueue.REWIND_KEY);
    }

    @Override
    public void resetForMissingSnapshot() {
        directQueue.resetForMissingSnapshot();
        moduleQueue.resetForMissingSnapshot();
        deferredFreshLevelRuntimeArt = null;
    }
}
