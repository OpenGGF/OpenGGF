package com.openggf.audio.rewind;

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
        int fmUserMuteMask,
        int fmUserSoloMask,
        int psgUserMuteMask,
        int psgUserSoloMask) {

    private static final AudioBackendLogicalSnapshot EMPTY =
            new AudioBackendLogicalSnapshot(null, false, false, false, 1,
                    List.of(), null, null,
                    0, 0, 0, 0);

    public AudioBackendLogicalSnapshot {
        overrideStack = List.copyOf(Objects.requireNonNull(overrideStack, "overrideStack"));
        fmUserMuteMask &= 0x3F;
        fmUserSoloMask &= 0x3F;
        psgUserMuteMask &= 0x0F;
        psgUserSoloMask &= 0x0F;
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
                0, 0, 0, 0);
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
                0, 0, 0, 0);
    }

    public static AudioBackendLogicalSnapshot empty() {
        return EMPTY;
    }
}
