package com.openggf.audio.rewind;

import com.openggf.audio.StreamedMusicPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@com.openggf.game.ModApi
public record AudioBackendLogicalSnapshot(
        AudioSourceDescriptor currentMusic,
        boolean sfxBlocked,
        boolean pendingRestore,
        boolean speedShoesEnabled,
        int speedMultiplier,
        List<AudioSourceDescriptor> overrideStack,
        SmpsDriverSnapshot musicDriver,
        SmpsDriverSnapshot standaloneSfxDriver,
        StreamedMusicPort.State streamedMusic,
        List<StreamedMusicPort.State> streamedOverrideStack) {

    private static final AudioBackendLogicalSnapshot EMPTY =
            new AudioBackendLogicalSnapshot(null, false, false, false, 1, List.of(), null, null,
                    null, List.of());

    public AudioBackendLogicalSnapshot {
        overrideStack = List.copyOf(Objects.requireNonNull(overrideStack, "overrideStack"));
        streamedOverrideStack = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(streamedOverrideStack, "streamedOverrideStack")));
        if (streamedOverrideStack.size() != overrideStack.size()) {
            throw new IllegalArgumentException("Saved streamed states must align with the override stack");
        }
    }

    public AudioBackendLogicalSnapshot(
            AudioSourceDescriptor currentMusic,
            boolean sfxBlocked,
            boolean pendingRestore,
            boolean speedShoesEnabled,
            int speedMultiplier,
            List<AudioSourceDescriptor> overrideStack) {
        this(currentMusic, sfxBlocked, pendingRestore, speedShoesEnabled, speedMultiplier,
                overrideStack, null, null, null,
                new ArrayList<>(Collections.nCopies(overrideStack.size(), null)));
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
        this(currentMusic, sfxBlocked, pendingRestore, speedShoesEnabled, speedMultiplier,
                overrideStack, musicDriver, standaloneSfxDriver, null,
                new ArrayList<>(Collections.nCopies(overrideStack.size(), null)));
    }

    public static AudioBackendLogicalSnapshot empty() {
        return EMPTY;
    }
}
