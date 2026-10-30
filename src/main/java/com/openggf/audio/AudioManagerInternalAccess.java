package com.openggf.audio;

import com.openggf.data.Rom;

import java.util.Objects;

/** Engine-only native request ingress without extending the creator audio API. */
public final class AudioManagerInternalAccess {
    private AudioManagerInternalAccess() { }

    /** Window-thread service between semantic ticks, without a synthesis/history/command clock. */
    public static void serviceDevice(AudioManager manager) {
        Objects.requireNonNull(manager, "manager").servicePresentationDevice();
    }

    /** One explicit replay/capture driver owns packet duration until teardown. */
    public static OfflineServiceCadence offlineServiceCadence(AudioManager manager, int hz) {
        return new OfflineServiceCadence(Objects.requireNonNull(manager), hz);
    }

    public static final class OfflineServiceCadence implements AutoCloseable {
        private AudioManager manager;
        private final Thread thread = Thread.currentThread();
        private OfflineServiceCadence(AudioManager manager, int hz) {
            manager.beginOfflineServiceCadence(this, hz);
            this.manager = manager;
        }
        @Override public void close() {
            if (Thread.currentThread() != thread) throw new IllegalStateException("offline cadence belongs to its driver thread");
            if (manager == null) return;
            manager.endOfflineServiceCadence(this);
            manager = null;
        }
    }

    /** Installs one selected world source without decoding either transient cross-pair. */
    public static void installMatchedSource(
            AudioManager manager, Rom rom, GameAudioProfile profile) {
        Objects.requireNonNull(manager, "manager").installMatchedSource(rom, profile);
    }

    public static void playSecondaryNativeRequest(AudioManager manager, int nativeId) {
        Objects.requireNonNull(manager, "manager").playSecondaryNativeRequest(nativeId);
    }
}
