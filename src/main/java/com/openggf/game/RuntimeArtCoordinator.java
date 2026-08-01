package com.openggf.game;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.resources.QueueDiagnosticsProvider;
import com.openggf.game.timing.HardwareServiceBoundary;

/**
 * Game-owned coordinator for runtime art work submitted to hardware timing.
 *
 * <p>Shared session and frame code owns only this boundary. Concrete queue
 * implementations remain inside the game module which creates the
 * coordinator.
 */
public interface RuntimeArtCoordinator extends QueueDiagnosticsProvider {
    RuntimeArtCoordinator NONE = new RuntimeArtCoordinator() {
    };

    /**
     * Runs before the timing ledger is serviced at a production boundary, for
     * coordinator work the ROM performs in the previous loop iteration's tail.
     */
    default void beforeTimingService(HardwareServiceBoundary boundary) {
    }

    default void afterTimingService(HardwareServiceBoundary boundary) {
    }

    default void registerRewindAdapters(RewindRegistry registry) {
    }

    default void deregisterRewindAdapters(RewindRegistry registry) {
    }

    default void resetForMissingSnapshot() {
    }
}
