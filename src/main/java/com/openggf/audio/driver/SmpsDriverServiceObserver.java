package com.openggf.audio.driver;

import com.openggf.audio.rewind.SmpsDriverSnapshot;

/**
 * Append-only diagnostic view of complete driver services and out-of-service
 * lifecycle mutations. Observers are deliberately absent from snapshots.
 */
public interface SmpsDriverServiceObserver {
    SmpsDriverServiceObserver NONE = new SmpsDriverServiceObserver() { };

    default void onServiceBegin(long ordinal) { }

    default void onServiceEnd(
            long ordinal, SmpsDriverSnapshot snapshot) { }

    default void onLifecycle(LifecycleKind kind) { }

    enum LifecycleKind {
        DRIVER_CREATED,
        RESET,
        PAUSE,
        RESUME,
        STOP_ALL,
        STOP_ALL_SFX,
        SAVE,
        RESTORE,
        SEGA_PCM_ENTER,
        SEGA_PCM_LEAVE
    }
}
