package com.openggf.audio.rewind;

import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import java.util.List;
import java.util.Objects;

public record AudioBackendLogicalSnapshot(
        AudioSourceDescriptor currentMusic,
        boolean sfxBlocked,
        boolean pendingRestore,
        boolean speedShoesEnabled,
        int speedMultiplier,
        List<AudioSourceDescriptor> overrideStack,
        SmpsDriverSnapshot musicDriver,
        SmpsDriverSnapshot standaloneSfxDriver,
        SmpsCoordFlagRuntimeState.Snapshot legacyCoordFlagRuntimeState) {

    private static final AudioBackendLogicalSnapshot EMPTY =
            new AudioBackendLogicalSnapshot(null, false, false, false, 1,
                    List.of(), null, null,
                    new SmpsCoordFlagRuntimeState.Snapshot(0));

    public AudioBackendLogicalSnapshot {
        overrideStack = List.copyOf(Objects.requireNonNull(overrideStack, "overrideStack"));
        legacyCoordFlagRuntimeState = Objects.requireNonNull(
                legacyCoordFlagRuntimeState, "legacyCoordFlagRuntimeState");
    }

    public AudioBackendLogicalSnapshot(
            AudioSourceDescriptor currentMusic,
            boolean sfxBlocked,
            boolean pendingRestore,
            boolean speedShoesEnabled,
            int speedMultiplier,
            List<AudioSourceDescriptor> overrideStack) {
        this(currentMusic, sfxBlocked, pendingRestore, speedShoesEnabled, speedMultiplier,
                overrideStack, null, null,
                new SmpsCoordFlagRuntimeState.Snapshot(0));
    }

    public AudioBackendLogicalSnapshot(
            AudioSourceDescriptor currentMusic,
            boolean sfxBlocked,
            boolean pendingRestore,
            boolean speedShoesEnabled,
            int speedMultiplier,
            List<AudioSourceDescriptor> overrideStack,
            SmpsDriverSnapshot musicDriver,
            SmpsDriverSnapshot standaloneSfxDriver) {
        this(currentMusic, sfxBlocked, pendingRestore, speedShoesEnabled,
                speedMultiplier, overrideStack, musicDriver,
                standaloneSfxDriver,
                new SmpsCoordFlagRuntimeState.Snapshot(0));
    }

    public static AudioBackendLogicalSnapshot empty() {
        return EMPTY;
    }
}
