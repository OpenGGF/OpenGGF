package com.openggf.game.rewind.snapshot;

/**
 * Snapshot of PLC/object-art progress for a game's object art provider.
 *
 * <p>The engine loads all PLC-driven object art at zone-load time
 * ({@code loadArtForZone}). The optional runtime state records provider-owned
 * queued art work, such as S3K's direct {@code Queue_Kos_Module} requests.
 *
 * <p>Providers may restore the epoch and runtime state when they own dynamic
 * work; providers without runtime queues may continue using the epoch only.
 */
public record PlcProgressSnapshot(int loadEpoch, int runtimeState) {
    public PlcProgressSnapshot(int loadEpoch) {
        this(loadEpoch, 0);
    }
}
