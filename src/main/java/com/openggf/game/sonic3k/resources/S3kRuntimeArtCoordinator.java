package com.openggf.game.sonic3k.resources;

import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.objects.ObjectServices;

import java.util.Objects;
import java.util.List;

/** S3K-owned facade for the direct Kosinski FIFO and KosM parent queue. */
public final class S3kRuntimeArtCoordinator implements RuntimeArtCoordinator {
    private final S3kKosDecompressionQueue directQueue;
    private final S3kKosModuleQueue moduleQueue;

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
     * ROM {@code LevelLoop} runs {@code Process_Kos_Module_Queue} in the loop
     * tail (sonic3k.asm:7908) and {@code Process_Kos_Queue} at the head of the
     * next iteration, ahead of {@code Wait_VSync} (7887-7888). The module step
     * is therefore the previous frame's tail work: it runs at the top of this
     * frame, before the direct FIFO is serviced and before any completion is
     * admitted, so a module submitted here is first drained by this frame's
     * direct service — never by the one that already ran.
     */
    @Override
    public void beforeTimingService(HardwareServiceBoundary boundary) {
        moduleQueue.beforeTimingService(boundary);
    }

    @Override
    public void afterTimingService(HardwareServiceBoundary boundary) {
        directQueue.afterTimingService(boundary);
        moduleQueue.afterTimingService(boundary);
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
    }

    @Override
    public void deregisterRewindAdapters(RewindRegistry registry) {
        registry.deregister(S3kKosDecompressionQueue.REWIND_KEY);
    }

    @Override
    public void resetForMissingSnapshot() {
        directQueue.resetForMissingSnapshot();
        moduleQueue.resetForMissingSnapshot();
    }
}
